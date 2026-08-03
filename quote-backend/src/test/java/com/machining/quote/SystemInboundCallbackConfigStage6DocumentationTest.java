package com.machining.quote;

import com.machining.quote.service.AuditService;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemInboundCallbackConfigStage6DocumentationTest {
    private static final Path SYSTEM_OUTBOUND_DOC = Path.of(
            "..", "docs", "QPO系统配置", "03-系统对接配置开发文档.md");
    private static final Path SYSTEM_SPLIT_DOC = Path.of(
            "..", "docs", "QPO系统配置", "04-系统配置出站入站接口拆分与MES回调配置开发文档.md");
    private static final Path PERMISSION_CATALOG_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path MES_SYNC_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md");
    private static final Path MES_OUTBOUND_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "06-提供给MES的物料号接口对接说明.md");
    private static final Path MES_CALLBACK_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "08-MES物料号回调接口对接说明.md");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_RENAME_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V79__rename_integration_permissions_to_outbound.sql");

    @Test
    void systemConfigDocsDescribeOutboundInboundSplitAndKeepInboundDetailsInStage4Doc() throws IOException {
        assertThat(read(SYSTEM_OUTBOUND_DOC))
                .contains(
                        "更名为出站接口配置",
                        "文件存储 / 出站接口 / 入站接口",
                        "入站接口配置详见 `04-系统配置出站入站接口拆分与MES回调配置开发文档.md`",
                        "QPO 主动调用 U9、MES、WMS")
                .doesNotContain(
                        "文件存储\n  - 系统对接",
                        "MES 回调 token 字段放在出站接口配置");

        assertThat(read(SYSTEM_SPLIT_DOC))
                .contains(
                        "原 `系统对接` 页面文案改为 `出站接口`",
                        "入站接口",
                        "MES 回调 token 支持在系统配置中生成、轮换和启停");
    }

    @Test
    void permissionAndAuditDocsUseOutboundAndInboundGovernanceTerms() throws IOException {
        assertThat(read(PERMISSION_CATALOG_DOC))
                .contains(
                        "出站接口配置",
                        "`system:config:integration:update`",
                        "`system:config:inbound:update`",
                        "`system:config:inbound:token-rotate`")
                .doesNotContain("维护系统对接配置");

        assertThat(read(PERMISSION_ADMIN_DOC))
                .contains(
                        "系统 > 系统配置 > 出站接口",
                        "系统 > 系统配置 > 入站接口",
                        "MES 回调接口的真实 token 由入站接口配置生成后通过受控渠道交付给 MES")
                .doesNotContain("系统 > 系统配置 > 系统对接");

        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "出站接口配置审计口径",
                        "入站接口配置审计口径",
                        "SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE",
                        "SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE",
                        "SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE",
                        "不得记录真实 token、token hash、Authorization header 或完整回调体");

        assertThat(read(FILE_LOG_DOC))
                .contains(
                        "Inbound callback config updated",
                        "Inbound callback token rotated",
                        "Inbound callback config validated",
                        "不得记录真实 token、token hash、Authorization header、完整 MES 回调 JSON 或完整请求体");
    }

    @Test
    void mesIntegrationDocsPointCallbackTokenToInboundConfigPage() throws IOException {
        assertThat(read(MES_SYNC_DOC))
                .contains(
                        "QPO 调 MES 的出站配置",
                        "MES 回调 QPO 的入站配置",
                        "系统配置 > 入站接口 > MES 物料号回调",
                        "ERP_MES_CALLBACK_TOKEN",
                        "fallback")
                .contains("不新增用户权限，继续使用 `market:delivered-item:mes-sync`");

        assertThat(read(MES_OUTBOUND_DOC))
                .contains(
                        "回调接口详情见 `08-MES物料号回调接口对接说明.md`",
                        "回调 URL 和 token 由 QPO 管理员从系统配置页面的入站接口提供",
                        "Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}")
                .doesNotContain("未带或带错 `ERP_MES_CALLBACK_TOKEN`");

        assertThat(read(MES_CALLBACK_DOC))
                .contains(
                        "Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}",
                        "系统配置 > 入站接口 > MES 物料号回调",
                        "真实 token 单独交付",
                        "明文只展示一次",
                        "ERP_MES_CALLBACK_TOKEN` 仅作为 QPO 侧老环境 fallback")
                .doesNotContain("Authorization: Bearer {ERP_MES_CALLBACK_TOKEN}");
    }

    @Test
    void defaultPermissionNamesAndAuditLabelsUseOutboundTermWithoutChangingCodes() {
        assertThat(readUnchecked(PERMISSION_SERVICE))
                .contains(
                        "\"system:config:integration:update\", \"维护出站接口配置\"",
                        "\"system:config:integration:test\", \"测试出站接口配置\"",
                        "\"system:config:integration:activate\", \"激活出站接口配置\"");
        assertThat(readUnchecked(PERMISSION_RENAME_MIGRATION))
                .contains(
                        "system:config:integration:update",
                        "维护出站接口配置",
                        "system:config:integration:test",
                        "测试出站接口配置",
                        "system:config:integration:activate",
                        "激活出站接口配置");

        PermissionService permissionService = new PermissionService();
        assertThat(String.valueOf(permissionService.getPermissionInfo(null, "ADMIN")))
                .contains(
                        "system:config:integration:update",
                        "system:config:integration:test",
                        "system:config:integration:activate");

        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_CREATE")).contains("出站接口配置");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_UPDATE")).contains("出站接口配置");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_TEST")).contains("出站接口配置");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_TEST_FAILED")).contains("出站接口配置");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_ACTIVATE")).contains("出站接口配置");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_DISABLE")).contains("出站接口配置");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String readUnchecked(Path path) {
        try {
            return read(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
