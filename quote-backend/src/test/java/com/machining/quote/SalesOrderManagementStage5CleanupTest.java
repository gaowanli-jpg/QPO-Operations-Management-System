package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderManagementStage5CleanupTest {
    @Test
    void frontendHistoryOrderCompatibilityIsRedirectOnly() throws Exception {
        Path historyComponent = Path.of("..", "quote-frontend", "src", "components", "HistoryOrderManage.vue");
        String router = source(Path.of("..", "quote-frontend", "src", "router", "index.js"));
        String orderApi = source(Path.of("..", "quote-frontend", "src", "services", "orderApi.js"));
        String orderPicker = source(Path.of("..", "quote-frontend", "src", "components", "OrderPickerDialog.vue"));

        assertThat(historyComponent).doesNotExist();
        assertThat(router)
                .contains("{ path: '/history-orders', redirect: '/sales-orders' }")
                .doesNotContain("HistoryOrderManage");
        assertThat(orderApi)
                .doesNotContain("'history-order'")
                .doesNotContain("/api/order-management/history-orders/customers")
                .doesNotContain("export const deleteOrder");
        assertThat(orderPicker).doesNotContain("market:history-order");
    }

    @Test
    void backendLegacyHistoryAndDeleteEndpointsAreDeprecatedCompatibilityOnly() throws Exception {
        String controller = source(Path.of("src", "main", "java", "com", "machining", "quote", "controller", "OrderManagementController.java"));
        String service = source(Path.of("src", "main", "java", "com", "machining", "quote", "service", "OrderManagementService.java"));

        assertThat(controller)
                .contains("@Deprecated")
                .contains("@GetMapping(\"/history-orders/customers\")")
                .contains("@DeleteMapping(\"/orders/{rfqId}\")");
        assertThat(service)
                .contains("@Deprecated")
                .contains("deleteOrderCompletely");
    }

    @Test
    void permissionFallbackKeepsOldCodesNonAssignableAndOutOfRoleTree() throws Exception {
        String permissionService = source(Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java"));
        String systemRoleService = source(Path.of("src", "main", "java", "com", "machining", "quote", "service", "SystemRoleService.java"));
        String migration = source(Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql"));

        assertThat(permissionService)
                .contains("deprecatedPermission(103L, \"BUTTON\", \"market:order:delete\"")
                .contains("deprecatedPermission(15L, \"MENU\", \"market:history-order:view\"")
                .doesNotContain("\"HistoryOrderManage\"");
        assertThat(systemRoleService)
                .doesNotContain("permissionCode.startsWith(\"market:history-order:\")");
        assertThat(migration)
                .contains("market:history-order:view")
                .contains("market:history-order:customer-select")
                .contains("market:history-order:file-preview")
                .contains("market:order:delete")
                .contains("'/history-orders'")
                .contains("false);");
    }

    private String source(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
