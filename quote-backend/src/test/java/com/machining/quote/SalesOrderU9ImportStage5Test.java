package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9ImportStage5Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path PERMISSION_LIST = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_GUIDE = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_GUIDE = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_GUIDE = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path DISCUSSION_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "10-销售订单直提流程方案讨论文档.md");
    private static final Path INDEPENDENT_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "14-销售订单独立主表开发文档.md");
    private static final Path U9_SYNC_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "16-销售订单U9同步模块开发文档.md");

    @Test
    void permissionDocsKeepImportAndU9SyncAsSeparateResponsibilities() throws IOException {
        String permissionList = read(PERMISSION_LIST);
        String adminGuide = read(ADMIN_GUIDE);

        assertThat(permissionList)
                .contains(
                        "销售订单 U9 同步已从新单导入拆为独立左侧模块",
                        "U9 同步独立模块口径",
                        "销售订单导入、模板下载仍沿用 `market:rfq:import-sales-order`",
                        "`market:rfq:import-sales-order`",
                        "`market:u9-sync:view`",
                        "`market:u9-sync:submit`",
                        "`market:u9-sync:*`",
                        "`sales_order_header` / `sales_order_item`",
                        "`material_no_status = 已确认`",
                        "不得实时从 `order_delivery_ticket.material_no` 组装 U9 请求"
                )
                .doesNotContain("`market:sales-order:view`")
                .doesNotContain("ItemCode` 使用 `order_delivery_ticket.material_no`");
        assertThat(adminGuide)
                .contains(
                        "销售订单 U9 同步独立模块",
                        "`market:rfq:import-sales-order`",
                        "`market:u9-sync:view`",
                        "`market:u9-sync:submit`",
                        "后续 U9 同步在独立 `U9同步` 模块内授权和执行",
                        "`sales_order_header` / `sales_order_item`",
                        "`sales_order_item.material_no_status = 已确认`"
                );
    }

    @Test
    void loggingDocsDescribeIndependentU9SyncInsteadOfNewOrderImportSync() throws IOException {
        String operationsGuide = read(LOG_OPS_GUIDE);
        String fileLogGuide = read(FILE_LOG_GUIDE);

        assertThat(operationsGuide)
                .contains(
                        "销售订单 U9 同步审计口径",
                        "SALES_ORDER_U9_SYNC",
                        "targetType` 为 `sales_order_header`",
                        "销售订单独立主表阶段 8 回归收口确认",
                        "SALES_ORDER_IMPORT_QPO",
                        "successOrderCount",
                        "failedOrderCount",
                        "不记录完整 Excel 内容",
                        "U9 token",
                        "完整 U9 请求体",
                        "销售订单数据不进入 `order_rfq` / `item_quote_version`"
                )
                .doesNotContain("U9 同步属于市场新单导入下的主流程写操作");
        assertThat(fileLogGuide)
                .contains(
                        "销售订单 U9 同步",
                        "Sales order U9 sync started orderCount={}",
                        "Sales order U9 sync completed orderCount={} successOrderCount={} failedOrderCount={} durationMs={}",
                        "Sales order U9 sync failed orderCount={} reason={}",
                        "不记录完整 Excel 内容、U9 token、clientsecret、完整 U9 请求体或完整 U9 响应大对象"
                )
                .doesNotContain("销售订单 U9 同步属于市场新单导入下的主流程写操作");
    }

    @Test
    void salesOrderDocsStateImportThenSalesMatchThenReleaseThenIndependentU9Sync() throws IOException {
        String discussionDoc = read(DISCUSSION_DOC);
        String independentDoc = read(INDEPENDENT_DOC);
        String u9SyncDoc = read(U9_SYNC_DOC);

        assertThat(discussionDoc)
                .contains(
                        "U9 同步改为后置动作",
                        "销售订单提单后获取 MES 物料号",
                        "U9 同步从独立销售订单和正式提单物料号组装请求"
                );
        assertThat(independentDoc)
                .contains(
                        "导入链路只负责写独立表、绑定图纸并返回导入结果",
                        "U9 同步由独立 **U9同步** 模块执行",
                        "U9 同步从 `sales_order_header` / `sales_order_item` 组装请求",
                        "`ItemCode` 使用 `sales_order_item.material_no`",
                        "`material_no_status = 已确认`",
                        "不再从 `order_delivery_ticket` 实时组装 U9 请求"
                )
                .doesNotContain("U9 同步从 `sales_order_header` / `sales_order_item` / `order_delivery_ticket` 组装请求");
        assertThat(u9SyncDoc)
                .contains(
                        "阶段 5：入口迁移、文档与回归",
                        "销售订单导入职责清晰：写独立表、绑定图纸、返回导入结果",
                        "U9同步职责清晰：选择销售订单、预览提单物料号、整单同步 U9",
                        "`mvnw.cmd test`、`npm test`、`npm run build` 通过"
                );
    }

    @Test
    void legacySalesOrderU9EndpointIsCompatibilityOnlyAndRemovedFromImportController() throws IOException {
        String importController = read(MAIN_JAVA.resolve(Path.of("controller", "SalesOrderQpoImportController.java")));
        String compatController = read(MAIN_JAVA.resolve(Path.of("controller", "SalesOrderU9SyncCompatibilityController.java")));
        String u9Controller = read(MAIN_JAVA.resolve(Path.of("controller", "SalesOrderU9SyncController.java")));

        assertThat(importController)
                .contains("SalesOrderQpoImportService")
                .contains("@PostMapping(\"/import-qpo\")")
                .doesNotContain("SalesOrderU9SyncService")
                .doesNotContain("SalesOrderU9SyncReq")
                .doesNotContain("SalesOrderU9SyncResp")
                .doesNotContain("@PostMapping(\"/u9-sync\")")
                .doesNotContain("syncU9(");
        assertThat(compatController)
                .contains(
                        "@Deprecated",
                        "@RequestMapping(\"/api/market/sales-orders\")",
                        "@PostMapping(\"/u9-sync\")",
                        "@RequirePermission(\"market:u9-sync:submit\")",
                        "syncSalesOrders(req == null ? null : req.getSalesOrderIds())"
                )
                .doesNotContain("@RequirePermission(\"market:rfq:import-sales-order\")");
        assertThat(u9Controller)
                .contains(
                        "@RequestMapping(\"/api/market/u9-sync\")",
                        "@PostMapping(\"/orders/{salesOrderId}/sync\")",
                        "@PostMapping(\"/sync\")",
                        "@RequirePermission(\"market:u9-sync:submit\")"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
