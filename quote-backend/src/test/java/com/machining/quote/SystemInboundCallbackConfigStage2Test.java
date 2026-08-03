package com.machining.quote;

import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemInboundCallbackConfigStage2Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V78__system_inbound_callback_config.sql"
    );
    private static final Path ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity",
            "SystemInboundCallbackConfig.java"
    );
    private static final Path MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper",
            "SystemInboundCallbackConfigMapper.java"
    );
    private static final Path CALLBACK_CODE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "integration",
            "InboundCallbackCode.java"
    );
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service",
            "PermissionService.java"
    );
    private static final Path PERMISSION_CATALOG_DOC = Path.of(
            "..", "docs", "QPO权限用户体系",
            "02-权限点清单与现有角色映射.md"
    );
    private static final Path PERMISSION_ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系",
            "03-权限体系部署与管理员使用说明.md"
    );
    private static final List<String> INBOUND_PERMISSION_CODES = List.of(
            "system:config:inbound:update",
            "system:config:inbound:token-rotate"
    );

    @Test
    void migrationCreatesInboundCallbackConfigTableAndIndexes() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS system_inbound_callback_config",
                        "callback_code VARCHAR(60) NOT NULL",
                        "source_system_code VARCHAR(30) NOT NULL",
                        "name VARCHAR(100) NOT NULL",
                        "enabled BOOLEAN NOT NULL DEFAULT TRUE",
                        "token_enabled BOOLEAN NOT NULL DEFAULT TRUE",
                        "public_base_url VARCHAR(500)",
                        "callback_path VARCHAR(300) NOT NULL",
                        "token_hash VARCHAR(128)",
                        "token_mask VARCHAR(30)",
                        "token_updated_at TIMESTAMP",
                        "last_validate_status VARCHAR(20)",
                        "last_validate_message VARCHAR(500)",
                        "last_validate_at TIMESTAMP",
                        "CONSTRAINT uk_system_inbound_callback_code UNIQUE (callback_code)",
                        "CONSTRAINT ck_system_inbound_callback_source CHECK (source_system_code IN ('MES'))",
                        "CONSTRAINT ck_system_inbound_callback_validate_status"
                )
                .contains(
                        "CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_source",
                        "ON system_inbound_callback_config(source_system_code)",
                        "CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_enabled",
                        "ON system_inbound_callback_config(enabled)"
                );
    }

    @Test
    void migrationAddsInboundPermissionsUnderSystemConfigForSuperAdminOnly() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "'system:config:view'",
                        "'system:config:inbound:update'",
                        "'system:config:inbound:token-rotate'",
                        "'维护入站接口配置'",
                        "'轮换入站回调Token'",
                        "WHERE r.role_code = 'SUPER_ADMIN'"
                )
                .doesNotContain("MARKET_USER")
                .doesNotContain("MARKET_MANAGER")
                .doesNotContain("READONLY")
                .doesNotContain("common:");
    }

    @Test
    void entityMapperAndCallbackCodeArePresent() throws IOException {
        String entity = read(ENTITY);
        String mapper = read(MAPPER);
        String callbackCode = read(CALLBACK_CODE);

        assertThat(entity)
                .contains(
                        "@TableName(\"system_inbound_callback_config\")",
                        "class SystemInboundCallbackConfig",
                        "private Long id;",
                        "private String callbackCode;",
                        "private String sourceSystemCode;",
                        "private String name;",
                        "private Boolean enabled;",
                        "private Boolean tokenEnabled;",
                        "private String publicBaseUrl;",
                        "private String callbackPath;",
                        "private String tokenHash;",
                        "private String tokenMask;",
                        "private LocalDateTime tokenUpdatedAt;",
                        "private String lastValidateStatus;",
                        "private String lastValidateMessage;",
                        "private LocalDateTime lastValidateAt;"
                );
        assertThat(mapper)
                .contains(
                        "@Mapper",
                        "interface SystemInboundCallbackConfigMapper extends BaseMapper<SystemInboundCallbackConfig>"
                );
        assertThat(callbackCode)
                .contains(
                        "enum InboundCallbackCode",
                        "MES_MATERIAL_CALLBACK",
                        "fromCode(String value)"
                );
    }

    @Test
    void fallbackCatalogContainsInboundPermissionsForSuperAdminOnly() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(197L, \"BUTTON\", \"system:config:inbound:update\"",
                        "8497, 174L",
                        "permission(198L, \"BUTTON\", \"system:config:inbound:token-rotate\"",
                        "8498, 174L"
                );
        for (String code : INBOUND_PERMISSION_CODES) {
            assertThat(source)
                    .as(code + " must remain super-admin only in fallback role mapping")
                    .contains("Map.entry(\"" + code + "\", Set.of())");
        }

        PermissionInfoDTO adminInfo = new PermissionService().getPermissionInfo(null, "ADMIN");
        assertThat(adminInfo.getPermissions()).containsAll(INBOUND_PERMISSION_CODES);
        PermissionInfoDTO marketInfo = new PermissionService().getPermissionInfo(null, "MARKET");
        assertThat(marketInfo.getPermissions()).doesNotContainAnyElementsOf(INBOUND_PERMISSION_CODES);
    }

    @Test
    void permissionDocsDescribeInboundCallbackPermissions() throws IOException {
        String catalogDoc = read(PERMISSION_CATALOG_DOC);
        String adminDoc = read(PERMISSION_ADMIN_DOC);

        assertThat(catalogDoc)
                .contains(
                        "`system:config:inbound:update`",
                        "`system:config:inbound:token-rotate`",
                        "入站接口配置",
                        "MES 回调",
                        "不新增 `common:*`"
                )
                .doesNotContain("`common:inbound");
        assertThat(adminDoc)
                .contains(
                        "入站接口配置",
                        "`system:config:inbound:update`",
                        "`system:config:inbound:token-rotate`",
                        "MES 回调"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
