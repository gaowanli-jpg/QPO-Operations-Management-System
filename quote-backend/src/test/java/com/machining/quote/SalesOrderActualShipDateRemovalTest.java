package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderActualShipDateRemovalTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");

    @Test
    void baselineDoesNotCreateActualShipDateOnSalesOrderOrRfqItems() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(tableDefinition(schema, "item_quote_version")).doesNotContain("actual_ship_date");
        assertThat(tableDefinition(schema, "sales_order_item")).doesNotContain("actual_ship_date");
    }

    @Test
    void salesOrderImportAndU9ContractsNoLongerExposeActualShipDate() throws IOException {
        assertThat(read(javaSource("entity", "SalesOrderItem.java"))).doesNotContain("actualShipDate");
        assertThat(read(javaSource("entity", "ItemQuoteVersion.java"))).doesNotContain("actualShipDate");
        assertThat(read(javaSource("dto", "SalesOrderQpoExcelLineDTO.java"))).doesNotContain("actualShipDate");
        assertThat(read(javaSource("dto", "SalesOrderU9SyncPreviewLine.java"))).doesNotContain("actualShipDate");
        assertThat(read(javaSource("service", "SalesOrderQpoExcelParser.java")))
                .doesNotContain("ACTUAL_SHIP_DATE", "setActualShipDate");
        assertThat(read(javaSource("service", "SalesOrderQpoImportService.java")))
                .doesNotContain("setActualShipDate", "getActualShipDate");
        assertThat(read(javaSource("service", "SalesOrderU9SyncService.java")))
                .doesNotContain("setActualShipDate", "getActualShipDate");
    }

    private static Path javaSource(String packageName, String fileName) {
        return Path.of("src", "main", "java", "com", "machining", "quote", packageName, fileName);
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
