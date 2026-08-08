package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveredItemFoundationTest {
    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SEED_BASELINE = Path.of(
            "src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path ALLOCATION_SNAPSHOT_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ORDER_DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path ORDER_DELIVERY_TICKET_ALLOCATION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ORDER_DELIVERY_TICKET_ALLOCATION_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketAllocationMapper.java");
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
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "ticket_id integer NOT NULL",
                        "FOREIGN KEY (ticket_id) REFERENCES order_delivery_ticket(id)",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED"
                );
        String seed = read(SEED_BASELINE);
        assertThat(migration)
                .doesNotContain("market:delivered-item:view", "market:delivered-item:file-preview");
        assertThat(seed)
                .as("The foundation migration owns the delivered item menu permission")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:file-preview",
                        "/delivered-items"
                );
    }

    @Test
    void allocationSnapshotMigrationBackfillsLockedItemDisplayFields() throws IOException {
        String migration = read(ALLOCATION_SNAPSHOT_MIGRATION);

        assertThat(migration)
                .as("Locked-item rows must physically own the display snapshot, not depend on ticket joins")
                .contains(
                        "rfq_no",
                        "customer_name",
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "rfq_no",
                        "customer_order_no",
                        "customer_project_no",
                        "material_name",
                        "engineering_material_name",
                        "surface_treatment",
                        "description",
                        "delivery_date",
                        "process_route",
                        "material_data",
                        "material_gross_weight",
                        "part_net_weight"
                )
                .doesNotContain(
                        "UPDATE order_delivery_ticket ticket",
                        "UPDATE order_delivery_ticket_allocation alloc"
                );
    }

    @Test
    void deliveryTicketEntityCarriesMesWmsFields() throws IOException {
        String source = read(ORDER_DELIVERY_TICKET);

        assertThat(source)
                .contains(
                        "private String materialNo;",
                        "private String rfqNo;",
                        "private String customerName;",
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
                        "private String wmsProductionOrderNo;",
                        "private String rfqNo;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String materialName;",
                        "private String engineeringMaterialName;",
                        "private String surfaceTreatment;",
                        "private String description;",
                        "private LocalDate deliveryDate;",
                        "private Object processRoute;",
                        "private Object materialData;",
                        "private Double materialGrossWeight;",
                        "private Double partNetWeight;"
                );
        assertThat(source)
                .as("jsonb snapshot fields need explicit type handlers on the allocation entity")
                .contains(
                        "@TableField(typeHandler = JsonbTypeHandler.class)\n    private Object processRoute;",
                        "@TableField(typeHandler = JsonbTypeHandler.class)\n    private Object materialData;"
                );
    }

    @Test
    void deliveryTicketAllocationMapperExistsForFutureWmsSplitRows() throws IOException {
        String source = read(ORDER_DELIVERY_TICKET_ALLOCATION_MAPPER);

        assertThat(source)
                .contains(
                        "interface OrderDeliveryTicketAllocationMapper",
                        "extends BaseMapper<OrderDeliveryTicketAllocation>",
                        "@Mapper"
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
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
