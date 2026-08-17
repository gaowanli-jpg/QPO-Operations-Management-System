package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9ItemGovernanceStage6Test {
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path AUDIT_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "01-操作日志模块开发文档.md");
    private static final Path ITEM_EDIT_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SalesOrderU9ItemEditService.java");
    private static final Path ITEM_REPLACEMENT_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SalesOrderU9ItemDataReplacementService.java");
    private static final Path FRONTEND_U9_SYNC = Path.of(
            "..", "quote-frontend", "src", "components", "SalesOrderU9Sync.vue");
    private static final Path FRONTEND_REPLACEMENT_DIALOG = Path.of(
            "..", "quote-frontend", "src", "components", "u9-sync", "SalesOrderU9ItemDataReplacementDialog.vue");

    @Test
    void permissionDocumentationKeepsU9ItemMaintenanceUnderSubmitPermissionOnly() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(permissionDoc)
                .contains(
                        "保存 Seller / SaleDepartment、刷新 U9 选项、同步 U9、核对 U9 结果、U9 同步前零件行字段保存和表格数据替换均复用 `market:u9-sync:submit`",
                        "不新增 `market:u9-sync:item-update`、`market:u9-sync:data-replacement` 或可分配 `common:*` 权限")
                .doesNotContain(
                        "| `market:u9-sync:item-update`",
                        "| `market:u9-sync:data-replacement`",
                        "| `market:u9-sync:replace-drawing`",
                        "| `common:u9-sync`");
    }

    @Test
    void auditDocumentationDefinesSafeSummaryForU9ItemUpdateAndReplacement() throws IOException {
        String auditDoc = read(AUDIT_LOG_DOC);

        assertThat(auditDoc)
                .contains(
                        "SALES_ORDER_U9_ITEM_UPDATE",
                        "SALES_ORDER_U9_ITEM_DATA_REPLACEMENT",
                        "sales_order_header",
                        "salesOrderId",
                        "salesOrderNo",
                        "updatedCount",
                        "changedFields",
                        "errorCount",
                        "durationMs",
                        "不得记录 Excel 原文、完整解析行、完整 U9 请求、完整 U9 响应、token、Authorization header、Cookie、图纸 URL 或完整价格明细");
    }

    @Test
    void auditActionsAreMappedToMarketLabelsAndSafeSourceSummaries() throws IOException {
        assertThat(AuditService.inferModule("SALES_ORDER_U9_ITEM_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("SALES_ORDER_U9_ITEM_DATA_REPLACEMENT")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_ITEM_UPDATE")).contains("U9零件行字段");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_ITEM_DATA_REPLACEMENT")).contains("U9表格数据替换");

        String editAudit = auditSuccessSection(read(ITEM_EDIT_SERVICE));
        String replacementAudit = auditSuccessSection(read(ITEM_REPLACEMENT_SERVICE));
        assertThat(editAudit + "\n" + replacementAudit)
                .contains(
                        "summary.put(\"salesOrderId\"",
                        "summary.put(\"salesOrderNo\"",
                        "summary.put(\"updatedCount\"",
                        "safeSummary.put(\"errorCount\"")
                .doesNotContain(
                        "beforeValue",
                        "afterValue",
                        "excelInput",
                        "excelFile",
                        "Authorization",
                        "Cookie",
                        "token",
                        "drawingUrl",
                        "drawingUrls",
                        "U9SalesOrderHeadDTO",
                        "requestBody",
                        "responseBody");
    }

    @Test
    void frontendKeepsReplacementPermissionDisabledStateAndNoDrawingEntry() throws IOException {
        String page = read(FRONTEND_U9_SYNC);
        String dialog = read(FRONTEND_REPLACEMENT_DIALOG);

        assertThat(page)
                .contains(
                        "表格数据替换",
                        "v-permission=\"U9_SYNC_SUBMIT_PERMISSION\"",
                        "const itemDataReplacementDisabledReason = computed(() =>",
                        "!hasItemEditPermission.value",
                        "['已同步', '同步中', '结果待确认'].includes(currentU9Status.value)",
                        "itemDataReplacementBusy",
                        "handleItemDataReplacementConfirmed")
                .doesNotContain("导入替换图纸");
        assertThat(dialog)
                .contains(
                        "accept=\".xlsx,.xls\"",
                        "summary.errorCount",
                        "canConfirmPreview",
                        "确认替换")
                .doesNotContain(
                        "导入替换图纸",
                        "replaceReleaseDrawings",
                        "acceptedQty",
                        "通过数量",
                        "QUANTITY_ONLY");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String auditSuccessSection(String source) {
        int start = source.indexOf("private void auditSuccess");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = source.indexOf("private void appendToken", start);
        if (end < 0) {
            end = source.indexOf("private boolean sameDecimal", start);
        }
        assertThat(end).isGreaterThan(start);
        return source.substring(start, end);
    }
}
