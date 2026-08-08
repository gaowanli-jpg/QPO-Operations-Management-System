package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStorageConfigStage1Test {
    private static final Path BASELINE_SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");

    private static final List<String> STORAGE_PERMISSION_CODES = List.of(
            "system:config:view",
            "system:config:storage:update",
            "system:config:storage:test",
            "system:config:storage:activate"
    );

    @Test
    void baselineSchemaCreatesStorageConfigAndFileObjectTables() throws IOException {
        String schema = read(BASELINE_SCHEMA);

        assertThat(schema)
                .contains(
                        "CREATE TABLE system_file_storage_config",
                        "CREATE TABLE system_file_object",
                        "storage_type character varying(20) NOT NULL",
                        "config_json jsonb NOT NULL",
                        "public_path character varying(800) NOT NULL",
                        "object_key character varying(800) NOT NULL",
                        "external_url character varying(1000)",
                        "CONSTRAINT ck_system_file_storage_config_type",
                        "CONSTRAINT ck_system_file_object_storage_type",
                        "CREATE UNIQUE INDEX uk_system_file_storage_config_active",
                        "CREATE UNIQUE INDEX uk_system_file_object_public_path"
                );
    }

    @Test
    void baselineSeedAddsSystemConfigPermissionTreeAndDefaultLocalConfig() throws IOException {
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .contains(
                        "system:config:view",
                        "system:config:storage:update",
                        "system:config:storage:test",
                        "system:config:storage:activate",
                        "/system-config",
                        "SystemConfigManage",
                        "ERP_UPLOAD_DIR",
                        "'LOCAL'",
                        "'Default local upload storage'",
                        "rootDir"
                )
                .doesNotContain("common:storage");
    }

    @Test
    void fallbackCatalogContainsSystemConfigPermissionsForSuperAdminOnly() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(174L, \"MENU\", \"system:config:view\"",
                        "\"/system-config\"",
                        "\"SystemConfigManage\"",
                        "permission(175L, \"BUTTON\", \"system:config:storage:update\"",
                        "permission(176L, \"BUTTON\", \"system:config:storage:test\"",
                        "permission(177L, \"BUTTON\", \"system:config:storage:activate\""
                );
        for (String code : STORAGE_PERMISSION_CODES) {
            assertThat(source).contains("Map.entry(\"" + code + "\", Set.of())");
        }
        assertThat(source)
                .doesNotContain("Map.entry(\"system:config:view\", Set.of(\"MARKET_MANAGER\"")
                .doesNotContain("Map.entry(\"system:config:storage:update\", Set.of(\"MARKET_MANAGER\"");
    }

    @Test
    void permissionMappingDocDescribesSystemConfigPermissions() throws IOException {
        String doc = read(PERMISSION_DOC);

        assertThat(doc)
                .contains(
                        "system:config:view",
                        "system:config:storage:update",
                        "system:config:storage:test",
                        "system:config:storage:activate",
                        "/system-config"
                )
                .doesNotContain("common:storage");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
