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

class RfqReleaseDerivedSalesOrderStage1Test {
    private static final String TEST_SCHEMA_PREFIX = "qpo_rfq_release_sales_order_";
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V90__rfq_release_derived_sales_order.sql");
    private static final Path SCHEMA = Path.of("src", "main", "resources", "schema.sql");
    private static final Path ITEM_QUOTE_VERSION = javaSource("entity", "ItemQuoteVersion.java");
    private static final Path SALES_ORDER_HEADER = javaSource("entity", "SalesOrderHeader.java");
    private static final Path SALES_ORDER_ITEM = javaSource("entity", "SalesOrderItem.java");
    private static final Path SOURCE_RFQ = javaSource("entity", "SalesOrderSourceRfq.java");
    private static final Path SOURCE_RFQ_MAPPER = javaSource("mapper", "SalesOrderSourceRfqMapper.java");
    private static final Path RELEASE_SERVICE = javaSource("service", "InternalTargetLeadTimeService.java");

    @Test
    void migrationAndBootstrapSchemaDefineRfqItemOrderAttributes() throws IOException {
        String migration = read(MIGRATION);
        String schema = read(SCHEMA);

        assertThat(migration)
                .contains(
                        "ALTER TABLE item_quote_version",
                        "ADD COLUMN IF NOT EXISTS sales_order_date DATE",
                        "ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255)",
                        "COMMENT ON COLUMN item_quote_version.sales_order_date",
                        "COMMENT ON COLUMN item_quote_version.customer_order_no",
                        "COMMENT ON COLUMN item_quote_version.customer_project_no",
                        "COMMENT ON COLUMN item_quote_version.customer_order_title",
                        "COMMENT ON COLUMN item_quote_version.order_type",
                        "COMMENT ON COLUMN item_quote_version.internal_project_no");
        assertThat(schema)
                .contains(
                        "sales_order_date DATE",
                        "customer_order_title VARCHAR(255)",
                        "ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_date DATE",
                        "ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255)");
    }

    @Test
    void migrationAndBootstrapSchemaDefineDerivedSalesOrderSources() throws IOException {
        String migration = read(MIGRATION);
        String schema = read(SCHEMA);

        assertDerivedSourceModel(migration);
        assertDerivedSourceModel(schema);
        assertThat(migration)
                .contains(
                        "UPDATE sales_order_header",
                        "UPDATE sales_order_item",
                        "DEFAULT 'DIRECT_IMPORT'",
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_header_release_batch",
                        "CREATE INDEX IF NOT EXISTS idx_sales_order_header_source_type_created",
                        "CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_rfq",
                        "CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_sales_order",
                        "CREATE INDEX IF NOT EXISTS idx_sales_order_item_source_order_item",
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_source_ticket",
                        "CONSTRAINT fk_sales_order_item_source_ticket",
                        "REFERENCES order_delivery_ticket(id)")
                .doesNotContain("ALTER TABLE order_delivery_ticket ADD COLUMN");
    }

    @Test
    void entitiesAndMapperExposeStageOneSourceFields() throws IOException {
        String itemQuoteVersion = read(ITEM_QUOTE_VERSION);
        String salesOrderHeader = read(SALES_ORDER_HEADER);
        String salesOrderItem = read(SALES_ORDER_ITEM);
        String sourceRfq = read(SOURCE_RFQ);
        String sourceRfqMapper = read(SOURCE_RFQ_MAPPER);

        assertThat(itemQuoteVersion)
                .contains(
                        "private LocalDate salesOrderDate;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String customerOrderTitle;",
                        "private String orderType;",
                        "private String internalProjectNo;")
                .doesNotContain(
                        "@Deprecated\n    private String customerOrderNo;",
                        "@Deprecated\n    private String customerProjectNo;",
                        "@Deprecated\n    private String internalProjectNo;");
        assertThat(salesOrderHeader)
                .contains(
                        "private String sourceType;",
                        "private String sourceReleaseBatchKey;",
                        "private String sourceReleaseRequestHash;");
        assertThat(salesOrderItem)
                .contains(
                        "private String sourceType;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;",
                        "private Integer sourceTicketId;");
        assertThat(sourceRfq)
                .contains(
                        "@TableName(\"sales_order_source_rfq\")",
                        "private Long id;",
                        "private Long salesOrderId;",
                        "private Integer rfqId;",
                        "private LocalDateTime createdAt;");
        assertThat(sourceRfqMapper)
                .contains(
                        "@Mapper",
                        "interface SalesOrderSourceRfqMapper extends BaseMapper<SalesOrderSourceRfq>");
    }

    @Test
    void releaseRowsReturnAllSixRawRfqItemOrderAttributes() throws IOException {
        assertThat(read(RELEASE_SERVICE))
                .contains(
                        "row.put(\"salesOrderDate\", item.getSalesOrderDate())",
                        "row.put(\"customerOrderNo\", item.getCustomerOrderNo())",
                        "row.put(\"customerProjectNo\", item.getCustomerProjectNo())",
                        "row.put(\"customerOrderTitle\", item.getCustomerOrderTitle())",
                        "row.put(\"orderType\", item.getOrderType())",
                        "row.put(\"internalProjectNo\", item.getInternalProjectNo())");
    }

