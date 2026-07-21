package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsAllocationTest {
    private static final Path WMS_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V45__delivery_ticket_mes_wms_foundation.sql");
    private static final Path DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path DELIVERY_ALLOCATION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");

    @Test
    void allocationAlgorithmFreezesDynamicProductionLocksAndRemainderRows() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("WMS allocation must split one delivered ticket into inventory, production-order, and remainder rows")
                .contains(
                        "demandQty = ticket.qty",
                        "inStockLockedQty",
                        "inProductionLocks",
                        "productionOrderNo",
                        "lockedQty",
                        "coveredQty",
                        "productionRequiredQty",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED",
                        "assert sum(rows.qty) == demandQty"
                );
    }

    @Test
    void wmsTaskStatusAndSubmissionGuardsAreFrozenBeforeImplementation() throws IOException {
        String plan = read(WMS_PLAN);
        String ticket = read(DELIVERY_TICKET);

        assertThat(plan)
                .as("WMS task submission must require material number and reject duplicate processing/success rows")
                .contains(
                        "WMS_ALLOCATION",
                        "material_no",
                        "wms_allocate_status=SUCCESS",
                        "wms_allocate_status=PROCESSING",
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "WMS allocation submitted",
                        "WMS allocation started",
                        "WMS allocation completed",
                        "WMS allocation failed"
                );
        assertThat(ticket)
                .as("The immutable delivered ticket row already owns WMS task state")
                .contains(
                        "private String wmsAllocateStatus;",
                        "private String wmsAllocateMessage;",
                        "private LocalDateTime wmsAllocatedAt;"
                );
    }

    @Test
    void allocationFoundationSupportsAllRequiredSplitRowFields() throws IOException {
        String migration = read(FOUNDATION_MIGRATION);
        String entity = read(DELIVERY_ALLOCATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE IF NOT EXISTS order_delivery_ticket_allocation",
                        "ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id)",
                        "allocation_type VARCHAR(40) NOT NULL",
                        "qty INTEGER NOT NULL",
                        "material_no VARCHAR(100)",
                        "wms_production_order_no VARCHAR(100)",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED",
                        "idx_delivery_allocation_wms_order"
                );
        assertThat(entity)
                .contains(
                        "private Integer ticketId;",
                        "private Integer rfqId;",
                        "private Integer itemId;",
                        "private String allocationType;",
                        "private Integer qty;",
                        "private String materialNo;",
                        "private String partNo;",
                        "private String version;",
                        "private String customerName;",
                        "private String internalProjectNo;",
                        "private String wmsProductionOrderNo;",
                        "private Object sourcePayload;"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
