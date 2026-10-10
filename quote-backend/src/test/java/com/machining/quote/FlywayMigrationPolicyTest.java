package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationPolicyTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final int FIRST_POST_BASELINE_VERSION = 5;

    @Test
    void activeMigrationsDoNotReuseCompressedHistoryVersions() throws IOException {
        List<MigrationFile> migrations = migrationFiles();

        assertThat(migrations).extracting(MigrationFile::version).contains(1, 2);
        assertThat(migrations).extracting(MigrationFile::version).doesNotHaveDuplicates();
        assertThat(migrations)
                .filteredOn(migration -> migration.version() > 2)
                .allSatisfy(migration -> assertThat(migration.version())
                        .as("%s must not reuse compressed-history versions V3/V4", migration.fileName())
                        .isGreaterThanOrEqualTo(FIRST_POST_BASELINE_VERSION));
    }

    @Test
    void migrationPolicyScriptsAndDocsPreventComposeTimeSurprises() throws IOException {
        Path newMigrationScript = ROOT.resolve(Path.of("scripts", "new-flyway-migration.ps1"));
        Path migrationShortcutScript = ROOT.resolve("mig.ps1");
        Path checkScript = ROOT.resolve(Path.of("scripts", "check-flyway-migrations.ps1"));
        Path composeScript = ROOT.resolve(Path.of("scripts", "compose-up.ps1"));
        Path composeFile = ROOT.resolve("docker-compose.yml");
        Path policyDoc = ROOT.resolve(Path.of("docs", "ops", "flyway-migration-policy.md"));

        assertThat(newMigrationScript).exists();
        assertThat(migrationShortcutScript).exists();
        assertThat(checkScript).exists();
        assertThat(composeScript).exists();
        assertThat(composeFile).exists();
        assertThat(policyDoc).exists();

        String newMigration = read(newMigrationScript);
        String migrationShortcut = read(migrationShortcutScript);
        String check = read(checkScript);
        String compose = read(composeScript);
        String composeYaml = read(composeFile);
        String doc = read(policyDoc);

        assertThat(newMigration)
                .contains("Position = 0")
                .contains("flyway_schema_history")
                .contains("quote-postgres")
                .contains("FIRST_POST_BASELINE_VERSION")
                .contains("[System.IO.File]::WriteAllText")
                .contains("[System.Text.UTF8Encoding]::new($false)");
        assertThat(migrationShortcut)
                .contains("scripts", "new-flyway-migration.ps1")
                .contains("-ExecutionPolicy", "Bypass")
                .contains("DryRun");
        assertThat(check)
                .contains("compressed-history")
                .contains("V3/V4")
                .contains("flyway_schema_history")
                .contains("exit 1");
        assertThat(compose)
                .contains("check-flyway-migrations.ps1")
                .contains("--env-file")
                .contains("erp.env");
        assertThat(composeYaml)
                .contains("Standard development and deployment startup")
                .contains("docker compose --env-file erp.env up -d --build")
                .contains("compose-up.ps1");
        assertThat(doc)
                .contains("V3/V4")
                .contains("V5")
                .contains("docker compose --env-file erp.env up -d --build")
                .contains("./mig.ps1 u9_customer_sync_schedule_config")
                .contains("scripts/new-flyway-migration.ps1")
                .contains("scripts/compose-up.ps1");
    }

    @Test
    void compressedBaselineKeepsRepeatMatchChangesInV14() throws IOException {
        String baseline = read(MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql"));
        String repeatMatchMigration = read(MIGRATION_DIR.resolve("V14__repeat_match_eligible_flag.sql"));

        assertThat(baseline)
                .doesNotContain("repeat_match_eligible")
                .doesNotContain("idx_item_quote_version_repeat_eligible_key")
                .contains("idx_item_quote_version_repeat_released_key");
        assertThat(repeatMatchMigration)
                .contains("repeat_match_eligible")
                .contains("idx_item_quote_version_repeat_released_key")
                .contains("idx_item_quote_version_repeat_eligible_key");
    }

    @Test
    void repeatIdentityLookupHasDedicatedPartVersionLatestIndex() throws IOException {
        Path migrationPath = MIGRATION_DIR.resolve("V29__repeat_identity_lookup_index.sql");
        assertThat(migrationPath)
                .as("展示型重复身份判断也需要按图号+版本+最新ID稳定命中，避免大表回退扫表")
                .exists();

        String migration = read(migrationPath);
        assertThat(migration)
                .contains("idx_item_quote_version_part_version_latest")
                .contains("ON item_quote_version USING btree (part_no, version, id DESC)");
        assertThat(migration)
                .as("展示型身份索引必须是通用图号版本索引，不能混入可复用快照过滤条件")
                .doesNotContain("tax_included_unit_price")
                .doesNotContain("material_data")
                .doesNotContain("process_route")
                .doesNotContain("repeat_match_eligible")
                .doesNotContain("order_delivery_ticket")
                .doesNotContain("sales_order_item");
    }

    @Test
    void pricingOptionUsageStatsStartEmptyAndUseStableBusinessKeys() throws IOException {
        Path migrationPath = MIGRATION_DIR.resolve("V30__pricing_option_usage_stat.sql");
        assertThat(migrationPath).exists();

        String migration = read(migrationPath);
        assertThat(migration)
                .contains("pricing_option_usage_stat")
                .contains("option_type")
                .contains("option_key")
                .contains("usage_count")
                .contains("uk_pricing_option_usage_stat_type_key")
                .contains("idx_pricing_option_usage_stat_sort")
                .contains("fee_code")
                .contains("uk_dict_extra_fee_item_fee_code");
        assertThat(migration)
                .as("usage stats are counted prospectively; no historical JSON backfill should scan item quote snapshots")
                .doesNotContain("item_quote_version")
                .doesNotContain("material_data")
                .doesNotContain("process_route")
                .doesNotContain("INSERT INTO pricing_option_usage_stat SELECT");
    }

    @Test
    void postBaselinePermissionParentsAreRepairedByPermissionCode() throws IOException {
        Path migrationPath = MIGRATION_DIR.resolve("V43__repair_permission_parent_ids.sql");
        assertThat(migrationPath).exists();

        String migration = read(migrationPath);
        assertThat(migration)
                .contains(
                        "UPDATE system_permission child",
                        "JOIN system_permission parent",
                        "child.permission_code = mapping.child_code",
                        "parent.permission_code = mapping.parent_code",
                        "('market:order:update-item', 'market:order:view')",
                        "('market:delivered-item:discard', 'market:delivered-item:view')",
                        "('market:delivered-item:restore', 'market:delivered-item:view')",
                        "('market:production-item:discard', 'market:production-item:view')",
                        "('market:production-item:restore', 'market:production-item:view')",
                        "('market:sales-order:production-task-notice-export', 'market:sales-order:view')"
                )
                .doesNotContain("parent_id = 3", "parent_id = 158", "parent_id = 190", "parent_id = 199");
    }

    @Test
    void salesOrderManagementButtonPermissionParentsAreRepairedByPermissionCode() throws IOException {
        Path migrationPath = MIGRATION_DIR.resolve("V47__repair_sales_order_management_permission_parent_ids.sql");
        assertThat(migrationPath).exists();

        String migration = read(migrationPath);
        assertThat(migration)
                .contains(
                        "UPDATE system_permission child",
                        "JOIN system_permission parent",
                        "child.permission_code = mapping.child_code",
                        "parent.permission_code = mapping.parent_code",
                        "('market:sales-order:production-task-notice-export', 'market:sales-order:view')",
                        "('market:sales-order:update', 'market:sales-order:view')")
                .doesNotContain("parent_id = 190", "parent_id = 150");
    }

    @Test
    void salesOrderItemLineNumbersAreNormalizedByPersistedRowOrder() throws IOException {
        Path migrationPath = MIGRATION_DIR.resolve("V51__normalize_sales_order_item_line_numbers.sql");
        assertThat(migrationPath).exists();

        String migration = read(migrationPath);
        assertThat(migration)
                .contains(
                        "ROW_NUMBER() OVER",
                        "PARTITION BY sales_order_id",
                        "ORDER BY id",
                        "line_no = ordered.new_line_no",
                        "ALTER TABLE sales_order_item",
                        "ALTER COLUMN line_no SET NOT NULL",
                        "uk_sales_order_item_order_line_no")
                .doesNotContain("ORDER BY line_no");
    }

    @Test
    void allocationLinkMigrationIsAppendOnlyAndContainsZeroQuantityRepairRules() throws IOException {
        String baseline = read(MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql"));
        String v2 = read(MIGRATION_DIR.resolve("V2__qpo_baseline_seed_data.sql"));
        String migration = read(MIGRATION_DIR.resolve("V58__sales_order_item_allocation_link.sql"));

        assertThat(migration)
                .contains("allocation_id", "allocation_type", "FOREIGN KEY (allocation_id)",
                        "qty >= 0", "production_qty", "split_rows", "qty > 0")
                .contains("allocation_type = 'PRODUCTION_REQUIRED'")
                .contains("allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK')");
        assertThat(baseline).doesNotContain("allocation_id BIGINT", "allocation_type VARCHAR(40)");
        assertThat(v2).doesNotContain("allocation_id BIGINT", "allocation_type VARCHAR(40)");
    }

    private static List<MigrationFile> migrationFiles() throws IOException {
        List<MigrationFile> migrations = new ArrayList<>();
        try (var paths = Files.list(MIGRATION_DIR)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString();
                Matcher matcher = VERSION_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    migrations.add(new MigrationFile(Integer.parseInt(matcher.group(1)), fileName));
                }
            }
        }
        return migrations;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private record MigrationFile(int version, String fileName) {
    }
}
