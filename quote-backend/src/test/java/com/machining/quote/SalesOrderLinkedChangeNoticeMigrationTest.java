package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLinkedChangeNoticeMigrationTest {
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void migrationCreatesAppendOnlySnapshotTableIndexesAndAssignablePermissions() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V55__sales_order_linked_change_notice.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("CREATE TABLE sales_order_linked_change_notice")
                .contains("linked_discard_event_key")
                .contains("sales_order_item_id")
                .contains("wms_submitted")
                .contains("mes_production_submitted")
                .contains("stock_picking_submitted")
                .contains("handling_status")
                .contains("change:linked-change:view")
                .contains("change:linked-change:customer-select")
                .contains("change:linked-change:handle")
                .contains("SUPER_ADMIN", "MARKET_USER", "MARKET_MANAGER", "READONLY")
                .contains("CREATE UNIQUE INDEX")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");

        assertThat(Files.exists(MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql"))).isTrue();
        assertThat(Files.exists(MIGRATION_DIR.resolve("V2__qpo_baseline_seed_data.sql"))).isTrue();
    }

    @Test
    void historicalOrderFieldFiltersRemainAndMigrationSequenceIncludesCurrentMigrations() throws Exception {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__.+\\.sql$");
        int maxVersion;
        try (var files = Files.list(MIGRATION_DIR)) {
            maxVersion = files.map(path -> path.getFileName().toString())
                    .map(versionPattern::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                    .max().orElse(0);
        }
        assertThat(maxVersion).isEqualTo(59);

        Path migration = MIGRATION_DIR.resolve("V56__sales_order_linked_change_notice_filter_fields.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("customer_order_no")
                .contains("internal_project_no")
                .contains("sales_order_item")
                .contains("sales_order_header")
                .contains("CREATE INDEX")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");
    }

    @Test
    void version57AddsOptionalWmsAllocationWithoutChangingBaselines() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V57__sales_order_optional_wms_allocation.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("allocation_source")
                .contains("MES_MATERIAL")
                .contains("WMS_RESERVATION")
                .contains("PRODUCTION_REQUIRED")
                .contains("RESULT_UNKNOWN")
                .contains("APPLY_FAILED")
                .contains("market:sales-order:wms-allocate")
                .contains("existing_role.role_code")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");
    }

    @Test
    void version58LinksSalesOrderItemsToAllocationsWithoutChangingBaselines() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V58__sales_order_item_allocation_link.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS allocation_id INTEGER")
                .contains("allocation_type VARCHAR(40)")
                .contains("CHECK (qty >= 0)")
                .contains("uk_sales_order_item_allocation_id")
                .contains("source_ticket_id")
                .contains("V58_PRODUCTION_REPAIR")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");
    }
}
