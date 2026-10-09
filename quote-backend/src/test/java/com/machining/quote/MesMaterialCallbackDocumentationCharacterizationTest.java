package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MesMaterialCallbackDocumentationCharacterizationTest {
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path MES_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "06-提供给MES的物料号接口对接说明.md");
    private static final Path MES_CALLBACK_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "08-MES物料号回调接口对接说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");

    @Test
    void mesIntegrationDocsDescribeImmediateAndCallbackMaterialNumberContract() throws IOException {
        assertThat(read(MES_SYNC_DOC))
                .contains(
                        "即时返回物料号",
                        "受理后回调物料号",
                        "`mesRequestId`",
                        "`CALLBACK_PENDING`",
                        "POST /api/integrations/mes/material-callback",
                        "待回调响应下 `system_task` 按成功完成",
                        "不新增用户权限，继续使用 `market:delivered-item:mes-sync`")
                .doesNotContain("HTTP 2xx 但 `materialNo` 为空，判定失败。");

        assertThat(read(MES_INTERFACE_DOC))
                .contains(
                        "MES 可以即时返回物料号，也可以返回已受理并后续回调",
                        "`mesRequestId`",
                        "callbackRequired",
                        "CALLBACK_PENDING",
                        "POST /api/integrations/mes/material-callback",
                        "Authorization: Bearer {QPO_OPEN_API_TOKEN}",
                        "旧同步响应口径已废弃")
                .doesNotContain("不需要 MES 回调");

        assertThat(read(MES_CALLBACK_DOC))
                .contains(
                        "批量 envelope 契约",
                        "即使只有 1 个零件，也必须放在 `items` 数组中回调",
                        "`items[].productCode`",
                        "缺少 `items`",
                        "`mes_sync_status=SUCCESS` 且 `material_no` 非空",
                        "不更新任何业务记录",
                        "按成功响应计入 `duplicateCount`")
                .doesNotContain("扁平 JSON 契约");
    }

    @Test
    void mesCallbackDocsKeepHttp200ForBusinessErrors() throws IOException {
        assertThat(read(MES_CALLBACK_DOC))
                .contains("HTTP `200`", "code=404", "code=409")
                .doesNotContain("HTTP `404`")
                .doesNotContain("HTTP `409`");
    }

    @Test
    void mesCallbackDocsExposeOnlyMsgInQpoResponseBody() throws IOException {
        assertThat(read(MES_CALLBACK_DOC))
                .contains("\"msg\": \"accepted\"")
                .doesNotContain("\"message\": \"accepted\"")
                .doesNotContain("`msg/message`")
                .doesNotContain("`message` / `msg`");
    }

    @Test
    void logDocsDescribeAcceptedCallbackAndConflictEventsWithoutSensitivePayloads() throws IOException {
        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED",
                        "DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS",
                        "DELIVERY_MES_MATERIAL_CALLBACK_FAILED",
                        "DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT",
                        "`mesRequestId`",
                        "callbackPendingCount",
                        "不得记录 MES token、QPO 入站开放接口统一 token、Authorization header、完整请求体或完整回调体");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "MES material sync accepted",
                        "MES material callback received",
                        "MES material callback completed",
                        "MES material callback rejected",
                        "MES material callback conflict",
                        "ticketId",
                        "mesRequestId",
                        "不得记录 MES token、QPO 入站开放接口统一 token、token hash、Authorization header、完整请求 JSON、完整响应 JSON 或完整回调 JSON");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
