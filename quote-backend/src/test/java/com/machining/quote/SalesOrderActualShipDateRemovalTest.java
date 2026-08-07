package com.machining.quote;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

class SalesOrderActualShipDateRemovalTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V89__remove_sales_order_actual_ship_date.sql");
    private static final String TEST_SCHEMA_PREFIX = "qpo_remove_sales_order_actual_ship_date_";

    @Test
    void migrationAndBootstrapSchemaRemoveActualShipDateFromBothItemTables() throws IOException {
        assertThat(MIGRATION).exists();
        assertThat(read(MIGRATION))
                .contains("ALTER TABLE sales_order_item")
                .contains("ALTER TABLE item_quote_version")
                .contains("DROP COLUMN IF EXISTS actual_ship_date");

        String schema = read(Path.of("src", "main", "resources", "schema.sql"));
        assertThat(tableDefinition(schema, "item_quote_version")).doesNotContain("actual_ship_date");
        assertThat(tableDefinition(schema, "sales_order_item")).doesNotContain("actual_ship_date");
        assertThat(schema).doesNotContain(
                "ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS actual_ship_date DATE");
    }

    @Test
    void salesOrderImportAndU9ContractsNoLongerExposeActualShipDate() throws IOException {
        assertThat(read(javaSource("entity", "SalesOrderItem.java")))
                .doesNotContain("actualShipDate");
        assertThat(read(javaSource("entity", "ItemQuoteVersion.java")))
                .doesNotContain("actualShipDate");
        assertThat(read(javaSource("dto", "SalesOrderQpoExcelLineDTO.java")))
                .doesNotContain("actualShipDate");
        assertThat(read(javaSource("dto", "SalesOrderU9SyncPreviewLine.java")))
                .doesNotContain("actualShipDate");
        assertThat(read(javaSource("service", "SalesOrderQpoExcelParser.java")))
                .doesNotContain("ACTUAL_SHIP_DATE")
                .doesNotContain("setActualShipDate");
        assertThat(read(javaSource("service", "SalesOrderQpoImportService.java")))
                .doesNotContain("setActualShipDate")
                .doesNotContain("getActualShipDate");
        assertThat(read(javaSource("service", "SalesOrderU9SyncService.java")))
                .doesNotContain("setActualShipDate")
                .doesNotContain("getActualShipDate");
    }

    @Test
    void migrationDropsColumnFromExistingSalesOrderAndRfqItems() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = TEST_SCHEMA_PREFIX + UUID.randomUUID().toString().replace("-", "");
            try {
                execute(connection, "CREATE SCHEMA " + schema);
                execute(connection, "SET search_path TO " + schema);
                execute(connection, """
                        CREATE TABLE sales_order_item (
                            id BIGINT PRIMARY KEY,
                            actual_ship_date DATE
                        )
                        """);
                execute(connection, """
                        CREATE TABLE item_quote_version (
                            id BIGINT PRIMARY KEY,
                            actual_ship_date DATE
                        )
                        """);
                connection.commit();
                execute(connection, read(MIGRATION));
                assertThat(columnExists(connection, schema, "sales_order_item", "actual_ship_date")).isFalse();
                assertThat(columnExists(connection, schema, "item_quote_version", "actual_ship_date")).isFalse();
            } finally {
                connection.rollback();
                execute(connection, "SET search_path TO public");
                execute(connection, "DROP SCHEMA " + schema + " CASCADE");
                connection.commit();
            }
        }
    }

    private static Path javaSource(String packageName, String fileName) {
        return Path.of("src", "main", "java", "com", "machining", "quote", packageName, fileName);
    }

    private static String tableDefinition(String schema, String tableName) {
        int tableStart = schema.indexOf("CREATE TABLE IF NOT EXISTS " + tableName + " (");
        int tableEnd = schema.indexOf(");", tableStart);
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        assertThat(tableEnd).isGreaterThan(tableStart);
        return schema.substring(tableStart, tableEnd);
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

    private static boolean columnExists(Connection connection, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
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
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
