package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LockedItemMesProductionDocumentationCharacterizationTest {
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path MES_PRODUCTION_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md");
    private static final Path WMS_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");

    @Test
    void mesAndWmsDocsDescribeProductionDispatchConfigInterfaceAndTicketQuantity() throws IOException {
        assertThat(read(MES_SYNC_DOC))
                .contains(
                        "ERP_MES_PRODUCTION_PATH",
                        "物料号获取继续使用 `ERP_MES_MATERIAL_PATH`",
                        "待生产下发使用 `ERP_MES_PRODUCTION_PATH`");

        assertThat(read(MES_PRODUCTION_INTERFACE_DOC))
                .contains(
                        "POST {ERP_MES_BASE_URL}{ERP_MES_PRODUCTION_PATH}",
                        "`allocationType = PRODUCTION_REQUIRED`",
                        "`productionQty`",
                        "`ticketQty`",
                        "`assemblyRelations`",
                        "普通件或无父子关系时传空数组",
                        "不得记录 token",
                        "不得记录完整请求体");

        assertThat(read(WMS_DOC))
                .contains(
                        "`ticket_qty`",
                        "原始提单数量快照",
                        "`qty` 表示 allocation 拆分后的当前数量",
                        "`ticket_qty` 表示未锁库存前的原始提单数量",
                        "原始 ticket 不删除");
    }

    @Test
    void permissionAndLogDocsDescribeProductionDispatchGovernanceWithoutCommonPermissions() throws IOException {
        assertThat(read(PERMISSION_DOC))
                .contains(
                        "market:production-item:mes-production-submit",
                        "待产零件下发MES生产",
                        "不新增可分配 `common:*` 权限")
                .doesNotContain("common:mes-production");

        assertThat(read(ADMIN_DOC))
                .contains(
                        "待产零件下发MES生产",
                        "market:production-item:mes-production-submit",
                        "只控制生产下发，不反向授予 MES 物料号获取或 WMS 锁库能力");

        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT",
                        "PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS",
                        "PRODUCTION_ITEM_MES_PRODUCTION_FAILED",
                        "order_delivery_ticket_allocation",
                        "allocationId",
                        "ticketQty",
                        "productionQty")
                .doesNotContain("完整 MES 生产下发请求体");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "MES production dispatch submitted",
                        "MES production dispatch started",
                        "MES production dispatch completed",
                        "MES production dispatch skipped",
                        "MES production dispatch failed",
                        "allocationId",
                        "requestId",
                        "ticketQty",
                        "productionQty");
    }

    private static String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
