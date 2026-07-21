package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveredItemFoundationTest {
    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V45__delivery_ticket_mes_wms_foundation.sql");
    private static final Path ORDER_DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path ORDER_DELIVERY_TICKET_ALLOCATION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");

    @Test
    void foundationMigrationDefinesMesWmsFieldsAndAllocationTable() throws IOException {
        String migration = read(FOUNDATION_MIGRATION);

        assertThat(migration)
                .as("The delivered item demand pool must carry MES/WMS state on the immutable ticket row")
                .contains(
                        "material_no",
                        "mes_sync_status",
                        "mes_sync_message",
                        "mes_synced_at",
                        "wms_allocate_status",
                        "wms_allocate_message",
                        "wms_allocated_at"
                );
        assertThat(migration)
                .as("WMS split rows must be modeled as a child table of order_delivery_ticket")
                .contains(
                        "CREATE TABLE IF NOT EXISTS order_delivery_ticket_allocation",
                        "ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id)",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED"
                );
        assertThat(migration)
                .as("The foundation migration owns the delivered item menu permission")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:file-preview",
                        "/delivered-items"
                );
    }

    @Test
    void deliveryTicketEntityCarriesMesWmsFields() throws IOException {
        String source = read(ORDER_DELIVERY_TICKET);

        assertThat(source)
                .contains(
                        "private String materialNo;",
                        "private String mesSyncStatus;",
                        "private String mesSyncMessage;",
                        "private LocalDateTime mesSyncedAt;",
                        "private String wmsAllocateStatus;",
                        "private String wmsAllocateMessage;",
                        "private LocalDateTime wmsAllocatedAt;"
                );
    }

    @Test
    void deliveryTicketAllocationEntityExistsForFutureWmsSplitRows() throws IOException {
        String source = read(ORDER_DELIVERY_TICKET_ALLOCATION);

        assertThat(source)
                .contains(
                        "@TableName(value = \"order_delivery_ticket_allocation\"",
                        "private Integer ticketId;",
                        "private String allocationType;",
                        "private Integer qty;",
                        "private String materialNo;",
                        "private String wmsProductionOrderNo;"
                );
    }

    @Test
    void deliveredItemControllerExposesPermissionProtectedQueryEndpoints() throws IOException {
        String source = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(source)
                .contains(
                        "@RequestMapping(\"/api/order-delivery\")",
                        "@RequirePermission(\"market:delivered-item:view\")",
                        "@GetMapping(\"/tickets\")",
                        "@GetMapping(\"/tickets/{ticketId}\")"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
