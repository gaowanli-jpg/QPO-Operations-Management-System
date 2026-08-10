package com.machining.quote;

import org.flywaydb.core.Flyway;
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
                "V3__repeat_released_snapshot_candidate_index.sql");
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
                        "3|repeat released snapshot candidate index|true");
                assertThat(tableExists(connection, schema, "dict_process_category")).isTrue();
                assertThat(tableExists(connection, schema, "dict_process_detail")).isTrue();
                assertThat(tableExists(connection, schema, "dict_process")).isFalse();
                assertThat(columnExists(connection, schema, "item_quote_version", "final_unit_price")).isFalse();
                assertThat(columnExists(connection, schema, "sales_order_item", "final_unit_price")).isFalse();
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

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
