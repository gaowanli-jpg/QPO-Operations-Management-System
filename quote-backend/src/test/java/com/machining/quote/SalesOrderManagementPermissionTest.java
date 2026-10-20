package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderManagementPermissionTest {
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PRODUCTION_TASK_NOTICE_PERMISSION = Path.of("src", "main", "resources", "db", "migration", "V38__sales_order_production_task_notice_export_permission.sql");
    private static final Path SALES_ORDER_UPDATE_PERMISSION = Path.of("src", "main", "resources", "db", "migration", "V44__sales_order_management_update_permission.sql");
    private static final Path ITEM_DETAIL_EXPORT_PERMISSION = Path.of("src", "main", "resources", "db", "migration", "V52__sales_order_item_detail_export_permission.sql");
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
                        "permission(215L, \"BUTTON\", \"market:sales-order:production-task-notice-export\"",
                        "permission(225L, \"BUTTON\", \"market:sales-order:item-detail-export\"",
                        "permission(217L, \"BUTTON\", \"market:sales-order:update\"",
                        "Map.entry(\"market:sales-order:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:sales-order:supplement-drawing\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:discard\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:restore\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:production-task-notice-export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:item-detail-export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:sales-order:update\", Set.of(\"SUPER_ADMIN\", \"MARKET_MANAGER\"))"
                )
                .doesNotContain("permission(190L, \"MENU\", \"market:history-order:view\"");
    }

    @Test
    void productionTaskNoticeExportPermissionIsAppendOnlyMigration() throws IOException {
        String migration = read(PRODUCTION_TASK_NOTICE_PERMISSION);

        assertThat(migration)
                .contains(
                        "market:sales-order:production-task-notice-export",
                        "导出生产任务通知单",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "ON CONFLICT (id) DO UPDATE SET",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING"
                )
                .doesNotContain("READONLY");
    }

    @Test
    void salesOrderManagementUpdatePermissionIsAppendOnlyMigration() throws IOException {
        String migration = read(SALES_ORDER_UPDATE_PERMISSION);

        assertThat(migration)
                .contains(
                        "market:sales-order:update",
                        "维护销售订单信息",
                        "SUPER_ADMIN",
                        "MARKET_MANAGER",
                        "ON CONFLICT (id) DO UPDATE SET",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING"
                )
                .doesNotContain("MARKET_USER", "READONLY");
    }

    @Test
    void itemDetailExportPermissionIsAppendOnlyMigration() throws IOException {
        String migration = read(ITEM_DETAIL_EXPORT_PERMISSION);

        assertThat(migration)
                .contains(
                        "market:sales-order:item-detail-export",
                        "导出明细表",
                        "market:sales-order:view",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "ON CONFLICT (permission_code) DO UPDATE SET",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING"
                )
                .doesNotContain("READONLY");
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
                        "market:sales-order:production-task-notice-export",
                        "market:sales-order:item-detail-export",
                        "market:sales-order:update",
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
