package com.machining.quote;

import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.OrderRfq;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class U9CustomerMasterStage2MigrationTest {
    private static final String TEST_SCHEMA_PREFIX = "qpo_u9_customer_stage2_";
    private static final Path SCHEMA_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SEED_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path CLEAR_SCRIPT = Path.of(
            "..", "docs", "ops", "sql", "clear_order_business_data_before_u9_customer_cutover.sql");

    @Test
    void baselineDefinesU9CustomerCacheAndSyncOptionTables() throws IOException {
        String schema = read(SCHEMA_BASELINE);
        String seed = read(SEED_BASELINE);
        String clearScript = read(CLEAR_SCRIPT);

        assertThat(schema)
                .contains(
                        "CREATE TABLE customer_master",
                        "u9_org_code character varying(50)",
                        "u9_customer_id character varying(100)",
                        "last_synced_at timestamp without time zone",
                        "source_hash character varying(64)",
                        "CREATE TABLE u9_sync_seller_option_cache",
                        "CREATE TABLE u9_sync_department_option_cache",
                        "CREATE TABLE u9_master_data_sync_run",
                        "customer_code character varying(200) NOT NULL",
                        "customer_name_snapshot character varying(200)",
                        "uk_system_role_data_scope_customer_code",
                        "CREATE INDEX idx_order_rfq_customer_code")
                .doesNotContain(
                        "DROP TABLE customer_master",
                        "uk_customer_master_name",
                        "uk_customer_master_short_name");

        assertThat(seed)
                .doesNotContain(
                        "INSERT INTO customer_master",
                        "INSERT INTO u9_sync_seller_option_cache",
                        "INSERT INTO u9_sync_department_option_cache",
                        "INSERT INTO u9_master_data_sync_run");
        assertThat(clearScript)
                .contains(
                        "qpo.confirm_clear_order_business_data",
                        "TRUNCATE TABLE",
                        "order_rfq",
                        "sales_order_header",
                        "sales_order_import_batch")
                .doesNotContain("TRUNCATE TABLE customer_master");
    }

    @Test
    void customerMasterEntityExposesU9CacheFields() throws Exception {
        assertField(CustomerMaster.class, "u9OrgCode", String.class);
        assertField(CustomerMaster.class, "u9CustomerId", String.class);
        assertField(CustomerMaster.class, "lastSyncedAt", LocalDateTime.class);
        assertField(CustomerMaster.class, "sourceHash", String.class);
    }

    @Test
    void orderRfqCustomerCodeSnapshotFieldsAreFormalFields() throws Exception {
        assertThat(OrderRfq.class.getDeclaredField("customerCode").getAnnotation(Deprecated.class)).isNull();
        assertThat(OrderRfq.class.getDeclaredField("customerShortName").getAnnotation(Deprecated.class)).isNull();
    }

    @Test
    void flywayMigratesEmptySchemaAndAllowsDuplicateCustomerNames() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                migrate(connection, schema);

                assertThat(flywayHistory(connection, schema)).contains(
                        "1|qpo baseline schema|true",
                        "2|qpo baseline seed data|true",
                        "5|u9 customer sync schedule config|true");
                assertThat(tableExists(connection, schema, "u9_sync_seller_option_cache")).isTrue();
                assertThat(tableExists(connection, schema, "u9_sync_department_option_cache")).isTrue();
                assertThat(tableExists(connection, schema, "u9_master_data_sync_run")).isTrue();
                assertThat(tableExists(connection, schema, "u9_customer_sync_schedule_config")).isTrue();
                assertThat(columnExists(connection, schema, "customer_master", "u9_org_code")).isTrue();
                assertThat(columnExists(connection, schema, "customer_master", "u9_customer_id")).isTrue();
                assertThat(columnExists(connection, schema, "system_role_data_scope_customer", "customer_code")).isTrue();
                assertThat(columnExists(connection, schema, "system_role_data_scope_customer", "customer_name_snapshot")).isTrue();
                assertThat(indexExists(connection, schema, "uk_system_role_data_scope_customer_code")).isTrue();
                assertThat(indexExists(connection, schema, "idx_order_rfq_customer_code")).isTrue();
                assertThat(rowCount(connection, schema, "customer_master")).isZero();
                assertThat(rowCount(connection, schema, "u9_sync_seller_option_cache")).isZero();
                assertThat(rowCount(connection, schema, "u9_sync_department_option_cache")).isZero();
                assertThat(rowCount(connection, schema, "u9_customer_sync_schedule_config")).isEqualTo(1);

                insertCustomer(connection, schema, "C001", "Same Customer");
                insertCustomer(connection, schema, "C002", "Same Customer");
                assertDuplicateCustomerCodeRejected(connection, schema);
                assertDuplicateCustomerNamesAcceptedInDataScopes(connection, schema);
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    @Test
    void clearScriptRunsAfterCompressedBaselineAndPreservesSystemAndMasterSeed() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                migrate(connection, schema);
                execute(connection, "SET search_path TO " + schema);
                execute(connection, "SET qpo.confirm_clear_order_business_data = 'YES'");
                execute(connection, read(CLEAR_SCRIPT));
                connection.commit();

                assertThat(flywayHistory(connection, schema)).contains(
                        "1|qpo baseline schema|true",
                        "2|qpo baseline seed data|true",
                        "5|u9 customer sync schedule config|true");
                assertThat(rowCount(connection, schema, "system_permission")).isPositive();
                assertThat(rowCount(connection, schema, "system_role")).isPositive();
                assertThat(rowCount(connection, schema, "dict_material")).isPositive();
                assertThat(rowCount(connection, schema, "dict_process_category")).isPositive();
                assertThat(rowCount(connection, schema, "dict_process_detail")).isPositive();
                assertThat(rowCount(connection, schema, "order_rfq")).isZero();
                assertThat(rowCount(connection, schema, "sales_order_header")).isZero();
                assertThat(rowCount(connection, schema, "customer_master")).isZero();
                assertThat(rowCount(connection, schema, "u9_customer_sync_schedule_config")).isEqualTo(1);
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    private static void assertField(Class<?> type, String name, Class<?> fieldType) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        assertThat(field.getType()).isEqualTo(fieldType);
    }

    private static void insertCustomer(Connection connection, String schema, String code, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.customer_master (customer_code, customer_short_name, customer_name)
                VALUES (?, ?, ?)
                """.formatted(schema))) {
            statement.setString(1, code);
            statement.setString(2, code);
            statement.setString(3, name);
            statement.executeUpdate();
        }
    }

    private static void assertDuplicateCustomerCodeRejected(Connection connection, String schema) throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            assertThatThrownBy(() -> insertCustomer(connection, schema, "C001", "Another Name"))
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState())
                    .isEqualTo("23505");
        } finally {
            connection.rollback(savepoint);
        }
    }

    private static void assertDuplicateCustomerNamesAcceptedInDataScopes(Connection connection, String schema) throws SQLException {
        execute(connection, "INSERT INTO " + schema + ".system_role (id, role_code, role_name) VALUES (990001, 'STAGE2_ROLE_A', 'Stage2 Role A')");
        execute(connection, "INSERT INTO " + schema + ".system_role (id, role_code, role_name) VALUES (990002, 'STAGE2_ROLE_B', 'Stage2 Role B')");
        execute(connection, """
                INSERT INTO %s.system_role_data_scope_customer (role_id, customer_code, customer_name, customer_name_snapshot)
                VALUES (990001, 'C001', 'Same Customer', 'Same Customer')
                """.formatted(schema));
        execute(connection, """
                INSERT INTO %s.system_role_data_scope_customer (role_id, customer_code, customer_name, customer_name_snapshot)
                VALUES (990002, 'C002', 'Same Customer', 'Same Customer')
                """.formatted(schema));
    }

    private static void migrate(Connection connection, String schema) {
        Flyway.configure()
                .dataSource(connectionString(connection), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                .schemas(schema)
                .defaultSchema(schema)
                .locations("filesystem:src/main/resources/db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    private static String connectionString(Connection connection) {
        try {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection openDatabase() throws SQLException {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(notBlank(url) && notBlank(username) && password != null,
                "DB_URL, DB_USERNAME and DB_PASSWORD are required for PostgreSQL migration tests");
        Connection connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(false);
        return connection;
    }

    private static String createTestSchema(Connection connection) throws SQLException {
        String schema = TEST_SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
        execute(connection, "CREATE SCHEMA " + schema);
        connection.commit();
        return schema;
    }

    private static void dropTestSchema(Connection connection, String schema) throws SQLException {
        if (!schema.startsWith(TEST_SCHEMA_PREFIX)) {
            throw new IllegalArgumentException("Refusing to drop non-test schema: " + schema);
        }
        connection.rollback();
        execute(connection, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        connection.commit();
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean columnExists(Connection connection, String schema, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String schema, String index) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_indexes
                    WHERE schemaname = ? AND indexname = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static int rowCount(Connection connection, String schema, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + schema + "." + table)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static List<String> flywayHistory(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version, description, success
                FROM %s.flyway_schema_history
                WHERE type <> 'SCHEMA'
                ORDER BY installed_rank
                """.formatted(schema));
             ResultSet resultSet = statement.executeQuery()) {
            java.util.ArrayList<String> rows = new java.util.ArrayList<>();
            while (resultSet.next()) {
                rows.add(resultSet.getString(1) + "|" + resultSet.getString(2) + "|" + resultSet.getBoolean(3));
            }
            return rows;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
