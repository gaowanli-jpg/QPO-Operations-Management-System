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
    private static final Path BASELINE_SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path ENTITY = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SystemInboundCallbackConfig.java");
    private static final Path MAPPER = Path.of("src", "main", "java", "com", "machining", "quote", "mapper", "SystemInboundCallbackConfigMapper.java");
    private static final Path CALLBACK_CODE = Path.of("src", "main", "java", "com", "machining", "quote", "integration", "InboundCallbackCode.java");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_CATALOG_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "03-\u6743\u9650\u4f53\u7cfb\u90e8\u7f72\u4e0e\u7ba1\u7406\u5458\u4f7f\u7528\u8bf4\u660e.md");
    private static final List<String> INBOUND_PERMISSION_CODES = List.of(
            "system:config:inbound:update",
            "system:config:inbound:token-rotate"
    );

    @Test
    void baselineSchemaCreatesInboundCallbackConfigTableAndIndexes() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(schema)
                .contains(
                        "CREATE TABLE system_inbound_callback_config",
                        "callback_code character varying(60) NOT NULL",
                        "source_system_code character varying(30) NOT NULL",
                        "name character varying(100) NOT NULL",
                        "enabled boolean DEFAULT true NOT NULL",
                        "token_enabled boolean DEFAULT true NOT NULL",
                        "public_base_url character varying(500)",
                        "callback_path character varying(300) NOT NULL",
                        "token_hash character varying(128)",
                        "token_mask character varying(30)",
                        "token_updated_at timestamp without time zone",
                        "last_validate_status character varying(20)",
                        "last_validate_message character varying(500)",
                        "last_validate_at timestamp without time zone",
                        "ADD CONSTRAINT uk_system_inbound_callback_code UNIQUE (callback_code)",
                        "CONSTRAINT ck_system_inbound_callback_source",
                        "CONSTRAINT ck_system_inbound_callback_validate_status",
                        "CREATE INDEX idx_system_inbound_callback_source",
                        "CREATE INDEX idx_system_inbound_callback_enabled"
                );
    }

    @Test
    void baselineSeedAddsInboundPermissionsUnderSystemConfig() throws IOException {
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .contains(
                        "system:config:view",
                        "system:config:inbound:update",
                        "system:config:inbound:token-rotate"
                )
                .doesNotContain("common:inbound");
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
                .contains("@Mapper", "interface SystemInboundCallbackConfigMapper extends BaseMapper<SystemInboundCallbackConfig>");
        assertThat(callbackCode)
                .contains("enum InboundCallbackCode", "MES_MATERIAL_CALLBACK", "fromCode(String value)");
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
            assertThat(source).contains("Map.entry(\"" + code + "\", Set.of())");
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
                        "system:config:inbound:update",
                        "system:config:inbound:token-rotate",
                        "MES"
                )
                .doesNotContain("common:inbound");
        assertThat(adminDoc)
                .contains("system:config:inbound:update", "system:config:inbound:token-rotate");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
