package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinalPriceFieldRemovalMigrationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");

    @Test
    void baselineUsesCanonicalPriceFieldsAndDoesNotCreateLegacyFinalPriceColumns() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String rfqItem = tableDefinition(schema, "item_quote_version");
        String salesOrderItem = tableDefinition(schema, "sales_order_item");

        assertThat(rfqItem)
                .contains(
                        "tax_included_unit_price numeric",
                        "tax_included_total_price numeric",
                        "untaxed_unit_price numeric",
                        "untaxed_total_price numeric")
                .doesNotContain("final_unit_price", "final_total_price");
        assertThat(salesOrderItem)
                .contains(
                        "tax_included_unit_price numeric",
                        "tax_included_total_price numeric",
                        "untaxed_unit_price numeric",
                        "untaxed_total_price numeric")
                .doesNotContain("final_unit_price", "final_total_price");
        assertThat(schema)
                .contains("idx_item_quote_version_repeat_eligible_key")
                .contains("tax_included_unit_price IS NOT NULL")
                .doesNotContain("final_unit_price", "final_total_price");
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
