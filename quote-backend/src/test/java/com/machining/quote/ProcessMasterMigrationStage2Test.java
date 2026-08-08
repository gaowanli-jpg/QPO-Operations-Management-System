package com.machining.quote;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessMasterMigrationStage2Test {
    private static final String TEST_SCHEMA_PREFIX = "qpo_process_master_stage2_";
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V91__process_category_detail_master.sql");

    @Test
    void migrationDefinesTwoLevelProcessMasterTablesConstraintsAndIndexes() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS dict_process_category",
                        "process_code VARCHAR(64) NOT NULL",
                        "category_name VARCHAR(120) NOT NULL",
                        "hourly_rate NUMERIC(18, 4) NOT NULL",
                        "source_type VARCHAR(30) NOT NULL DEFAULT 'LOCAL'",
                        "enabled BOOLEAN NOT NULL DEFAULT TRUE",
                        "CONSTRAINT uk_dict_process_category_code UNIQUE (process_code)",
                        "CONSTRAINT chk_dict_process_category_rate CHECK (hourly_rate >= 0)",
                        "CONSTRAINT chk_dict_process_category_source CHECK (source_type IN ('LOCAL', 'MES'))",
                        "CREATE TABLE IF NOT EXISTS dict_process_detail",
                        "category_id BIGINT NOT NULL REFERENCES dict_process_category(id) ON DELETE RESTRICT",
                        "detail_name VARCHAR(120) NOT NULL",
                        "detail_remarks VARCHAR(500)",
                        "CONSTRAINT uk_dict_process_detail_category_name UNIQUE (category_id, detail_name)",
                        "CREATE INDEX IF NOT EXISTS idx_dict_process_category_enabled_sort",
                        "CREATE INDEX IF NOT EXISTS idx_dict_process_category_name",
                        "CREATE INDEX IF NOT EXISTS idx_dict_process_category_mes_external",
                        "CREATE INDEX IF NOT EXISTS idx_dict_process_detail_category_enabled_sort",
                        "CREATE INDEX IF NOT EXISTS idx_dict_process_detail_name");
    }

    @Test
    void migrationBackfillsLegacyDictProcessAsCategoryAndSameNameDetail() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                createLegacyDictProcess(connection, schema);
                execute(connection, """
                        INSERT INTO dict_process (
                            id, process_name, calc_type, sort_no, price_1_5, price_5_10, price_3000_plus
                        ) VALUES
                            (1, 'CNC', 'WORK_HOUR', 10, 120.00, 100.00, 45.00),
                            (2, 'PACKAGING', 'WORK_HOUR', 20, NULL, 10.00, 10.00)
                        """);
                connection.commit();

                executeMigration(connection, schema);
                connection.commit();

                assertThat(tableExists(connection, schema, "dict_process_category")).isTrue();
                assertThat(tableExists(connection, schema, "dict_process_detail")).isTrue();
                assertThat(indexExists(connection, schema, "idx_dict_process_category_enabled_sort")).isTrue();
                assertThat(indexExists(connection, schema, "idx_dict_process_detail_category_enabled_sort")).isTrue();

                assertThat(categoryCount(connection, schema)).isEqualTo(2);
                assertThat(detailCount(connection, schema)).isEqualTo(2);
                assertThat(categoryName(connection, schema, "LEGACY_1")).isEqualTo("CNC");
                assertThat(hourlyRate(connection, schema, "LEGACY_1")).isEqualByComparingTo("120.0000");
                assertThat(hourlyRate(connection, schema, "LEGACY_2")).isEqualByComparingTo("10.0000");
                assertThat(detailExists(connection, schema, "LEGACY_1", "CNC")).isTrue();
                assertThat(detailExists(connection, schema, "LEGACY_2", "PACKAGING")).isTrue();

                assertThatThrownBy(() -> execute(connection, """
                        INSERT INTO dict_process_category (process_code, category_name, hourly_rate)
                        VALUES ('LEGACY_1', 'Duplicate Code', 1.00)
                        """))
                        .isInstanceOf(SQLException.class);
                connection.rollback();

                setSearchPath(connection, schema);
                assertThatThrownBy(() -> execute(connection, """
                        INSERT INTO dict_process_detail (category_id, detail_name)
                        SELECT id, 'CNC'
                        FROM dict_process_category
                        WHERE process_code = 'LEGACY_1'
                        """))
                        .isInstanceOf(SQLException.class);
                connection.rollback();
            } finally {
                dropTestSchema(connection, schema);
            }
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

    private static void createLegacyDictProcess(Connection connection, String schema) throws SQLException {
        setSearchPath(connection, schema);
        execute(connection, """
                CREATE TABLE dict_process (
                    id INTEGER PRIMARY KEY,
                    process_name VARCHAR(120),
                    calc_type VARCHAR(30),
                    sort_no INTEGER,
                    price_1_5 NUMERIC(10, 2),
                    price_5_10 NUMERIC(10, 2),
                    price_10_50 NUMERIC(10, 2),
                    price_50_100 NUMERIC(10, 2),
                    price_100_500 NUMERIC(10, 2),
                    price_500_1000 NUMERIC(10, 2),
                    price_1000_3000 NUMERIC(10, 2),
                    price_3000_plus NUMERIC(10, 2)
                )
                """);
        connection.commit();
    }

    private static void executeMigration(Connection connection, String schema) throws IOException, SQLException {
        setSearchPath(connection, schema);
        execute(connection, read(MIGRATION));
    }

    private static void setSearchPath(Connection connection, String schema) throws SQLException {
        assertThat(schema).startsWith(TEST_SCHEMA_PREFIX);
        execute(connection, "SET search_path TO " + schema);
    }

    private static void dropTestSchema(Connection connection, String schema) throws SQLException {
        if (!schema.startsWith(TEST_SCHEMA_PREFIX)) {
            throw new IllegalArgumentException("Refusing to drop non-test schema: " + schema);
        }
        connection.rollback();
        execute(connection, "SET search_path TO public");
        execute(connection, "DROP SCHEMA " + schema + " CASCADE");
        connection.commit();
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

    private static int categoryCount(Connection connection, String schema) throws SQLException {
        return count(connection, schema, "dict_process_category");
    }

    private static int detailCount(Connection connection, String schema) throws SQLException {
        return count(connection, schema, "dict_process_detail");
    }

    private static int count(Connection connection, String schema, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + schema + "." + table)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static String categoryName(Connection connection, String schema, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT category_name
                FROM %s.dict_process_category
                WHERE process_code = ?
                """.formatted(schema))) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static BigDecimal hourlyRate(Connection connection, String schema, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT hourly_rate
                FROM %s.dict_process_category
                WHERE process_code = ?
                """.formatted(schema))) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBigDecimal(1);
            }
        }
    }

    private static boolean detailExists(Connection connection, String schema, String code, String detailName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM %s.dict_process_detail d
                    JOIN %s.dict_process_category c ON c.id = d.category_id
                    WHERE c.process_code = ? AND d.detail_name = ?
                )
                """.formatted(schema, schema))) {
            statement.setString(1, code);
            statement.setString(2, detailName);
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

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
