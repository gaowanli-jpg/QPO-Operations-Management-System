package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9SyncStage8AcceptanceTest {
    private static final Path DOC_42 = Path.of("..", "docs", "QPO报价状态流转", "42-销售订单U9同步模块重构开发文档.md");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path BACKEND_MAIN = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path FRONTEND_SRC = Path.of("..", "quote-frontend", "src");

    @Test
    void stageEightCompletionRecordCapturesRegressionAndRealU9AcceptanceMatrix() throws IOException {
        String doc = read(DOC_42);

        assertThat(doc)
                .contains(
                        "设计状态：阶段 8 已完成并验收。",
                        "2026-08-11 阶段 8 完成记录",
                        ".\\mvnw.cmd test",
                        "npm test",
                        "npm run build",
                        "真实 U9 验收矩阵已按新的唯一测试 DocNo 执行",
                        "不得复用本文历史测试单号再次创建");
        assertThat(doc)
                .contains(
                        "销售员和部门都为空可以创建",
                        "只传销售员可以创建",
                        "只传销售部门可以创建",
                        "同时传真实销售员和部门可以创建并正确回显",
                        "未税单价 `100`、数量 `1`",
                        "多行订单一次原子创建",
                        "客户项目号、客户采购订单号、内部项目号、交期、内部交期、价格和备注私有字段正确",
                        "不提供交期时仍可创建，QPO 不伪造日期",
                        "U9 自动带出 `PCS`、换算率、币种、站点、供应组织和开立状态",
                        "请求中不存在客户料号、图号、版本、单位、换算率、订单价、税额和金额字段",
                        "已存在同 `DocNo` 的订单不重复创建",
                        "模拟超时后可通过 `DocNo` 查回并修复 QPO 状态",
                        "QPO 正式销售订单和 RFQ 派生销售订单都可继续同步",
                        "不新增用户权限",
                        "操作日志和文件日志不包含完整请求、响应、token、Authorization 或凭据");
    }

    @Test
    void finalSourceContractKeepsMinimalU9PayloadAndSubmitPermissionReuse() throws IOException {
        String assembler = read(BACKEND_MAIN.resolve(Path.of("service", "u9", "U9SalesOrderRequestAssembler.java")));
        String headDto = read(BACKEND_MAIN.resolve(Path.of("dto", "u9", "U9SalesOrderHeadDTO.java")));
        String lineDto = read(BACKEND_MAIN.resolve(Path.of("dto", "u9", "U9SalesOrderLineDTO.java")));
        String shipLineDto = read(BACKEND_MAIN.resolve(Path.of("dto", "u9", "U9SalesOrderShipLineDTO.java")));
        String frontendU9 = read(FRONTEND_SRC.resolve(Path.of("components", "SalesOrderU9Sync.vue")));
        String permissionDoc = read(PERMISSION_DOC);

        assertThat(assembler)
                .contains(
                        "head.setSeller(trimToNull(header.getU9SellerCode()))",
                        "head.setSaleDepartment(trimToNull(header.getU9SaleDepartmentCode()))",
                        "head.setIsPriceIncludeTax(false)",
                        "line.setFinallyPriceTC(item.getUntaxedUnitPrice())",
                        "desc.setPrivateDescSeg1",
                        "desc.setPrivateDescSeg2",
                        "desc.setPrivateDescSeg3",
                        "desc.setPrivateDescSeg4",
                        "desc.setPrivateDescSeg5",
                        "desc.setPrivateDescSeg9",
                        "desc.setPubDescSeg2",
                        "item.getCustomerDeliveryDate() == null ? null",
                        "item.getInternalDeliveryDate() == null ? null");

        assertThat(headDto + lineDto + shipLineDto)
                .contains("Seller", "SaleDepartment", "IsPriceIncludeTax", "FinallyPriceTC", "DescFlexField")
                .doesNotContain(
                        "CustomerItemNo", "CustomerItemName", "ItemVersion", "Project", "Task",
                        "@JsonProperty(\"TU\")", "@JsonProperty(\"PU\")", "TBUToPBURate",
                        "OrderPriceTC", "NetMoneyTC", "TaxMoneyTC", "TotalMoneyTC",
                        "TaxSchedule", "SupplyOrg", "PlanDate");

        assertThat(frontendU9)
                .contains("const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'")
                .doesNotContain("market:u9-sync:assignment", "market:u9-sync:reconcile", "common:u9");
        assertThat(permissionDoc)
                .contains("保存 Seller / SaleDepartment 和核对 U9 结果仍复用 `market:u9-sync:submit`")
                .doesNotContain("| `market:u9-sync:assignment`", "| `market:u9-sync:reconcile`", "| `common:u9`");
    }

    @Test
    void stageEightArtifactsDoNotRecordRealSecretsOrFullPayloads() throws IOException {
        String doc = read(DOC_42);
        String source = String.join("\n",
                read(BACKEND_MAIN.resolve(Path.of("service", "SalesOrderU9SyncService.java"))),
                read(BACKEND_MAIN.resolve(Path.of("service", "u9", "U9SalesOrderClient.java"))),
                read(BACKEND_MAIN.resolve(Path.of("service", "u9", "U9CommonEntityClient.java"))));

        assertThat(doc)
                .doesNotContain("Authorization: Bearer " + "eyJ", "access_token=" + "eyJ", "client_secret=")
                .doesNotContainPattern("(?i)(password|secret|token)\\s*[:=]\\s*[A-Za-z0-9+/=_-]{16,}");
        assertThat(source)
                .contains("sanitize(error.getMessage())", "safeError(result.message())")
                .doesNotContain("Data.Headers", "Authorization: Bearer", "client_secret=");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
