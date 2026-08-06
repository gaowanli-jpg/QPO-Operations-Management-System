package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderImportStage7DocumentationTest {
    private static final Path DASHBOARD_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DashboardOverviewService.java");
    private static final Path SALES_ORDER_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "14-销售订单独立主表开发文档.md");
    private static final Path SALES_MATCH_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "15-销售匹配模块开发文档.md");
    private static final Path MATERIAL_RULE_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "26-销售匹配材料字段口径重构开发文档.md");
    private static final Path ORDER_WORKBENCH_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "27-销售匹配订单级工作台重构开发文档.md");
    private static final Path U9_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "16-销售订单U9同步模块开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path MES_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path WMS_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path DATA_CENTER_U9_DOC = Path.of(
            "..", "docs", "QPO系统配置", "05-数据中心统一对接开发文档.md");

    @Test
    void dashboardNoLongerTreatsSalesMatchAsNormalTodo() throws IOException {
        String service = read(DASHBOARD_SERVICE);

        assertThat(service)
                .doesNotContain("待补全销售订单")
                .doesNotContain("按销售订单补全真实材料和生产快照")
                .contains("销售订单异常治理")
                .contains("排查历史订单或自动补全异常");
    }

    @Test
    void stageSevenDocsCloseSalesOrderAutoImportFlow() throws IOException {
        String salesOrderDoc = read(SALES_ORDER_DOC);
        String salesMatchDoc = read(SALES_MATCH_DOC);
        String materialRuleDoc = read(MATERIAL_RULE_DOC);
        String orderWorkbenchDoc = read(ORDER_WORKBENCH_DOC);
        String u9Doc = read(U9_DOC);

        assertThat(salesOrderDoc)
                .contains("销售订单导入成功后自动补全历史生产快照并自动生成已提零件")
                .contains("异常治理只作为历史销售订单或自动补全异常的兜底工具")
                .contains("销售订单不再进入议价提单页面手动生成提单")
                .contains("价格补齐前不可同步 U9")
                .doesNotContain("销售订单导入后状态为 `已报价`")
                .doesNotContain("销售订单可被议价提单选择");
        assertThat(salesMatchDoc)
                .contains("异常治理")
                .contains("不作为新销售订单日常流程入口")
                .contains("导入事务已经自动执行补全")
                .contains("权限 code 继续保留 `market:sales-match:*`");
        assertThat(materialRuleDoc)
                .contains("导入自动补全沿用只补空缺生产快照")
                .contains("不提供人工覆盖字段确认")
                .contains("异常治理");
        assertThat(orderWorkbenchDoc)
                .contains("订单级工作台已降级为异常治理兜底页面")
                .contains("新销售订单导入后不需要手动进入该页面");
        assertThat(u9Doc)
                .contains("价格补齐前不可同步 U9")
                .contains("preview 返回不可同步")
                .contains("不调用数据中心或 U9");
    }

    @Test
    void permissionsAndLogsDocumentNoNewPermissionAndImportChainSummary() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String logDocs = read(LOG_OPS_DOC) + "\n" + read(FILE_LOG_DOC);

        assertThat(permissionDoc)
                .contains("阶段 7 不新增权限")
                .contains("自动补全和自动提单由 `market:rfq:import-sales-order` 覆盖")
                .contains("异常治理继续复用 `market:sales-match:*`");
        assertThat(logDocs)
                .contains("autoMatch=true")
                .contains("autoRelease=true")
                .contains("autoReleasedTicketCount")
                .contains("missingPriceCount")
                .contains("自动补全和自动提单归入销售订单导入链路摘要");
    }

    @Test
    void integrationDocsDescribeSalesOrdersEnteringDeliveredItemsDirectlyAfterImport() throws IOException {
        String integrationDocs = read(MES_DOC) + "\n" + read(WMS_DOC) + "\n" + read(DATA_CENTER_U9_DOC);

        assertThat(integrationDocs)
                .contains("销售订单来源提单行由销售订单导入自动生成")
                .contains("不再要求市场用户手动销售匹配或手动议价提单")
                .contains("销售订单导入后直接进入已提零件需求池")
                .contains("价格补齐前不可同步 U9");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
