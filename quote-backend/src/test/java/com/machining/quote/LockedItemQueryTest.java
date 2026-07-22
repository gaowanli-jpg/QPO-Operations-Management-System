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
    private static final Path LOCKED_ITEM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "LockedItemDTO.java");
    private static final Path LOCKED_ITEM_PAGE_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "LockedItemPageDTO.java");
    private static final Path LOCKED_ITEM_QUERY_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "LockedItemQueryReq.java");
    private static final Path LOCKED_ITEM_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "LockedItemService.java");
    private static final Path ALLOCATION_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketAllocationMapper.java");
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");

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
                        "private String orderType;",
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

    @Test
    void lockedItemQueryImplementationProvidesDtosServiceMapperAndEndpoints() throws IOException {
        String dto = read(LOCKED_ITEM_DTO);
        String pageDto = read(LOCKED_ITEM_PAGE_DTO);
        String queryReq = read(LOCKED_ITEM_QUERY_REQ);
        String service = read(LOCKED_ITEM_SERVICE);
        String mapper = read(ALLOCATION_MAPPER);
        String controller = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(dto)
                .contains(
                        "private Integer id;",
                        "private Integer ticketId;",
                        "private Integer rfqId;",
                        "private String rfqNo;",
                        "private String customerName;",
                        "private String allocationType;",
                        "private Integer qty;",
                        "private String materialNo;",
                        "private String orderType;",
                        "private String partNo;",
                        "private String version;",
                        "private String internalProjectNo;",
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
                        "private Double partNetWeight;",
                        "private String wmsProductionOrderNo;"
                );
        assertThat(pageDto)
                .contains("private java.util.List<LockedItemDTO> records;", "private long total;");
        assertThat(queryReq)
                .contains(
                        "private String customerName;",
                        "private String partNo;",
                        "private String materialNo;",
                        "private String allocationType;",
                        "private String wmsProductionOrderNo;"
                );
        assertThat(service)
                .contains(
                        "listAllocations",
                        "listTicketAllocations",
                        "dataScopeService.applyRfqScope(query, \"alloc.rfq_id\")",
                        "countLockedItems",
                        "selectLockedItems"
                );
        assertThat(mapper)
                .contains(
                        "countLockedItems",
                        "selectLockedItems",
                        "order_delivery_ticket_allocation alloc",
                        "LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id",
                        "LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id",
                        "COALESCE(alloc.order_type, ticket.order_type, rfq.order_type) AS order_type",
                        "alloc.rfq_no",
                        "alloc.customer_order_no",
                        "alloc.customer_project_no",
                        "alloc.process_route",
                        "alloc.material_data",
                        "alloc.material_gross_weight",
                        "alloc.part_net_weight",
                        "ORDER BY alloc.id DESC"
                );
        assertThat(mapper)
                .as("Locked-item list rows should read their own immutable allocation snapshot fields")
                .doesNotContain(
                        "ticket.customer_order_no",
                        "ticket.customer_project_no",
                        "ticket.rfq_no",
                        "ticket.process_route",
                        "ticket.material_data",
                        "ticket.material_gross_weight",
                        "ticket.part_net_weight",
                        "LEFT JOIN item_quote_version item"
                );
        assertThat(controller)
                .contains(
                        "@GetMapping(\"/allocations\")",
                        "@GetMapping(\"/tickets/{ticketId}/allocations\")",
                        "@RequirePermission(\"market:locked-item:view\")"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
