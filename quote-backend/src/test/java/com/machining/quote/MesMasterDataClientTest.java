package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMasterDataClientTest {
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");

    @Test
    void clientSkeletonCoversReadOnlyMesProcessAndMaterialApis() throws IOException {
        String doc = read(DESIGN_DOC);

        assertThat(doc)
                .contains(
                        "MesMasterDataClient",
                        "MesProcessTypeListResp",
                        "MesMaterialPageResp",
                        "listProcessTypes()",
                        "pageMaterials(int pageNo, int pageSize)",
                        "/api/v1/mes/openapi/process-type/list",
                        "/api/v1/mes/openapi/material/page");
    }

    @Test
    void clientSkeletonCoversSensitiveDataBoundary() throws IOException {
        assertThat(read(DESIGN_DOC))
                .contains(
                        "不记录 token",
                        "client secret",
                        "完整 MES 响应体",
                        "Client 测试不依赖真实 MES",
                        "错误信息脱敏")
                .doesNotContain("Authorization: Bearer eyJ");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
