package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9ImportStage5Test {
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
    private static final Path DEVELOPMENT_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "11-销售订单导入与U9同步开发文档.md");

    @Test
    void permissionDocsCloseSalesOrderU9ImportBoundary() throws IOException {
        String permissionList = read(PERMISSION_LIST);
        String adminGuide = read(ADMIN_GUIDE);

        assertThat(permissionList)
                .contains(
                        "销售订单 U9 同步已从新单导入拆为独立左侧模块",
                        "U9 同步独立模块口径",
                        "`market:rfq:import-sales-order`",
                        "挂在 `market:rfq:view` 下",
                        "不能复用 `market:rfq:import`",
                        "`market:u9-sync:view`",
                        "`market:u9-sync:submit`",
                        "`market:u9-sync:*`",
                        "`sales_order_header` / `sales_order_item`",
                        "`material_no_status = 已确认`"
                )
                .doesNotContain("`market:sales-order:view`")
                .doesNotContain("ItemCode` 使用 `order_delivery_ticket.material_no`");
        assertThat(adminGuide)
                .contains(
                        "销售订单 U9 同步独立模块",
                        "`market:rfq:import-sales-order`",
                        "`market:u9-sync:view`",
                        "`market:u9-sync:submit`",
                        "`sales_order_header` / `sales_order_item`",
                        "`sales_order_item.material_no_status = 已确认`"
                );
    }

    @Test
    void loggingDocsCloseSalesOrderU9ImportAuditAndFileLogRules() throws IOException {
        String operationsGuide = read(LOG_OPS_GUIDE);
        String fileLogGuide = read(FILE_LOG_GUIDE);

        assertThat(operationsGuide)
                .contains(
                        "销售订单 U9 同步审计口径",
                        "SALES_ORDER_IMPORT_U9",
                        "targetType` 为 `sales_order_import_batch`",
                        "batchNo",
                        "successOrderCount",
                        "failedOrderCount",
                        "不记录完整 Excel 内容",
                        "U9 token",
                        "clientsecret",
                        "完整 U9 请求体",
                        "只同步 U9，不创建 QPO 订单、不进入议价提单"
                );
        assertThat(fileLogGuide)
                .contains(
                        "销售订单 U9 同步",
                        "Sales order import started batchNo={} sourceFileName={}",
                        "Sales order import parsed batchNo={} orderCount={} lineCount={}",
                        "Sales order U9 sync started batchNo={} orderCount={}",
                        "Sales order U9 sync completed batchNo={} successOrderCount={} failedOrderCount={} durationMs={}",
                        "Sales order U9 sync failed batchNo={} durationMs={}",
                        "不记录完整 Excel 内容、U9 token、clientsecret、完整 U9 请求体或完整 U9 响应大对象"
                );
    }

    @Test
    void salesOrderDiscussionAndDevelopmentDocsStateStageOneDoesNotEnterRelease() throws IOException {
        String discussionDoc = read(DISCUSSION_DOC);
        String developmentDoc = read(DEVELOPMENT_DOC);

        assertThat(discussionDoc)
                .contains(
                        "### 12.3 阶段 5 收口结论",
                        "阶段 1 到阶段 5 只完成销售订单导入并同步 U9",
                        "不创建 QPO `order_rfq`",
                        "不创建 QPO `item_quote_version`",
                        "不把订单或零件置为 `已报价`",
                        "不进入 `议价提单`",
                        "第二步再讨论销售订单进入 QPO 提单生产链路"
                );
        assertThat(developmentDoc)
                .contains(
                        "阶段 5 已落地",
                        "权限、日志、运维和测试收口",
                        "`mvnw.cmd test`",
                        "`npm test`",
                        "`npm run build`",
                        "本阶段只同步 U9，不创建 QPO 订单或零件，不进入议价提单"
                );
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
