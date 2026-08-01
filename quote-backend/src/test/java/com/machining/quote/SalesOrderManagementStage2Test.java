package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderManagementStage2Test {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V71__sales_order_management_module.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path SYSTEM_ROLE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SystemRoleService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_DOC = Path.of("..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");

    @Test
    void migrationCreatesSalesOrderManagementPermissionsWithoutReusingHistoryOrderPermission() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "market:sales-order:view",
                        "销售订单",
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
                        "READONLY",
                        "WHERE permission_code = 'market:history-order:view'"
                )
                .doesNotContain("'/history-orders'")
                .doesNotContain("market:history-order:view', '销售订单'");
    }

    @Test
    void fallbackCatalogDefinesIndependentSalesOrderManagementPermissions() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(190L, \"MENU\", \"market:sales-order:view\", \"销售订单\", \"/sales-orders\", \"SalesOrderManage\", true, 125)",
                        "permission(191L, \"API\", \"market:sales-order:customer-select\", \"销售订单选择客户\", null, null, false, 126, 190L)",
                        "permission(192L, \"API\", \"market:sales-order:file-preview\", \"销售订单预览文件\", null, null, false, 127, 190L)",
                        "permission(193L, \"BUTTON\", \"market:sales-order:supplement-drawing\", \"销售订单补传图纸\", null, null, false, 128, 190L)",
                        "permission(194L, \"BUTTON\", \"market:sales-order:discard\", \"废弃销售订单\", null, null, false, 129, 190L)",
                        "permission(195L, \"BUTTON\", \"market:sales-order:restore\", \"恢复销售订单\", null, null, false, 130, 190L)",
                        "Map.entry(\"market:sales-order:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-order:customer-select\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-order:file-preview\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-order:supplement-drawing\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:discard\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:restore\", Set.of(\"MARKET_MANAGER\"))"
                )
                .doesNotContain("permission(190L, \"MENU\", \"market:history-order:view\", \"销售订单\"");
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
                        "销售订单",
                        "`market:sales-order:view`",
                        "`market:sales-order:customer-select`",
                        "`market:sales-order:file-preview`",
                        "`market:sales-order:supplement-drawing`",
                        "`market:sales-order:discard`",
                        "`market:sales-order:restore`",
                        "`/sales-orders`"
                );
        assertThat(adminDoc)
                .contains(
                        "销售订单",
                        "`market:sales-order:view`",
                        "旧 `/history-orders` 只作为兼容入口跳转到 `/sales-orders`"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
