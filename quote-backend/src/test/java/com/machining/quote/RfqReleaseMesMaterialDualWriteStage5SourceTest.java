package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReleaseMesMaterialDualWriteStage5SourceTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void allMesSuccessPathsUseTheSharedDualWriteServiceWithoutSingleTableFallbacks() throws IOException {
        String syncService = read(MAIN.resolve(Path.of("service", "mes", "MesMaterialSyncService.java")));
        String dedupService = read(MAIN.resolve(Path.of("service", "mes", "MesMaterialDedupService.java")));

        assertThat(syncService)
                .contains("sourceItemMaterialNoSyncService.syncConfirmedMaterialNo")
                .doesNotContain(
                        "assertSalesOrderItemMaterialWritable",
                        "syncSalesOrderItemMaterialNo",
                        "MATERIAL_SOURCE_MES_TICKET");
        assertThat(dedupService)
                .contains("sourceItemMaterialNoSyncService.syncConfirmedMaterialNo")
                .doesNotContain(
                        "syncSalesOrderItemMaterialNo",
                        "MATERIAL_SOURCE_MES_TICKET");
    }

    @Test
    void mesResultAndAuditSummariesExposeDualWriteCounts() throws IOException {
        String syncService = read(MAIN.resolve(Path.of("service", "mes", "MesMaterialSyncService.java")));
        String dedupService = read(MAIN.resolve(Path.of("service", "mes", "MesMaterialDedupService.java")));

        assertThat(syncService).contains(
                "sourceSyncTargetCount",
                "sourceSyncUpdatedCount",
                "sourceSyncIdempotentCount",
                "rfqSourceUpdatedCount",
                "salesOrderSourceUpdatedCount");
        assertThat(dedupService).contains(
                "sourceSyncTargetCount",
                "sourceSyncUpdatedCount",
                "sourceSyncIdempotentCount",
                "rfqSourceUpdatedCount",
                "salesOrderSourceUpdatedCount");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
