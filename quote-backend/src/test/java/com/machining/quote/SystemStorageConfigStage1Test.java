package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStorageConfigStage1Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V62__system_file_storage_config.sql"
    );
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service",
            "PermissionService.java"
    );
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系",
            "02-权限点清单与现有角色映射.md"
    );

    private static final List<String> STORAGE_PERMISSION_CODES = List.of(
            "system:config:view",
            "system:config:storage:update",
            "system:config:storage:test",
            "system:config:storage:activate"
    );

    @Test
    void migrationCreatesStorageConfigAndFileObjectTables() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS system_file_storage_config",
                        "CREATE TABLE IF NOT EXISTS system_file_object",
                        "storage_type VARCHAR(20) NOT NULL",
                        "config_json JSONB NOT NULL",
                        "public_path VARCHAR(800) NOT NULL",
                        "object_key VARCHAR(800) NOT NULL",
                        "external_url VARCHAR(1000)",
                        "CONSTRAINT ck_system_file_storage_config_type",
                        "CONSTRAINT ck_system_file_object_storage_type"
                );
        assertThat(migration)
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_system_file_storage_config_active")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_system_file_object_public_path");
    }

    @Test
    void migrationAddsSystemConfigPermissionTreeAndDefaultLocalConfig() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "'system:config:view'",
                        "'system:config:storage:update'",
                        "'system:config:storage:test'",
                        "'system:config:storage:activate'",
                        "'/system-config'",
                        "'SystemConfigManage'",
                        "WHERE r.role_code = 'SUPER_ADMIN'",
                        "ERP_UPLOAD_DIR",
                        "'LOCAL'",
                        "'Default local upload storage'",
                        "'rootDir'"
                )
                .doesNotContain("WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')")
                .doesNotContain("WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER'");
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
            assertThat(source)
                    .as(code + " must be present in fallback role mapping")
                    .contains("Map.entry(\"" + code + "\", Set.of())");
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
                        "`system:config:view`",
                        "`system:config:storage:update`",
                        "`system:config:storage:test`",
                        "`system:config:storage:activate`",
                        "`/system-config`"
                )
                .doesNotContain("`common:storage");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
