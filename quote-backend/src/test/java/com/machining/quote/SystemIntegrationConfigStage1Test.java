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
    private static final Path BASELINE_SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_CATALOG_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "03-\u6743\u9650\u4f53\u7cfb\u90e8\u7f72\u4e0e\u7ba1\u7406\u5458\u4f7f\u7528\u8bf4\u660e.md");

    private static final List<String> INTEGRATION_PERMISSION_CODES = List.of(
            "system:config:integration:update",
            "system:config:integration:test",
            "system:config:integration:activate"
    );

    @Test
    void baselineSchemaCreatesExternalIntegrationConfigTableAndIndexes() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(schema)
                .contains(
                        "CREATE TABLE system_external_integration_config",
                        "system_code character varying(30) NOT NULL",
                        "auth_type character varying(30) DEFAULT 'NONE'::character varying NOT NULL",
                        "config_json jsonb DEFAULT '{}'::jsonb NOT NULL",
                        "CONSTRAINT ck_system_external_integration_system",
                        "CONSTRAINT ck_system_external_integration_auth",
                        "CONSTRAINT ck_system_external_integration_test_status",
                        "CREATE UNIQUE INDEX uk_system_external_integration_active",
                        "CREATE INDEX idx_system_external_integration_system",
                        "CREATE INDEX idx_system_external_integration_enabled"
                )
                .doesNotContain("DATA_CENTER");
    }

    @Test
    void baselineSeedAddsIntegrationPermissionsUnderSystemConfig() throws IOException {
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .contains(
                        "system:config:view",
                        "system:config:integration:update",
                        "system:config:integration:test",
                        "system:config:integration:activate"
                )
                .doesNotContain("common:integration");
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
            assertThat(source).contains("Map.entry(\"" + code + "\", Set.of())");
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
                        "system:config:integration:update",
                        "system:config:integration:test",
                        "system:config:integration:activate",
                        "U9",
                        "MES",
                        "WMS"
                )
                .doesNotContain("common:integration");
        assertThat(adminDoc)
                .contains(
                        "system:config:integration:update",
                        "system:config:integration:test",
                        "system:config:integration:activate"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
