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

class FinalPriceFieldRemovalMigrationTest {
    private static final String TEST_SCHEMA_PREFIX = "qpo_final_price_migration_";
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V87__remove_final_price_fields.sql");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "schema.sql");

    @Test
    void migrationAndBootstrapSchemaUseCanonicalPriceFields() throws IOException {
        String migration = read(MIGRATION);
        String schema = read(SCHEMA);

        assertThat(migration)
                .contains(
                        "item_quote_version contains conflicting unit prices",
                        "item_quote_version contains conflicting total prices",
                        "sales_order_item contains conflicting unit prices",
                        "sales_order_item contains conflicting total prices",
                        "SET tax_included_unit_price = final_unit_price",
                        "SET tax_included_total_price = final_total_price",
                        "ROUND(tax_included_unit_price * qty, 2)",
                        "DROP INDEX IF EXISTS idx_item_quote_version_repeat_released_key",
                        "AND tax_included_unit_price IS NOT NULL",
                        "DROP COLUMN IF EXISTS final_unit_price",
                        "DROP COLUMN IF EXISTS final_total_price");
        assertThat(schema)
                .contains("AND tax_included_unit_price IS NOT NULL")
                .doesNotContain(
                        "final_unit_price NUMERIC",
                        "final_total_price NUMERIC",
                        "AND final_unit_price IS NOT NULL");
    }

    @Test
    void consistentDataIsBackfilledBeforeLegacyColumnsAreRemoved() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                createLegacyTables(connection, schema);
                execute(connection, """
                        INSERT INTO item_quote_version (
                            part_no, version, status, qty, material_data, process_route,
                            final_unit_price, final_total_price, untaxed_unit_price, untaxed_total_price
                        ) VALUES
                            ('RFQ-1', 'A', '已提单', 2, '{}'::jsonb, '[]'::jsonb, 100.00, 200.00, 88.00, 176.00),
                            ('RFQ-2', 'A', '已提单', 3, '{}'::jsonb, '[]'::jsonb, 50.00, 150.00, 44.00, 132.00),
                            ('RFQ-3', 'A', '已提单', 4, '{}'::jsonb, '[]'::jsonb, NULL, NULL, 22.00, 88.00)
                        """);
                execute(connection, """
                        UPDATE item_quote_version
                        SET tax_included_unit_price = 50.00,
                            tax_included_total_price = 150.00
                        WHERE part_no = 'RFQ-2'
                        """);
                execute(connection, """
                        UPDATE item_quote_version
                        SET tax_included_unit_price = 25.00
                        WHERE part_no = 'RFQ-3'
                        """);
                execute(connection, """
                        INSERT INTO sales_order_item (
                            part_no, qty, final_unit_price, final_total_price,
                            untaxed_unit_price, untaxed_total_price
                        ) VALUES ('SO-1', 3, 33.33, 99.99, 29.50, 88.50)
                        """);
                connection.commit();

                executeMigration(connection, schema);
                connection.commit();

                assertThat(legacyColumnCount(connection, schema)).isZero();
                assertThat(price(connection, schema, "item_quote_version", "RFQ-1", "tax_included_unit_price"))
                        .isEqualByComparingTo("100.00");
                assertThat(price(connection, schema, "item_quote_version", "RFQ-1", "tax_included_total_price"))
                        .isEqualByComparingTo("200.00");
                assertThat(price(connection, schema, "item_quote_version", "RFQ-3", "tax_included_total_price"))
                        .isEqualByComparingTo("100.00");
                assertThat(price(connection, schema, "item_quote_version", "RFQ-1", "untaxed_total_price"))
                        .isEqualByComparingTo("176.00");
                assertThat(price(connection, schema, "sales_order_item", "SO-1", "tax_included_unit_price"))
                        .isEqualByComparingTo("33.33");
                assertThat(price(connection, schema, "sales_order_item", "SO-1", "tax_included_total_price"))
                        .isEqualByComparingTo("99.99");
                assertThat(repeatQuoteIndexDefinition(connection, schema))
                        .contains("tax_included_unit_price IS NOT NULL")
                        .doesNotContain("final_unit_price");
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    @Test
    void unitPriceConflictFailsAndRollsBack() throws Exception {
        assertConflictRollsBack("""
                INSERT INTO item_quote_version (
                    part_no, version, status, qty, material_data, process_route,
                    final_unit_price, final_total_price,
                    tax_included_unit_price, tax_included_total_price
                ) VALUES ('RFQ-CONFLICT', 'A', '已提单', 2, '{}'::jsonb, '[]'::jsonb,
                          100.00, 200.00, 101.00, 200.00)
                """, "item_quote_version contains conflicting unit prices");
    }

