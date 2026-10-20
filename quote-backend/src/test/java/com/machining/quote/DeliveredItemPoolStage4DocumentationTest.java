package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveredItemPoolStage4DocumentationTest {
    private static final Path DELIVERED_ITEM_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "02-基础数据模型与已提零件模块开发文档.md");
    private static final Path WMS_LOCKED_ITEM_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");

    @Test
    void deliveredItemDocDefinesDefaultActivePoolWithoutMovingFacts() throws IOException {
        assertThat(read(DELIVERED_ITEM_DOC))
                .contains(
                        "已提零件默认是未完成池",
                        "`flowScope` 为空时按 `ACTIVE` 处理",
                        "`order_delivery_ticket` 继续作为全量事实主表",
                        "完成流程行不删除、不搬表、不复制到历史表",
                        "完成锁库后的结果在锁库零件模块查看",
                        "默认列表仍由后端分页和索引过滤");
    }

    @Test
    void wmsDocDefinesLockedItemResultPoolAndTicketTraceability() throws IOException {
        assertThat(read(WMS_LOCKED_ITEM_DOC))
                .contains(
                        "WMS 成功后结果进入锁库零件模块",
                        "`order_delivery_ticket_allocation` 是锁库结果池",
                        "原始 ticket 不删除",
                        "通过 `ticketId` 反查原始 `order_delivery_ticket`",
                        "完成行从已提零件默认列表隐藏");
    }

    @Test
    void permissionDocStatesPoolRefactorAddsNoPermissions() throws IOException {
        assertThat(read(PERMISSION_DOC))
                .contains(
                        "已提零件未完成池与锁库结果池重构不新增权限",
                        "`market:delivered-item:view`",
                        "`market:delivered-item:wms-allocate`",
                        "`market:locked-item:view`")
                .doesNotContain(
                        "market:delivered-item:completed-view",
                        "market:locked-item:trace");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
