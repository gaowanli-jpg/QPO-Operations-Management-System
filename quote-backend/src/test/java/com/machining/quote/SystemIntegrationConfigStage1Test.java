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

class SystemIntegrationConfigStage1Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V64__system_external_integration_config.sql"
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

    private static final List<String> INTEGRATION_PERMISSION_CODES = List.of(
            "system:config:integration:update",
            "system:config:integration:test",
            "system:config:integration:activate"
    );

    @Test
    void migrationCreatesExternalIntegrationConfigTableAndIndexes() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS system_external_integration_config",
                        "system_code VARCHAR(30) NOT NULL",
                        "auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE'",
                        "config_json JSONB NOT NULL DEFAULT '{}'::jsonb",
                        "CONSTRAINT ck_system_external_integration_system CHECK (system_code IN ('U9', 'MES', 'WMS'))",
                        "CONSTRAINT ck_system_external_integration_auth CHECK (auth_type IN ('NONE', 'BEARER_TOKEN', 'U9_AUTH_LOGIN'))",
                        "CONSTRAINT ck_system_external_integration_test_status"
                )
                .contains(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uk_system_external_integration_active",
                        "ON system_external_integration_config(system_code, active)",
                        "WHERE active = TRUE",
                        "CREATE INDEX IF NOT EXISTS idx_system_external_integration_system",
                        "CREATE INDEX IF NOT EXISTS idx_system_external_integration_enabled"
                )
                .doesNotContain("DATA_CENTER");
    }

    @Test
    void migrationAddsIntegrationPermissionsUnderSystemConfigForSuperAdminOnly() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "'system:config:view'",
                        "'system:config:integration:update'",
                        "'system:config:integration:test'",
                        "'system:config:integration:activate'",
                        "'维护系统对接配置'",
                        "'测试系统对接配置'",
                        "'激活系统对接配置'",
                        "WHERE r.role_code = 'SUPER_ADMIN'"
                )
                .doesNotContain("MARKET_USER")
                .doesNotContain("MARKET_MANAGER")
                .doesNotContain("READONLY");
    }

    @Test
    void fallbackCatalogContainsIntegrationPermissionsForSuperAdminOnly() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(178L, \"BUTTON\", \"system:config:integration:update\"",
                        "8494, 174L",
                        "permission(179L, \"BUTTON\", \"system:config:integration:test\"",
                        "8495, 174L",
                        "permission(180L, \"BUTTON\", \"system:config:integration:activate\"",
                        "8496, 174L"
                );
        for (String code : INTEGRATION_PERMISSION_CODES) {
            assertThat(source)
                    .as(code + " must remain super-admin only in fallback role mapping")
                    .contains("Map.entry(\"" + code + "\", Set.of())");
        }

        PermissionInfoDTO adminInfo = new PermissionService().getPermissionInfo(null, "ADMIN");
        assertThat(adminInfo.getPermissions()).containsAll(INTEGRATION_PERMISSION_CODES);
        PermissionInfoDTO marketInfo = new PermissionService().getPermissionInfo(null, "MARKET");
        assertThat(marketInfo.getPermissions()).doesNotContainAnyElementsOf(INTEGRATION_PERMISSION_CODES);
    }

    @Test
    void permissionDocsDescribeIntegrationConfigPermissions() throws IOException {
        String catalogDoc = read(PERMISSION_CATALOG_DOC);
        String adminDoc = read(PERMISSION_ADMIN_DOC);

        assertThat(catalogDoc)
                .contains(
                        "`system:config:integration:update`",
                        "`system:config:integration:test`",
                        "`system:config:integration:activate`",
                        "系统对接配置",
                        "U9",
                        "MES",
                        "WMS"
                )
                .doesNotContain("`common:integration");
        assertThat(adminDoc)
                .contains(
                        "系统对接配置",
                        "`system:config:integration:update`",
                        "`system:config:integration:test`",
                        "`system:config:integration:activate`"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
