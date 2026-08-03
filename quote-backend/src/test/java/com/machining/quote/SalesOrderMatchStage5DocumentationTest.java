package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderMatchStage5DocumentationTest {
    private static final Path SALES_MATCH_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "15-销售匹配模块开发文档.md");
    private static final Path MATERIAL_RULE_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "26-销售匹配材料字段口径重构开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");

    @Test
    void salesMatchDocIsClosedAsOrderLevelWorkbench() throws IOException {
        assertThat(read(SALES_MATCH_DOC))
                .contains(
                        "销售匹配已收口为订单级工作台",
                        "选择销售订单 -> 整单预览 -> 补全生产快照",
                        "只允许选择 `已报价` 销售订单",
                        "不再提供页面内销售订单列表、零件分页列表或零件级勾选",
                        "补全执行只传销售订单 ID，不传零件选择范围")
                .doesNotContain(
                        "页面支持按客户、销售订单号、客户订单号、内部项目号、匹配状态和销售订单状态筛选",
                        "第一版不做字段级逐项勾选",
                        "勾选一个或多个销售订单",
                        "用户勾选要补全的销售订单零件");
    }

    @Test
    void materialRuleDocMentionsOrderLevelShapeAndNoOverwriteChoices() throws IOException {
        assertThat(read(MATERIAL_RULE_DOC))
                .contains(
                        "页面形态已进一步收口为订单级工作台",
                        "选择销售订单 -> 整单预览 -> 补全生产快照",
                        "不再提供零件级勾选",
                        "不再提供任何覆盖字段确认",
                        "补全生产快照只补空缺信息")
                .doesNotContain(
                        "用户选择销售订单或零件后，点击匹配补全");
    }

    @Test
    void permissionDocStatesPhase5ReusesExistingSalesMatchPermissions() throws IOException {
        assertThat(read(PERMISSION_DOC))
                .contains(
                        "销售匹配订单级工作台阶段 5 不新增权限",
                        "继续复用 `market:sales-match:*`",
                        "`market:sales-match:view` 控制页面入口和订单级预览",
                        "`market:sales-match:order-select` 控制共享销售订单选择弹窗",
                        "`market:sales-match:apply` 控制整单补全生产快照")
                .doesNotContain(
                        "market:sales-match:item-select",
                        "market:sales-match:overwrite");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