    @Test
    void totalPriceConflictFailsAndRollsBack() throws Exception {
        assertConflictRollsBack("""
                INSERT INTO sales_order_item (
                    part_no, qty, final_unit_price, final_total_price,
                    tax_included_unit_price, tax_included_total_price
                ) VALUES ('SO-CONFLICT', 2, 100.00, 200.00, 100.00, 201.00)
                """, "sales_order_item contains conflicting total prices");
    }

    private void assertConflictRollsBack(String conflictInsert, String expectedMessage) throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                createLegacyTables(connection, schema);
                execute(connection, conflictInsert);
                connection.commit();

                assertThatThrownBy(() -> executeMigration(connection, schema))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining(expectedMessage);
                connection.rollback();

                assertThat(legacyColumnCount(connection, schema)).isEqualTo(4);
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

    private static void createLegacyTables(Connection connection, String schema) throws SQLException {
        setSearchPath(connection, schema);
        execute(connection, """
                CREATE TABLE item_quote_version (
                    id BIGSERIAL PRIMARY KEY,
                    part_no VARCHAR(255),
                    version VARCHAR(50),
                    status VARCHAR(50),
                    qty INTEGER,
                    material_data JSONB,
                    process_route JSONB,
                    final_unit_price NUMERIC(12, 2),
                    final_total_price NUMERIC(12, 2),
                    tax_included_unit_price NUMERIC(12, 2),
                    untaxed_unit_price NUMERIC(12, 2),
                    tax_included_total_price NUMERIC(12, 2),
                    untaxed_total_price NUMERIC(12, 2)
                )
                """);
        execute(connection, """
                CREATE TABLE sales_order_item (
                    id BIGSERIAL PRIMARY KEY,
                    part_no VARCHAR(255),
                    qty INTEGER,
                    final_unit_price NUMERIC(14, 4),
                    final_total_price NUMERIC(14, 2),
                    tax_included_unit_price NUMERIC(14, 4),
                    untaxed_unit_price NUMERIC(14, 4),
                    tax_included_total_price NUMERIC(14, 2),
                    untaxed_total_price NUMERIC(14, 2)
                )
                """);
        execute(connection, """
                CREATE INDEX idx_item_quote_version_repeat_released_key
                    ON item_quote_version(part_no, version, id DESC)
                    WHERE status = '已提单'
                      AND final_unit_price IS NOT NULL
                      AND material_data IS NOT NULL
                      AND process_route IS NOT NULL
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

    private static int legacyColumnCount(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name IN ('item_quote_version', 'sales_order_item')
                  AND column_name IN ('final_unit_price', 'final_total_price')
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static BigDecimal price(Connection connection, String schema, String table,
                                    String partNo, String column) throws SQLException {
        assertThat(table).isIn("item_quote_version", "sales_order_item");
        assertThat(column).isIn(
                "tax_included_unit_price", "tax_included_total_price",
                "untaxed_unit_price", "untaxed_total_price");
        String sql = "SELECT " + column + " FROM " + schema + "." + table + " WHERE part_no = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, partNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBigDecimal(1);
            }
        }
    }

    private static String repeatQuoteIndexDefinition(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = ?
                  AND indexname = 'idx_item_quote_version_repeat_released_key'
                """)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
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
        return value != null && !value.isBlank();
    }
}
