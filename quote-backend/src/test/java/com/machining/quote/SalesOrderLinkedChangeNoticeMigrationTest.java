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
    void version56AddsHistoricalOrderFieldFiltersWithoutChangingBaseline() throws Exception {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__.+\\.sql$");
        int maxVersion;
        try (var files = Files.list(MIGRATION_DIR)) {
            maxVersion = files.map(path -> path.getFileName().toString())
                    .map(versionPattern::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                    .max().orElse(0);
        }
        assertThat(maxVersion).isEqualTo(56);

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
}
