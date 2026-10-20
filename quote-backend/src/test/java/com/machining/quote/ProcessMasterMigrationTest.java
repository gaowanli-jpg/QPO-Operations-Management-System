package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMasterMigrationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PROCESS_DETAIL_HOURLY_RATE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V16__process_detail_hourly_rate.sql");
    private static final Path QPO_OWNED_PROCESS_DETAIL_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V36__qpo_owned_process_detail_code.sql");

    @Test
    void baselineDefinesTwoLevelProcessMasterTablesConstraintsAndIndexes() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(tableDefinition(schema, "dict_process_category"))
                .contains(
                        "process_code character varying(64) NOT NULL",
                        "category_name character varying(120) NOT NULL",
                        "hourly_rate numeric(18,4) DEFAULT 0 NOT NULL",
                        "source_type character varying(30) DEFAULT 'LOCAL'::character varying NOT NULL",
                        "enabled boolean DEFAULT true NOT NULL",
                        "CONSTRAINT chk_dict_process_category_rate",
                        "CONSTRAINT chk_dict_process_category_source");
        assertThat(tableDefinition(schema, "dict_process_detail"))
                .contains(
                        "category_id bigint NOT NULL",
                        "detail_name character varying(120) NOT NULL",
                        "detail_remarks character varying(500)",
                        "enabled boolean DEFAULT true NOT NULL");
        assertThat(schema)
                .contains(
                        "uk_dict_process_category_code",
                        "uk_dict_process_detail_category_name",
                        "idx_dict_process_category_enabled_sort",
                        "idx_dict_process_category_name",
                        "idx_dict_process_category_mes_external",
                        "idx_dict_process_detail_category_enabled_sort",
                        "idx_dict_process_detail_name")
                .doesNotContain("CREATE TABLE dict_process (");
    }

    @Test
    void baselineSeedLeavesConcreteProcessRowsForMesSync() throws IOException {
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .contains(
                        "SELECT pg_catalog.setval('dict_process_category_id_seq', 1, false)",
                        "SELECT pg_catalog.setval('dict_process_detail_id_seq', 1, false)")
                .doesNotContain("INSERT INTO dict_process ");
        assertThat(seed)
                .doesNotContain(
                        "INSERT INTO dict_process_category",
                        "INSERT INTO dict_process_detail",
                        "Baseline process category seed",
                        "Baseline process detail seed");
    }

    @Test
    void processDetailHourlyRateIsAddedByAppendOnlyMigration() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String detailTable = tableDefinition(schema, "dict_process_detail");
        String migration = read(PROCESS_DETAIL_HOURLY_RATE_MIGRATION);

        assertThat(detailTable)
                .doesNotContain("hourly_rate numeric")
                .doesNotContain("hourly_rate NUMERIC");
        assertThat(migration)
                .contains(
                        "ALTER TABLE dict_process_detail",
                        "ADD COLUMN IF NOT EXISTS hourly_rate NUMERIC(18, 6)",
                        "COMMENT ON COLUMN dict_process_detail.hourly_rate")
                .doesNotContain("CREATE TABLE dict_process_detail");
    }

    @Test
    void qpoOwnedProcessDetailCodeMigrationDecouplesSpecificProcessesFromMesCategories() throws IOException {
        String schema = read(BASELINE_SCHEMA);
        String seed = read(BASELINE_SEED);
        String migration = read(QPO_OWNED_PROCESS_DETAIL_MIGRATION);
        String compactMigration = migration.replaceAll("\\s+", " ");

        assertThat(compactMigration)
                .contains(
                        "ALTER TABLE dict_process_detail ADD COLUMN IF NOT EXISTS process_code VARCHAR(80)",
                        "DELETE FROM dict_process_arrangement_item",
                        "DELETE FROM dict_process_detail",
                        "DELETE FROM pricing_option_usage_stat",
                        "option_type = 'PROCESS_CATEGORY'",
                        "ALTER TABLE dict_process_detail DROP CONSTRAINT IF EXISTS dict_process_detail_category_id_fkey",
                        "ALTER TABLE dict_process_detail ALTER COLUMN category_id DROP NOT NULL",
                        "ALTER TABLE dict_process_detail ALTER COLUMN hourly_rate SET DEFAULT 0",
                        "ALTER TABLE dict_process_detail ALTER COLUMN hourly_rate SET NOT NULL",
                        "uk_dict_process_detail_process_code",
                        "chk_dict_process_detail_process_code",
                        "chk_dict_process_detail_hourly_rate")
                .doesNotContain("DROP TABLE dict_process_category")
                .doesNotContain("DROP TABLE dict_process_detail");

        assertThat(schema)
                .as("baseline migrations stay immutable; the decoupling is append-only")
                .contains("category_id bigint NOT NULL")
                .doesNotContain("process_code character varying(80) NOT NULL");
        assertThat(seed).doesNotContain("INSERT INTO dict_process_detail");
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
