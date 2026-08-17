package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9SyncModulePermissionTest {
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path BASELINE_SCHEMA = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SALES_ORDER_ITEM = Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path ADMIN_DOC = Path.of("..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "03-\u6743\u9650\u4f53\u7cfb\u90e8\u7f72\u4e0e\u7ba1\u7406\u5458\u4f7f\u7528\u8bf4\u660e.md");

    @Test
    void baselineAddsU9SyncPermissionsAndMaterialConfirmationFields() throws IOException {
        String seed = read(BASELINE_SEED);
        String schema = read(BASELINE_SCHEMA);

        assertThat(seed)
                .contains(
                        "market:u9-sync:view",
                        "market:u9-sync:customer-select",
                        "market:u9-sync:order-select",
                        "market:u9-sync:submit",
                        "/u9-sync",
                        "SalesOrderU9Sync"
                )
                .doesNotContain("common:u9-sync");
        assertThat(schema)
                .contains(
                        "CREATE TABLE sales_order_item",
                        "material_no_status character varying(30) DEFAULT",
                        "material_no_source character varying(30)",
                        "material_no_source_ticket_id bigint",
                        "material_no_synced_at timestamp without time zone",
                        "material_no_message text"
                );
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
                        "permission(186L, \"MENU\", \"market:u9-sync:view\"",
                        "\"/u9-sync\"",
                        "\"SalesOrderU9Sync\"",
                        "permission(187L, \"API\", \"market:u9-sync:customer-select\"",
                        "permission(188L, \"API\", \"market:u9-sync:order-select\"",
                        "permission(189L, \"BUTTON\", \"market:u9-sync:submit\"",
                        "Map.entry(\"market:u9-sync:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
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
                        "market:u9-sync:view",
                        "market:u9-sync:customer-select",
                        "market:u9-sync:order-select",
                        "market:u9-sync:submit",
                        "/u9-sync")
                .doesNotContain("common:u9-sync");

        assertThat(adminDoc)
                .contains("market:u9-sync:view", "market:u9-sync:submit");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
