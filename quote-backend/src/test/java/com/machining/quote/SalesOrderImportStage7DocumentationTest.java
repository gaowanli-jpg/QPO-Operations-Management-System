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
                .doesNotContain("销售订单异常治理")
                .doesNotContain("排查历史订单或自动补全异常")
                .doesNotContain("salesMatch");
    }

    @Test
    void stageSevenDocsCloseSalesOrderAutoImportFlow() throws IOException {
        String salesOrderDoc = read(SALES_ORDER_DOC);
        String salesMatchDoc = read(SALES_MATCH_DOC);
        String materialRuleDoc = read(MATERIAL_RULE_DOC);
        String orderWorkbenchDoc = read(ORDER_WORKBENCH_DOC);
        String u9Doc = read(U9_DOC);

        assertThat(salesOrderDoc)
                .contains("销售订单导入成功后仍自动补全历史生产快照并自动生成已提零件")
                .contains("原手工异常治理入口、预览和确认 API 已删除")
                .contains("销售订单不再进入议价提单页面手动生成提单")
                .contains("价格补齐前不可同步 U9")
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match")
                .doesNotContain("销售订单导入后状态为 `已报价`")
                .doesNotContain("销售订单可被议价提单选择");
        assertThat(salesMatchDoc)
                .contains("手工模块已删除")
                .contains("销售订单导入自动快照补全继续保留为导入事务内部能力")
                .contains("不再提供独立左侧入口")
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match");
        assertThat(materialRuleDoc)
                .contains("销售订单导入自动快照补全")
                .contains("不得覆盖")
                .contains("不再存在材料差异预览");
        assertThat(orderWorkbenchDoc)
                .contains("原订单级销售匹配工作台已删除")
                .contains("不再提供用户手工选择订单、预览和确认的工作台");
        assertThat(u9Doc)
                .contains("价格补齐前不可同步 U9")
                .contains("preview 返回不可同步")
                .contains("不调用数据中心或 U9");
    }

    @Test
    void salesOrderImportDocDescribesOrderDefaultMatrixAndLineOverrides() throws IOException {
        String salesOrderDoc = read(SALES_ORDER_DOC);

        assertThat(salesOrderDoc)
                .contains("页面额外提供客户选择、单据类型编码和订单默认信息矩阵")
                .contains("订单头字段以下单页面填写的订单默认信息为准")
                .contains("Excel 中 `下单日期 / 客户订单号 / 客户项目号 / 客户下单抬头 / 订单类型 / 内部项目号` 为可选的零件级覆盖列")
                .contains("行内为空或列不存在时继承页面订单默认信息")
                .contains("一次导入只创建一个 `sales_order_header`")
                .contains("自动提单优先使用 `sales_order_item` 行级有效字段，空值再回退 `sales_order_header`");
    }

    @Test
    void permissionsAndLogsDocumentNoNewPermissionAndImportChainSummary() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String logDocs = read(LOG_OPS_DOC) + "\n" + read(FILE_LOG_DOC);

        assertThat(permissionDoc)
                .contains("销售订单手工异常治理模块退役")
                .contains("销售订单导入自动快照补全")
                .contains("`market:rfq:import-sales-order`")
                .doesNotContain("market:sales-match")
                .doesNotContain("/sales-match");
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
