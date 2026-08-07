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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderItemOrderDefaultsMigrationTest {
    private static final String TEST_SCHEMA_PREFIX = "qpo_sales_order_item_defaults_";
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V88__sales_order_item_order_defaults.sql");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "schema.sql");
    private static final Path SALES_ORDER_ITEM = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");

    @Test
    void migrationAndBootstrapSchemaExposeOrderLevelSalesOrderItemFields() throws IOException {
        String migration = read(MIGRATION);
        String schema = read(SCHEMA);
        String entity = read(SALES_ORDER_ITEM);

        assertThat(migration)
                .contains("ALTER TABLE sales_order_item")
                .contains("ADD COLUMN IF NOT EXISTS sales_order_date DATE")
                .contains("ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255)")
                .contains("UPDATE sales_order_item item")
                .contains("SET sales_order_date = header.sales_order_date")
                .contains("customer_order_title = header.customer_order_title")
                .contains("FROM sales_order_header header")
                .contains("AND (item.sales_order_date IS NULL OR item.customer_order_title IS NULL)");
        assertThat(schema)
                .contains("ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS sales_order_date DATE")
                .contains("ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255)");
        assertThat(entity)
                .contains("private LocalDate salesOrderDate;")
                .contains("private String customerOrderTitle;");
    }

    @Test
    void migrationBackfillsItemDefaultsFromHeaderWithoutBreakingLegacyData() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                createLegacyTables(connection, schema);
                execute(connection, """
                        INSERT INTO sales_order_header (
                            id, sales_order_date, customer_order_title
                        ) VALUES (
                            1001, DATE '2026-03-08', '客户抬头A'
                        )
                        """);
                execute(connection, """
                        INSERT INTO sales_order_item (
                            id, sales_order_id, part_no, qty
                        ) VALUES (
                            2001, 1001, 'P-001', 2
                        )
                        """);
                connection.commit();

                executeMigration(connection, schema);
                connection.commit();

                assertThat(itemDate(connection, schema, 2001L)).isEqualTo(LocalDate.of(2026, 3, 8));
                assertThat(itemTitle(connection, schema, 2001L)).isEqualTo("客户抬头A");
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
                CREATE TABLE sales_order_header (
                    id BIGINT PRIMARY KEY,
                    sales_order_date DATE,
                    customer_order_title VARCHAR(200)
                )
                """);
        execute(connection, """
                CREATE TABLE sales_order_item (
                    id BIGINT PRIMARY KEY,
                    sales_order_id BIGINT NOT NULL,
                    part_no VARCHAR(100) NOT NULL,
                    qty INTEGER NOT NULL
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

    private static LocalDate itemDate(Connection connection, String schema, Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sales_order_date
                FROM %s.sales_order_item
                WHERE id = ?
                """.formatted(schema))) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getDate(1).toLocalDate();
            }
        }
    }

    private static String itemTitle(Connection connection, String schema, Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT customer_order_title
                FROM %s.sales_order_item
                WHERE id = ?
                """.formatted(schema))) {
            statement.setLong(1, id);
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

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
