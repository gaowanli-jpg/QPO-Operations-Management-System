package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderChangeStage8GovernanceTest {
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path AUDIT_DOC = Path.of(
            "..", "docs", "QPO日志模块", "01-操作日志模块开发文档.md");
    private static final Path CHANGE_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderChangeController.java");
    private static final Path CHANGE_ITEM_EDIT_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SalesOrderChangeItemEditService.java");
    private static final Path CHANGE_DATA_REPLACEMENT_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SalesOrderChangeItemDataReplacementService.java");
    private static final Path U9_ITEM_EDIT_TEST = Path.of(
            "src", "test", "java", "com", "machining", "quote", "SalesOrderU9ItemEditServiceTest.java");
    private static final Path FRONTEND_WORKFLOW_TEST = Path.of(
            "..", "quote-frontend", "src", "sales-order-change.workflow.test.js");

    @Test
    void permissionDocumentClosesStage8WithoutAddingCommonPermissions() throws Exception {
        String doc = read(PERMISSION_DOC);

        assertThat(doc)
                .contains(
                        "销售变更阶段 8 收口",
                        "行内保存和表格替换确认均使用 `change:sales-order:update`",
                        "不新增 `common:*` 权限",
                        "销售变更不调用 U9 / MES / WMS",
                        "保存后 `u9_sync_status` 继续保持 `已同步`",
                        "U9 同步前零件行字段编辑仍由 `market:u9-sync:submit` 控制")
                .doesNotContain("change:sales-order:data-replacement", "common:sales-order-change");
    }

    @Test
    void auditDocumentClosesStage8WithSafeSummaryBoundary() throws Exception {
        String doc = read(AUDIT_DOC);

        assertThat(doc)
                .contains(
                        "销售变更阶段 8 收口",
                        "只记录低噪声摘要",
                        "不得记录 Excel 原文",
                        "完整解析行",
                        "完整价格明细",
                        "图纸 URL",
                        "完整 U9 请求",
                        "完整 U9 响应",
                        "认证信息",
                        "销售变更不触发 U9 / MES / WMS",
                        "`u9_sync_status = 已同步` 保存后保持不变")
                .doesNotContain("Excel 原文示例", "Authorization header 示例");
    }

    @Test
    void salesOrderChangeRuntimeDoesNotDependOnExternalIntegrationClients() throws Exception {
        String combinedSource = read(CHANGE_CONTROLLER) + read(CHANGE_ITEM_EDIT_SERVICE) + read(CHANGE_DATA_REPLACEMENT_SERVICE);

        assertThat(combinedSource)
                .doesNotContain(
                        "U9SalesOrderClient",
                        "U9SalesOrderSyncService",
                        "WmsReservation",
                        "MesMaterial",
                        "MesProduction",
                        "DataCenterEndpointRegistry",
                        "RestTemplate",
                        "WebClient");
        assertThat(combinedSource)
                .contains("STATUS_SYNCED = \"已同步\"")
                .contains("assertSynced(header)")
                .contains("SALES_ORDER_CHANGE_ITEM_UPDATE")
                .contains("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")
                .doesNotContain("set(\"u9_sync_status\"");
    }

    @Test
    void regressionTestsCoverSalesChangeFrontendAndU9PreSyncEditing() throws Exception {
        String frontendTest = read(FRONTEND_WORKFLOW_TEST);
        String u9EditTest = read(U9_ITEM_EDIT_TEST);

        assertThat(frontendTest)
                .contains(
                        "supports stage 7 Excel data replacement with preview and required reason",
                        "变更原因不能为空",
                        "表格数据替换",
                        "saveSalesOrderChangeItems",
                        "导入替换图纸")
                .contains("not.toContain('导入替换图纸')")
                .contains("not.toContain('syncSalesOrderU9')");
        assertThat(u9EditTest)
                .contains(
                        "saveItemsLocksHeaderAndLineUpdatesAllowedFieldsAndRecalculatesDerivedValuesForU9Preview",
                        "market:u9-sync:submit",
                        "new U9SalesOrderRequestAssembler().assemble",
                        "saveItemsRejectsLockedU9StatusesBeforeLineUpdate");
    }

    private static String read(Path path) throws Exception {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
