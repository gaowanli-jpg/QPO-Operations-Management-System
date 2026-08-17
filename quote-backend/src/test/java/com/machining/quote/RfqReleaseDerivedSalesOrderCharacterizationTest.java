package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReleaseDerivedSalesOrderCharacterizationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ITEM_QUOTE_VERSION = javaSource("entity", "ItemQuoteVersion.java");
    private static final Path SALES_ORDER_HEADER = javaSource("entity", "SalesOrderHeader.java");
    private static final Path SALES_ORDER_ITEM = javaSource("entity", "SalesOrderItem.java");
    private static final Path SOURCE_RFQ = javaSource("entity", "SalesOrderSourceRfq.java");
    private static final Path SOURCE_RFQ_MAPPER = javaSource("mapper", "SalesOrderSourceRfqMapper.java");
    private static final Path RELEASE_SERVICE = javaSource("service", "InternalTargetLeadTimeService.java");

    @Test
    void baselineDefinesRfqItemOrderAttributes() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String rfqItem = tableDefinition(schema, "item_quote_version");

        assertThat(rfqItem)
                .contains(
                        "sales_order_date date",
                        "customer_order_no character varying(120)",
                        "customer_project_no character varying(120)",
                        "customer_order_title character varying(255)",
                        "order_type character varying(20)",
                        "internal_project_no character varying(120)");
    }

    @Test
    void baselineDefinesDerivedSalesOrderSourceModel() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(tableDefinition(schema, "sales_order_header"))
                .contains(
                        "source_type character varying(30) DEFAULT 'DIRECT_IMPORT'::character varying NOT NULL",
                        "source_release_batch_key character varying(100)",
                        "source_release_request_hash character varying(64)");
        assertThat(tableDefinition(schema, "sales_order_source_rfq"))
                .contains(
                        "sales_order_id bigint NOT NULL",
                        "rfq_id integer NOT NULL");
        assertThat(tableDefinition(schema, "sales_order_item"))
                .contains(
                        "source_type character varying(30) DEFAULT 'DIRECT_IMPORT'::character varying NOT NULL",
                        "source_order_id bigint",
                        "source_item_id bigint",
                        "source_ticket_id integer");
        assertThat(schema)
                .contains(
                        "uk_sales_order_header_release_batch",
                        "idx_sales_order_header_source_type_created",
                        "idx_sales_order_source_rfq_rfq",
                        "idx_sales_order_source_rfq_sales_order",
                        "idx_sales_order_item_source_order_item",
                        "uk_sales_order_item_source_ticket",
                        "fk_sales_order_item_source_ticket")
                .doesNotContain("ALTER TABLE order_delivery_ticket ADD COLUMN");
    }

    @Test
    void entitiesAndMapperExposeDerivedSalesOrderSourceFields() throws IOException {
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

    private static String tableDefinition(String schema, String tableName) {
        int tableStart = schema.indexOf("CREATE TABLE " + tableName + " (");
        int tableEnd = schema.indexOf(");", tableStart);
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        assertThat(tableEnd).isGreaterThan(tableStart);
        return schema.substring(tableStart, tableEnd);
    }

    private static Path javaSource(String packageName, String fileName) {
        return Path.of("src", "main", "java", "com", "machining", "quote", packageName, fileName);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
