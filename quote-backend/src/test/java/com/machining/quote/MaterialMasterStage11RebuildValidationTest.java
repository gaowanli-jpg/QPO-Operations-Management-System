package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialMasterStage11RebuildValidationTest {
    private static final Path STATUS_DOC = Path.of("..", "docs", "QPO报价状态流转",
            "39-材料主数据编码与类型全链路重构开发文档.md");

    @Test
    void stage11RecordsCleanRebuildAndMaterialPricingEvidence() throws IOException {
        String doc = Files.readString(STATUS_DOC, StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertThat(doc)
                .contains("阶段 1-12 已完成")
                .contains("阶段 11 完成记录")
                .contains("阶段 12 完成记录")
                .contains("qpo_public_before_material_stage11_20260809_141519.sql")
                .contains("qpo_public_stage11_stale_image_rebuild_20260809_142220.sql")
                .contains("V1 / V2")
                .contains("/api/health", "/material-process")
                .contains("MAT-TEST-001", "RAW_MATERIAL")
                .contains("/api/engineer/submit-quote")
                .contains("material_data.materialCode", "material_data.materialTypeCode")
                .contains("不删除上传文件", "MinIO")
                .doesNotContain("docker compose down -v")
                .doesNotContain("Remove-Item -Recurse")
                .doesNotContain("DROP DATABASE");
    }
}
