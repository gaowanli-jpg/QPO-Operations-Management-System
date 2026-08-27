package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PricingMarkupFeeMigrationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PRICING_MARKUP_FEE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V23__pricing_markup_fee_category_scope.sql");

    @Test
    void pricingMarkupFeeMigrationCreatesUnifiedTableAndRetiresLegacyTables() throws IOException {
        String migration = read(PRICING_MARKUP_FEE_MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE dict_pricing_markup_fee",
                        "pricing_scope",
                        "fee_category",
                        "system_required",
                        "BASIC_PERCENT",
                        "TAX",
                        "CUSTOMER_WEIGHT",
                        "NORMAL",
                        "ASSEMBLY")
                .containsPattern("(?is)INSERT INTO dict_pricing_markup_fee.*FROM dict_markup_fee")
                .containsPattern("(?is)INSERT INTO dict_pricing_markup_fee.*FROM dict_assembly_markup_fee")
                .contains("DROP TABLE IF EXISTS dict_markup_fee")
                .contains("DROP TABLE IF EXISTS dict_assembly_markup_fee")
                .contains("DROP SEQUENCE IF EXISTS dict_markup_fee_id_seq")
                .contains("DROP SEQUENCE IF EXISTS dict_assembly_markup_fee_id_seq");
    }

    @Test
    void pricingMarkupFeeMigrationSeedsSystemCustomerWeightsAndProtectsTaxRows() throws IOException {
        String migration = read(PRICING_MARKUP_FEE_MIGRATION);

        assertThat(migration)
                .contains("fee_category = 'TAX'")
                .contains("system_required = true")
                .contains("'NORMAL', 'CUSTOMER_WEIGHT', U&'\\5BA2\\6237\\6743\\91CD', 0")
                .contains("'ASSEMBLY', 'CUSTOMER_WEIGHT', U&'\\5BA2\\6237\\6743\\91CD', 0")
                .contains("position(U&'\\7A0E' in fee_name) > 0")
                .contains("enabled", "system_required")
                .contains("false, true");
    }

    @Test
    void pricingMarkupFeeChangeIsAppendOnlyAndDoesNotEditBaselines() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String seed = read(BASELINE_SEED);
        String migration = read(PRICING_MARKUP_FEE_MIGRATION);

        assertThat(schema)
                .contains("CREATE TABLE dict_markup_fee")
                .contains("CREATE TABLE dict_assembly_markup_fee")
                .doesNotContain("dict_pricing_markup_fee")
                .doesNotContain("pricing_scope")
                .doesNotContain("fee_category");
        assertThat(seed)
                .contains("INSERT INTO dict_markup_fee")
                .contains("INSERT INTO dict_assembly_markup_fee")
                .doesNotContain("dict_pricing_markup_fee")
                .doesNotContain("CUSTOMER_WEIGHT");
        assertThat(migration).doesNotContain("V1__qpo_baseline_schema", "V2__qpo_baseline_seed_data");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
