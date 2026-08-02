package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialDedupStage6DocumentationTest {
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path MES_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "06-提供给MES的物料号接口对接说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");

    @Test
    void mesDocsCloseStage6DedupCallbackAndRegressionContract() throws IOException {
        assertThat(read(MES_SYNC_DOC))
                .contains(
                        "阶段 6 收口说明",
                        "同一 `图号 + 版本` 同一时间只保留一个活跃 MES 主请求",
                        "同批量相同 `图号 + 版本` 只打 MES 一次",
                        "跨批次待回调时相同 `图号 + 版本` 不再打 MES",
                        "跨批次已成功且只有一个成功物料号时直接复用物料号",
                        "跨批次存在多个不同成功物料号时不自动复用，按正常请求调用 MES",
                        "MES 仍只回调主 `mesRequestId`",
                        "销售订单 U9 同步前能看到等待行物料号已确认");

        assertThat(read(MES_INTERFACE_DOC))
                .contains(
                        "同图号版本去重",
                        "MES 仍只回调主 `mesRequestId`",
                        "关联等待行",
                        "QPO fan-out",
                        "`dedupFollowerCount`",
                        "`reusedMaterialCount`",
                        "`submittedToMesCount`")
                .doesNotContain("MES 需要分别回调每个相同图号版本零件");
    }

    @Test
    void logAndPermissionDocsFreezeDedupOperationsWithoutNewUserPermission() throws IOException {
        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "DELIVERY_MES_MATERIAL_DEDUP_REUSE",
                        "DELIVERY_MES_MATERIAL_DEDUP_FOLLOW",
                        "DELIVERY_MES_MATERIAL_DEDUP_FANOUT",
                        "dedupFollowerCount",
                        "reusedMaterialCount",
                        "submittedToMesCount",
                        "MES 同图号版本去重不新增用户权限，继续使用 `market:delivered-item:mes-sync`");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "MES material dedup master created",
                        "MES material dedup follow",
                        "MES material dedup reuse",
                        "MES material dedup fanout completed",
                        "MES material dedup fanout failed",
                        "dedupKey",
                        "不记录完整 MES 请求 / 响应 / 回调 JSON");

        assertThat(read(PERMISSION_DOC))
                .contains(
                        "MES 同图号版本去重不新增用户权限",
                        "`market:delivered-item:mes-sync`")
                .doesNotContain(
                        "market:delivered-item:mes-dedup",
                        "market:delivered-item:dedup");
        assertThat(read(PERMISSION_SERVICE))
                .doesNotContain(
                        "market:delivered-item:mes-dedup",
                        "market:delivered-item:dedup");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
