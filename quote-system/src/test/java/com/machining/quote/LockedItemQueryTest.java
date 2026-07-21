package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LockedItemQueryTest {
    private static final Path WMS_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path ALLOCATION_ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");

    @Test
    void lockedItemQueryContractFreezesEndpointsAndPermission() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("Locked-item results need their own query surface and MENU permission")
                .contains(
                        "LockedItemDTO",
                        "LockedItemPageDTO",
                        "LockedItemQueryReq",
                        "LockedItemService",
                        "listAllocations",
                        "listTicketAllocations",
                        "/api/order-delivery/allocations",
                        "/api/order-delivery/tickets/${ticketId}/allocations",
                        "market:locked-item:view",
                        "/locked-items",
                        "LockedItemManage.vue"
                );
    }

    @Test
    void lockedItemFiltersAndColumnsCoverWmsSplitRows() throws IOException {
        String plan = read(WMS_PLAN);
        String entity = read(ALLOCATION_ENTITY);

        assertThat(plan)
                .contains(
                        "customerName",
                        "rfqNo",
                        "partNo",
                        "version",
                        "materialNo",
                        "internalProjectNo",
                        "allocationType",
                        "wms_production_order_no",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED"
                );
        assertThat(entity)
                .contains(
                        "private String allocationType;",
                        "private Integer qty;",
                        "private String materialNo;",
                        "private String customerName;",
                        "private String internalProjectNo;",
                        "private String wmsProductionOrderNo;"
                );
    }

    @Test
    void lockedItemQueryMustRemainDataScopedAndEmptyScopeSafe() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("Locked item reads are order-derived data and must inherit order data scope")
                .contains(
                        "order_delivery_ticket_allocation",
                        "order_delivery_ticket_allocation.id DESC"
                );
        assertThat(plan)
                .as("The stage plan must explicitly keep data scope in the locked-item query contract")
                .contains("数据范围");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