    @Test
    void migrationExecutesAgainstLegacyTablesAndPreservesExistingRows() throws Exception {
        try (Connection connection = openDatabase()) {
            String schema = createTestSchema(connection);
            try {
                createLegacyTables(connection, schema);
                insertLegacyRows(connection);
                connection.commit();

                executeMigration(connection, schema);
                connection.commit();

                assertThat(textValue(connection, schema, "sales_order_header", "source_type", 101L))
                        .isEqualTo("DIRECT_IMPORT");
                assertThat(textValue(connection, schema, "sales_order_item", "source_type", 201L))
                        .isEqualTo("DIRECT_IMPORT");
                assertThat(columnExists(connection, schema, "item_quote_version", "sales_order_date")).isTrue();
                assertThat(columnExists(connection, schema, "item_quote_version", "customer_order_title")).isTrue();
                assertThat(tableExists(connection, schema, "sales_order_source_rfq")).isTrue();
                assertThat(indexExists(connection, schema, "uk_sales_order_header_release_batch")).isTrue();
                assertThat(indexExists(connection, schema, "uk_sales_order_item_source_ticket")).isTrue();

                insertExplainFixtures(connection, schema);
                assertExplainUsesIndex(
                        connection,
                        "SELECT id FROM sales_order_header WHERE source_release_batch_key = 'batch-1'",
                        "uk_sales_order_header_release_batch");
                assertExplainUsesIndex(
                        connection,
                        "SELECT sales_order_id FROM sales_order_source_rfq WHERE rfq_id = 2",
                        "idx_sales_order_source_rfq_rfq");
                assertExplainUsesIndex(
                        connection,
                        "SELECT id FROM sales_order_item WHERE source_type = 'RFQ' "
                                + "AND source_order_id = 2 AND source_item_id = 12",
                        "idx_sales_order_item_source_order_item");
                assertExplainUsesIndex(
                        connection,
                        "SELECT id FROM sales_order_item WHERE source_ticket_id = 701",
                        "uk_sales_order_item_source_ticket");
            } finally {
                dropTestSchema(connection, schema);
            }
        }
    }

    private static void assertDerivedSourceModel(String sql) {
        assertThat(sql)
                .contains(
                        "source_type VARCHAR(30)",
                        "source_release_batch_key VARCHAR(100)",
                        "source_release_request_hash VARCHAR(64)",
                        "CREATE TABLE IF NOT EXISTS sales_order_source_rfq",
                        "sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE",
                        "rfq_id INTEGER NOT NULL REFERENCES order_rfq(id)",
                        "CONSTRAINT uk_sales_order_source_rfq UNIQUE (sales_order_id, rfq_id)",
                        "source_order_id BIGINT",
                        "source_item_id BIGINT",
                        "source_ticket_id INTEGER");
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
        execute(connection, "CREATE TABLE order_rfq (id INTEGER PRIMARY KEY)");
        execute(connection, """
                CREATE TABLE item_quote_version (
                    id SERIAL PRIMARY KEY,
                    rfq_id INTEGER REFERENCES order_rfq(id),
                    order_type VARCHAR(20),
                    customer_order_no VARCHAR(120),
                    customer_project_no VARCHAR(120),
                    internal_project_no VARCHAR(120)
                )
                """);
        execute(connection, """
                CREATE TABLE sales_order_header (
                    id BIGINT PRIMARY KEY,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        execute(connection, "CREATE TABLE order_delivery_ticket (id SERIAL PRIMARY KEY)");
        execute(connection, """
                CREATE TABLE sales_order_item (
                    id BIGINT PRIMARY KEY,
                    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id)
                )
                """);
        connection.commit();
    }

    private static void insertLegacyRows(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO order_rfq(id) VALUES (1)");
        execute(connection, """
                INSERT INTO item_quote_version (
                    id, rfq_id, order_type, customer_order_no, customer_project_no, internal_project_no
                ) VALUES (
                    1, 1, 'NORMAL', 'CO-1', 'CP-1', 'IP-1'
                )
                """);
        execute(connection, "INSERT INTO sales_order_header(id) VALUES (101)");
        execute(connection, "INSERT INTO sales_order_item(id, sales_order_id) VALUES (201, 101)");
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

    private static String textValue(
            Connection connection,
            String schema,
            String table,
            String column,
            Long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM " + schema + "." + table + " WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static boolean columnExists(
            Connection connection,
            String schema,
            String table,
            String column) throws SQLException {
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

    private static void insertExplainFixtures(Connection connection, String schema) throws SQLException {
        setSearchPath(connection, schema);
        execute(connection, """
                INSERT INTO sales_order_header (
                    id, source_type, source_release_batch_key, source_release_request_hash
                ) VALUES (102, 'RFQ_RELEASE', 'batch-1', repeat('a', 64))
                """);
        execute(connection, "INSERT INTO order_rfq(id) VALUES (2)");
        execute(connection, "INSERT INTO sales_order_source_rfq(sales_order_id, rfq_id) VALUES (102, 2)");
        execute(connection, "INSERT INTO order_delivery_ticket(id) VALUES (701)");
        execute(connection, """
                INSERT INTO sales_order_item (
                    id, sales_order_id, source_type, source_order_id, source_item_id, source_ticket_id
                ) VALUES (202, 102, 'RFQ', 2, 12, 701)
                """);
        connection.commit();
    }

    private static void assertExplainUsesIndex(
            Connection connection,
            String query,
            String indexName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL enable_seqscan = off");
            StringBuilder plan = new StringBuilder();
            try (ResultSet resultSet = statement.executeQuery("EXPLAIN " + query)) {
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
            }
            assertThat(plan.toString())
                    .as("EXPLAIN plan for %s", query)
                    .contains(indexName);
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

    private static Path javaSource(String packageName, String fileName) {
        return Path.of("src", "main", "java", "com", "machining", "quote", packageName, fileName);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
