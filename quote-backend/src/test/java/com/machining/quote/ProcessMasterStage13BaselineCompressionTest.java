package com.machining.quote;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMasterStage13BaselineCompressionTest {
    private static final String TEST_SCHEMA_PREFIX = "qpo_stage13_baseline_";
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path MIGRATION_DIR = RESOURCES.resolve(Path.of("db", "migration"));

    @Test
    void activeMigrationDirectoryContainsCompressedBaselineAndCurrentIncrementalFiles() throws Exception {
        List<String> migrationNames;
        try (var files = Files.list(MIGRATION_DIR)) {
            migrationNames = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertThat(migrationNames).containsExactly(
                "V1__qpo_baseline_schema.sql",
                "V2__qpo_baseline_seed_data.sql",
                "V3__repeat_released_snapshot_candidate_index.sql",
                "V4__retire_sales_match_module.sql",
                "V5__refactor_sales_order_u9_sync_contract.sql");
        assertThat(RESOURCES.resolve("schema.sql")).doesNotExist();
    }

    @Test
    void flywayRunsOnlyNewBaselineVersionsOnEmptySchema() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                Flyway.configure()
                        .dataSource(connection.getMetaData().getURL(), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("filesystem:src/main/resources/db/migration")
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();

                assertThat(flywayHistory(connection, schema)).containsExactly(
                        "1|qpo baseline schema|true",
                        "2|qpo baseline seed data|true",
                        "3|repeat released snapshot candidate index|true",
                        "4|retire sales match module|true",
                        "5|refactor sales order u9 sync contract|true");
                assertThat(tableExists(connection, schema, "dict_process_category")).isTrue();
                assertThat(tableExists(connection, schema, "dict_process_detail")).isTrue();
                assertThat(tableExists(connection, schema, "dict_process")).isFalse();
                assertThat(columnExists(connection, schema, "item_quote_version", "final_unit_price")).isFalse();
                assertThat(columnExists(connection, schema, "sales_order_item", "final_unit_price")).isFalse();
                assertThat(columnExists(connection, schema, "sales_order_header", "u9_seller_code")).isTrue();
                assertThat(columnExists(connection, schema, "sales_order_header", "u9_sale_department_code")).isTrue();
                assertThat(columnExists(connection, schema, "sales_order_header", "u9_sync_attempt_id")).isTrue();
                assertThat(columnExists(connection, schema, "sales_order_header", "u9_sync_request_hash")).isTrue();
                assertThat(indexExists(connection, schema, "uk_sales_order_header_document_no")).isTrue();
                assertDuplicateDocumentNoRejected(connection, schema);
                assertThat(permissionExists(connection, schema, "market:sales-match:view")).isFalse();
                assertThat(routePathExists(connection, schema, "/sales-match")).isFalse();
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    @Test
    void v5BackfillsBlankDocumentNumbersWithoutChangingExistingValues() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                Flyway.configure()
                        .dataSource(connection.getMetaData().getURL(), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("filesystem:src/main/resources/db/migration")
                        .target(MigrationVersion.fromVersion("4"))
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();
                insertHistoricalSalesOrder(connection, schema, "SO-HIST-BLANK", null, null);
                insertHistoricalSalesOrder(connection, schema, "SO-HIST-RFQ", "DOC-RFQ-HIST", "");
                connection.commit();

                Flyway.configure()
                        .dataSource(connection.getMetaData().getURL(), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("filesystem:src/main/resources/db/migration")
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();

                assertThat(salesOrderState(connection, schema, "SO-HIST-BLANK"))
                        .isEqualTo("SO-HIST-BLANK|未同步");
                assertThat(salesOrderState(connection, schema, "SO-HIST-RFQ"))
                        .isEqualTo("DOC-RFQ-HIST|未同步");
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    @Test
    void applicationFlywayConfigDoesNotBaselineUnknownNonEmptyDatabases() throws Exception {
        String yaml = Files.readString(RESOURCES.resolve("application.yaml"), StandardCharsets.UTF_8);

        assertThat(yaml).contains("baseline-on-migrate: false");
    }

    private static Connection openDatabase() throws SQLException {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(notBlank(url) && notBlank(username) && password != null,
                "DB_URL, DB_USERNAME and DB_PASSWORD are required for PostgreSQL baseline tests");
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

    private static boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
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
                    SELECT 1
                    FROM information_schema.columns
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
                    SELECT 1
                    FROM pg_indexes
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

    private static void assertDuplicateDocumentNoRejected(Connection connection, String schema) throws SQLException {
        var savepoint = connection.setSavepoint();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        try {
            insertHistoricalSalesOrder(connection, schema, "SO-UNIQUE-A-" + suffix, "DOC-DUP-" + suffix, "未同步");
            try {
                insertHistoricalSalesOrder(connection, schema, "SO-UNIQUE-B-" + suffix, "DOC-DUP-" + suffix, "未同步");
                throw new AssertionError("duplicate document_no was accepted");
            } catch (SQLException ex) {
                assertThat(ex.getSQLState()).isEqualTo("23505");
            }
        } finally {
            connection.rollback(savepoint);
        }
    }

    private static void insertHistoricalSalesOrder(
            Connection connection,
            String schema,
            String salesOrderNo,
            String documentNo,
            String u9SyncStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s.sales_order_header (sales_order_no, document_no, u9_sync_status)
                VALUES (?, ?, ?)
                """.formatted(schema))) {
            statement.setString(1, salesOrderNo);
            statement.setString(2, documentNo);
            statement.setString(3, u9SyncStatus);
            statement.executeUpdate();
        }
    }

    private static String salesOrderState(Connection connection, String schema, String salesOrderNo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT document_no, u9_sync_status
                FROM %s.sales_order_header
                WHERE sales_order_no = ?
                """.formatted(schema))) {
            statement.setString(1, salesOrderNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1) + "|" + resultSet.getString(2);
            }
        }
    }

    private static boolean permissionExists(Connection connection, String schema, String permissionCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM %s.system_permission
                    WHERE permission_code = ?
                )
                """.formatted(schema))) {
            statement.setString(1, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean routePathExists(Connection connection, String schema, String routePath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM %s.system_permission
                    WHERE route_path = ?
                )
                """.formatted(schema))) {
            statement.setString(1, routePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
