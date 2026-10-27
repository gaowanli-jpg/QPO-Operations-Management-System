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
        // 全局迁移版本门禁：新增迁移后必须同步抬高这里的期望值，避免漏掉迁移或出现版本回退。
        assertThat(maxVersion).isEqualTo(66);

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

    @Test
    void version61MakesLegacyAllocationQuantityOptionalWithoutChangingBaselines() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V61__allocation_current_data_source.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("ALTER COLUMN qty DROP NOT NULL")
                .contains("Legacy quantity snapshot")
                .contains("new linked allocations do not write it")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");
    }

    @Test
    void version63RetiresSalesOrderChangeWithoutRemovingHistoricalStorage() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V63__retire_sales_order_change_module.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("enabled = FALSE", "visible = FALSE", "assignable = FALSE")
                .contains("change:sales-order:view", "change:sales-order:customer-select",
                        "change:sales-order:order-select", "change:sales-order:update")
                .doesNotContain("DROP TABLE", "DELETE FROM sales_order_change_record");
    }

    @Test
    void version64CreatesNoticePoolAndRemovesRetiredRecordTables() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V64__sales_order_change_notice_pool.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("DROP TABLE IF EXISTS sales_order_change_record_item")
                .contains("DROP TABLE IF EXISTS sales_order_change_record")
                .contains("CREATE TABLE sales_order_change_notice")
                .contains("notice_id")
                .contains("target_external_id")
                .contains("source_biz_id")
                .contains("payload_snapshot_json")
                .contains("uk_sales_order_change_notice_notice_id")
                .contains("uk_sales_order_change_notice_pending_modify")
                .contains("CREATE TABLE sales_order_change_dispatch_setting")
                .contains("VALUES (1, 'MANUAL', 0)")
                .contains("MES_SALES_ORDER_CHANGE_NOTICE")
                .contains("change:sales-change:view")
                .contains("change:sales-change:send")
                .contains("change:sales-change:mode-update")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");
    }

    @Test
    void version65AddsNoticeVisibilityAndPermissionsWithoutEditingHistory() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V65__sales_change_notice_visibility.sql");
        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("ALTER TABLE sales_order_change_notice")
                .contains("is_hidden BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("hidden_reason VARCHAR(500)")
                .contains("hidden_at TIMESTAMP")
                .contains("hidden_by BIGINT")
                .contains("hidden_by_name VARCHAR(100)")
                .contains("restored_at TIMESTAMP")
                .contains("restored_by BIGINT")
                .contains("(237, 233, 'BUTTON', 'change:sales-change:hide'")
                .contains("(238, 233, 'BUTTON', 'change:sales-change:restore'")
                .contains("ON CONFLICT (id) DO UPDATE")
                .contains("ON CONFLICT (role_id, permission_id) DO NOTHING")
                .contains("'SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER'")
                .contains("RAISE EXCEPTION", "id IN (237, 238)", "permission_code IN ('change:sales-change:hide', 'change:sales-change:restore')")
                .contains("system_permission_id_seq")
                .contains(", 238), TRUE)")
                .doesNotContain("READONLY")
                .doesNotContain("V1__qpo_baseline_schema.sql", "V2__qpo_baseline_seed_data.sql");

        assertThat(Files.readString(MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql"), StandardCharsets.UTF_8))
                .doesNotContain("is_hidden");
        assertThat(Files.readString(MIGRATION_DIR.resolve("V2__qpo_baseline_seed_data.sql"), StandardCharsets.UTF_8))
                .doesNotContain("change:sales-change:hide", "change:sales-change:restore");
    }
}
