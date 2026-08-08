package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderManagementStage2Test {
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path SYSTEM_ROLE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SystemRoleService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path ADMIN_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "03-\u6743\u9650\u4f53\u7cfb\u90e8\u7f72\u4e0e\u7ba1\u7406\u5458\u4f7f\u7528\u8bf4\u660e.md");

    @Test
    void baselineSeedCreatesSalesOrderManagementPermissionsWithoutReusingHistoryOrderPermission() throws IOException {
        String seed = read(BASELINE_SEED);

        assertThat(seed)
                .contains(
                        "market:sales-order:view",
                        "/sales-orders",
                        "SalesOrderManage",
                        "market:sales-order:customer-select",
                        "market:sales-order:file-preview",
                        "market:sales-order:supplement-drawing",
                        "market:sales-order:discard",
                        "market:sales-order:restore",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "READONLY"
                )
                .doesNotContain("market:history-order:view', '\u9500\u552e\u8ba2\u5355");
    }

    @Test
    void fallbackCatalogDefinesIndependentSalesOrderManagementPermissions() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(190L, \"MENU\", \"market:sales-order:view\"",
                        "\"/sales-orders\"",
                        "\"SalesOrderManage\"",
                        "permission(191L, \"API\", \"market:sales-order:customer-select\"",
                        "permission(192L, \"API\", \"market:sales-order:file-preview\"",
                        "permission(193L, \"BUTTON\", \"market:sales-order:supplement-drawing\"",
                        "permission(194L, \"BUTTON\", \"market:sales-order:discard\"",
                        "permission(195L, \"BUTTON\", \"market:sales-order:restore\"",
                        "Map.entry(\"market:sales-order:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-order:supplement-drawing\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:discard\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:restore\", Set.of(\"MARKET_MANAGER\"))"
                )
                .doesNotContain("permission(190L, \"MENU\", \"market:history-order:view\"");
    }

    @Test
    void roleAuthorizationTreeGroupsSalesOrderWithOrderModules() throws IOException {
        String source = read(SYSTEM_ROLE_SERVICE);

        assertThat(source)
                .contains("permissionCode.startsWith(\"market:sales-order:\")")
                .contains("permissionCode.startsWith(\"market:order:\")")
                .contains("return \"orders\";");
    }

    @Test
    void permissionDocsDescribeSalesOrderManagementAsHistoryOrderReplacement() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String adminDoc = read(ADMIN_DOC);

        assertThat(permissionDoc)
                .contains(
                        "market:sales-order:view",
                        "market:sales-order:customer-select",
                        "market:sales-order:file-preview",
                        "market:sales-order:supplement-drawing",
                        "market:sales-order:discard",
                        "market:sales-order:restore",
                        "/sales-orders"
                );
        assertThat(adminDoc)
                .contains("market:sales-order:view", "/history-orders", "/sales-orders");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
