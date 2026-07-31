package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9SyncModuleStage1Test {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V69__sales_order_u9_sync_module.sql");
    private static final Path SALES_ORDER_ITEM = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path ADMIN_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "03-\u6743\u9650\u4f53\u7cfb\u90e8\u7f72\u4e0e\u7ba1\u7406\u5458\u4f7f\u7528\u8bf4\u660e.md");

    @Test
    void migrationAddsU9SyncPermissionsAndMaterialConfirmationFields() throws IOException {
        assertThat(MIGRATION).exists();
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "market:u9-sync:view",
                        "market:u9-sync:customer-select",
                        "market:u9-sync:order-select",
                        "market:u9-sync:submit",
                        "/u9-sync",
                        "SalesOrderU9Sync",
                        "material_no_status VARCHAR(30) NOT NULL DEFAULT '未确认'",
                        "material_no_source VARCHAR(30)",
                        "material_no_source_ticket_id BIGINT",
                        "material_no_synced_at TIMESTAMP",
                        "material_no_message TEXT",
                        "COMMENT ON COLUMN sales_order_item.material_no_status")
                .doesNotContain(
                        "common:u9-sync",
                        "DEFAULT '已确认'");
    }

    @Test
    void salesOrderItemEntityExposesMaterialConfirmationFields() throws IOException {
        String entity = read(SALES_ORDER_ITEM);

        assertThat(entity)
                .contains(
                        "private String materialNoStatus;",
                        "private String materialNoSource;",
                        "private Long materialNoSourceTicketId;",
                        "private LocalDateTime materialNoSyncedAt;",
                        "private String materialNoMessage;");
    }

    @Test
    void fallbackPermissionCatalogPlacesU9SyncBetweenReleaseAndDeliveryDocuments() throws IOException {
        String permissionService = read(PERMISSION_SERVICE);

        assertThat(permissionService)
                .contains(
                        "permission(186L, \"MENU\", \"market:u9-sync:view\", \"U9同步\", \"/u9-sync\", \"SalesOrderU9Sync\", true, 146)",
                        "permission(187L, \"API\", \"market:u9-sync:customer-select\", \"U9同步选择客户\", null, null, false, 147, 186L)",
                        "permission(188L, \"API\", \"market:u9-sync:order-select\", \"U9同步选择销售订单\", null, null, false, 148, 186L)",
                        "permission(189L, \"BUTTON\", \"market:u9-sync:submit\", \"同步U9\", null, null, false, 149, 186L)",
                        "Map.entry(\"market:u9-sync:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:u9-sync:customer-select\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:u9-sync:order-select\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:u9-sync:submit\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))");

        assertThat(permissionService.indexOf("\"market:release:view\""))
                .isLessThan(permissionService.indexOf("\"market:u9-sync:view\""));
        assertThat(permissionService.indexOf("\"market:u9-sync:view\""))
                .isLessThan(permissionService.indexOf("\"market:delivery-document:view\""));
    }

    @Test
    void permissionDocumentationListsU9SyncModule() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String adminDoc = read(ADMIN_DOC);

        assertThat(permissionDoc)
                .contains(
                        "| U9同步 | `market:u9-sync:view` |",
                        "| U9同步 | `market:u9-sync:view` | `MENU` |",
                        "| U9同步选择客户 | `market:u9-sync:customer-select` | `API` |",
                        "| U9同步选择销售订单 | `market:u9-sync:order-select` | `API` |",
                        "| 同步U9 | `market:u9-sync:submit` | `BUTTON` |",
                        "| `market:u9-sync:view` | U9同步 | SUPER_ADMIN, MARKET_USER, MARKET_MANAGER, READONLY |",
                        "| `market:u9-sync:submit` | 同步U9 | SUPER_ADMIN, MARKET_USER, MARKET_MANAGER |",
                        "| `/u9-sync` | MARKET, READONLY, ADMIN | market:u9-sync:view |")
                .doesNotContain("common:u9-sync");

        assertThat(adminDoc)
                .contains("U9同步", "market:u9-sync:view", "market:u9-sync:submit");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
