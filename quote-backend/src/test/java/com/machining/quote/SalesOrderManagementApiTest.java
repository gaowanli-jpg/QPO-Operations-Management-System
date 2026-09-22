package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderManagementApiTest {
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderManageController.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderManageService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");

    @Test
    void controllerExposesSalesOrderManagementReadApisWithIndependentPermissions() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@RequestMapping(\"/api/market/sales-order-management\")",
                        "@GetMapping(\"/customers\")",
                        "@RequirePermission(\"market:sales-order:customer-select\")",
                        "@GetMapping(\"/orders\")",
                        "@GetMapping(\"/orders/{salesOrderId}\")",
                        "@GetMapping(\"/orders/{salesOrderId}/items\")",
                        "@GetMapping(\"/orders/{salesOrderId}/items/export\")",
                        "@GetMapping(\"/orders/{salesOrderId}/tickets\")",
                        "@GetMapping(\"/orders/{salesOrderId}/process-sheets\")",
                        "@GetMapping(\"/orders/{salesOrderId}/production-task-notice/export\")",
                        "@PostMapping(\"/orders/{salesOrderId}/u9-resync-reset\")",
                        "@RequirePermission(\"market:u9-sync:submit\")",
                        "@RequirePermission(\"market:sales-order:item-detail-export\")",
                        "@RequirePermission(\"market:sales-order:production-task-notice-export\")",
                        "@RequirePermission(\"market:sales-order:view\")",
                        "SalesOrderItemDetailExportService",
                        "exportItemDetails"
                )
                .doesNotContain("market:history-order:");
    }

    @Test
    void serviceReadsOnlyIndependentSalesOrderTablesBeforeSharedDownstreamQueries() throws IOException {
        String source = read(SERVICE);

        assertThat(source)
                .contains(
                        "SalesOrderHeaderMapper",
                        "SalesOrderItemMapper",
                        "OrderDeliveryTicketMapper",
                        "dataScopeService.applySalesOrderScope",
                        "dataScopeService.assertCanAccessSalesOrder",
                        "ReleaseSourceType.SALES_ORDER.name()",
                        ".eq(\"source_type\"",
                        ".eq(\"source_order_id\"",
                        ".eq(\"sales_order_id\"",
                        "LIMIT ",
                        "OFFSET "
                )
                .doesNotContain(
                        "OrderRfqMapper",
                        "ItemQuoteVersionMapper",
                        "\"order_rfq\"",
                        "\"item_quote_version\""
                );
    }

    @Test
    void permissionDocsTrackSalesOrderManagementBackendBoundary() throws IOException {
        String source = read(PERMISSION_DOC);

        assertThat(source)
                .contains(
                        "controller/SalesOrderManageController.java",
                        "market:sales-order:view / market:sales-order:customer-select",
                        "market:sales-order:item-detail-export",
                        "market:sales-order:production-task-notice-export",
                        "sales_order_header",
                        "sales_order_item",
                        "source_type = SALES_ORDER"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
