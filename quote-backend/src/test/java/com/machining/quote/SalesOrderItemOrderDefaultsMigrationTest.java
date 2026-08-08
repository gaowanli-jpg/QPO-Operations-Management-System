package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderItemOrderDefaultsMigrationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SALES_ORDER_ITEM = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");

    @Test
    void baselineExposesOrderLevelFieldsOnSalesOrderItems() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String itemTable = tableDefinition(schema, "sales_order_item");
        String entity = read(SALES_ORDER_ITEM);

        assertThat(itemTable)
                .contains(
                        "sales_order_date date",
                        "customer_order_title character varying(255)",
                        "customer_order_no character varying(120)",
                        "customer_project_no character varying(120)",
                        "internal_project_no character varying(120)",
                        "order_type character varying(50)");
        assertThat(entity)
                .contains(
                        "private LocalDate salesOrderDate;",
                        "private String customerOrderTitle;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String internalProjectNo;",
                        "private String orderType;");
    }

    private static String tableDefinition(String schema, String tableName) {
        int tableStart = schema.indexOf("CREATE TABLE " + tableName + " (");
        int tableEnd = schema.indexOf(");", tableStart);
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        assertThat(tableEnd).isGreaterThan(tableStart);
        return schema.substring(tableStart, tableEnd);
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
