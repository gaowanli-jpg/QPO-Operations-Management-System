package com.machining.quote;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.machining.quote.entity.MesMasterDataSyncRun;
import com.machining.quote.mapper.MesMasterDataSyncRunMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MesMasterDataSchemaStage2Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V8__mes_master_data_sync.sql");
    private static final Path PROCESS_CATEGORY_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "DictProcessCategory.java");

    @Test
    void migrationAddsMesMaterialSourceFieldsAndSyncRunTable() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS source_type",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_external_id",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_material_type",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_purchase_type",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_production_type",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_doc_num",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_standard",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_weight",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_density_raw",
                        "ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_synced_at",
                        "CREATE TABLE IF NOT EXISTS mes_master_data_sync_run",
                        "sync_type VARCHAR(40) NOT NULL",
                        "status VARCHAR(30) NOT NULL",
                        "fetched_count INTEGER NOT NULL DEFAULT 0",
                        "valid_count INTEGER NOT NULL DEFAULT 0",
                        "invalid_count INTEGER NOT NULL DEFAULT 0",
                        "duplicate_count INTEGER NOT NULL DEFAULT 0",
                        "upserted_count INTEGER NOT NULL DEFAULT 0",
                        "disabled_count INTEGER NOT NULL DEFAULT 0",
                        "message TEXT",
                        "created_by BIGINT",
                        "CREATE INDEX IF NOT EXISTS idx_dict_material_source_type",
                        "CREATE INDEX IF NOT EXISTS idx_dict_material_mes_external_id",
                        "CREATE INDEX IF NOT EXISTS idx_mes_master_data_sync_run_type_started",
                        "CREATE INDEX IF NOT EXISTS idx_mes_master_data_sync_run_status");
    }

    @Test
    void mesSyncRunEntityAndMapperExposeBatchCounters() {
        MesMasterDataSyncRun run = new MesMasterDataSyncRun();
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);

        run.setId(10L);
        run.setSyncType("MATERIAL");
        run.setStatus("SUCCESS");
        run.setStartedAt(now);
        run.setFinishedAt(now.plusMinutes(1));
        run.setFetchedCount(20);
        run.setValidCount(18);
        run.setInvalidCount(1);
        run.setDuplicateCount(1);
        run.setUpsertedCount(17);
        run.setDisabledCount(2);
        run.setMessage("safe summary only");
        run.setCreatedBy(7L);
        run.setCreatedAt(now);

        assertThat(run.getId()).isEqualTo(10L);
        assertThat(run.getSyncType()).isEqualTo("MATERIAL");
        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getStartedAt()).isEqualTo(now);
        assertThat(run.getFinishedAt()).isEqualTo(now.plusMinutes(1));
        assertThat(run.getFetchedCount()).isEqualTo(20);
        assertThat(run.getValidCount()).isEqualTo(18);
        assertThat(run.getInvalidCount()).isEqualTo(1);
        assertThat(run.getDuplicateCount()).isEqualTo(1);
        assertThat(run.getUpsertedCount()).isEqualTo(17);
        assertThat(run.getDisabledCount()).isEqualTo(2);
        assertThat(run.getMessage()).isEqualTo("safe summary only");
        assertThat(run.getCreatedBy()).isEqualTo(7L);
        assertThat(run.getCreatedAt()).isEqualTo(now);
        assertThat(BaseMapper.class).isAssignableFrom(MesMasterDataSyncRunMapper.class);
    }

    @Test
    void processCategoryContinuesToReuseExistingMesFields() throws IOException {
        assertThat(read(PROCESS_CATEGORY_ENTITY))
                .contains(
                        "private String sourceType;",
                        "private String mesExternalId;",
                        "private LocalDateTime mesSyncedAt;")
                .doesNotContain(
                        "private String mesProcessType",
                        "private String mesProcessCode",
                        "private String mesProcessName");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
