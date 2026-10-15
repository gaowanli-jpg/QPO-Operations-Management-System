package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    private static final Path QUERY_INDEX_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path MES_PRODUCTION_DISPATCH_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path MES_PRODUCTION_STATUS_INDEX_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SCHEMA_SQL = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path PRODUCTION_ORDER_REMOVAL_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V11__remove_wms_" + "production_order_no.sql");
    private static final Path LOCKED_ITEM_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "LockedItemManage.vue");
    private static final Path PRODUCTION_ITEM_PAGE = Path.of(
            "..", "quote-frontend", "src", "components", "ProductionItemManage.vue");

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
                        "/api/order-delivery/locked-items",
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
                        "private String internalProjectNo;"
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
                        "private String itemType;",
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
                        "private String remark;",
                        "private String description;",
                        "private LocalDate deliveryDate;",
                        "private Object processRoute;",
                        "private Object materialData;",
                        "private Double materialGrossWeight;",
                        "private Double partNetWeight;"
                );
        assertThat(pageDto)
                .contains("private java.util.List<LockedItemDTO> records;", "private long total;");
        assertThat(queryReq)
                .contains(
                        "private String customerName;",
                        "private String customerCode;",
                        "private String partNo;",
                        "private String materialNo;",
                        "private String internalProjectNo;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String itemType;",
                        "private String allocationType;"
                );
        assertThat(service)
                .contains(
                        "listAllocations",
                        "listTicketAllocations",
                        "dataScopeService.applyDeliveryTicketScope(query, \"ticket\")",
                        "applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName())",
                        "sales_header.customer_code = {0}",
                        "SELECT so.id FROM sales_order_header so WHERE so.customer_code = {2}",
                        "applyLinkedItemLike(query, \"alloc.customer_order_no\", \"sales_item.customer_order_no\", req.getCustomerOrderNo())",
                        "applyLinkedItemLike(query, \"alloc.customer_project_no\", \"sales_item.customer_project_no\", req.getCustomerProjectNo())",
                        "applyLinkedItemEquals(query, \"alloc.item_type\", \"sales_item.item_type\", req.getItemType())",
                        "(CASE WHEN sales_item.id IS NULL THEN alloc.delivery_date ELSE COALESCE(sales_item.internal_delivery_date, sales_item.sales_order_date) END) >= {0}",
                        "(CASE WHEN sales_item.id IS NULL THEN alloc.delivery_date ELSE COALESCE(sales_item.internal_delivery_date, sales_item.sales_order_date) END) <= {0}",
                        "listCustomers",
                        "countLockedItems",
                        "selectLockedItems"
                );
        assertThat(service)
                .as("Customer picker passes exact customer names, so locked-item queries should not use fuzzy customer matching")
                .doesNotContain("applyLike(query, \"alloc.customer_name\", req.getCustomerName())");
        assertNoLegacyProductionOrderNumber(dto, LOCKED_ITEM_DTO);
        assertNoLegacyProductionOrderNumber(queryReq, LOCKED_ITEM_QUERY_REQ);
        assertNoLegacyProductionOrderNumber(service, LOCKED_ITEM_SERVICE);
        assertThat(mapper)
                .contains(
                        "countLockedItems",
                        "selectLockedItems",
                        "order_delivery_ticket_allocation alloc",
                        "LEFT JOIN order_rfq rfq ON rfq.id = alloc.rfq_id",
                        "LEFT JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.order_type ELSE sales_item.order_type END AS order_type",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.item_type ELSE sales_item.item_type END AS item_type",
                        "alloc.rfq_no",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.customer_order_no ELSE sales_item.customer_order_no END AS customer_order_no",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.customer_project_no ELSE sales_item.customer_project_no END AS customer_project_no",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.process_route ELSE sales_item.process_route END AS process_route",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.material_data ELSE sales_item.material_data END AS material_data",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.material_gross_weight ELSE sales_item.material_gross_weight END AS material_gross_weight",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.part_net_weight ELSE sales_item.part_net_weight END AS part_net_weight",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.remark ELSE sales_item.remark END AS remark",
                        "ORDER BY alloc.id DESC"
                );
        assertNoLegacyProductionOrderNumber(mapper, ALLOCATION_MAPPER);
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
                        "LEFT JOIN item_quote_version item",
                        "COALESCE(alloc.order_type, ticket.order_type, rfq.order_type)"
                );
        assertThat(mapper)
                .as("Linked locked-item rows should prefer current sales-order item fields and keep allocation fallback")
                .contains(
                        "LEFT JOIN sales_order_item sales_item ON sales_item.allocation_id = alloc.id",
                        "LEFT JOIN sales_order_header sales_header ON sales_header.id = sales_item.sales_order_id",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.qty ELSE sales_item.qty END AS qty",
                        "CASE WHEN sales_item.id IS NULL THEN alloc.material_no ELSE sales_item.material_no END AS material_no"
                );
        assertThat(controller)
                .contains(
                        "@GetMapping(\"/locked-items\")",
                        "@GetMapping(\"/locked-items/customers\")",
                        "@GetMapping(\"/tickets/{ticketId}/allocations\")",
                        "@RequirePermission(\"market:locked-item:view\")",
                        "@RequirePermission(\"market:locked-item:customer-select\")",
                        "return lockedItemService.pageCustomerOptions(field, keyword, page, pageSize)"
                )
                .doesNotContain(
                        "@GetMapping(\"/allocations\")",
                        "@GetMapping(\"/allocations/customers\")",
                        "listAllocationCustomers("
                );
    }

    @Test
    void lockedItemQueryReturnsMesProductionDispatchSkeletonFields() throws IOException {
        String migration = read(MES_PRODUCTION_DISPATCH_MIGRATION);
        String entity = read(ALLOCATION_ENTITY);
        String dto = read(LOCKED_ITEM_DTO);
        String queryReq = read(LOCKED_ITEM_QUERY_REQ);
        String service = read(LOCKED_ITEM_SERVICE);
        String mapper = read(ALLOCATION_MAPPER);

        assertThat(migration)
                .contains(
                        "ticket_qty",
                        "mes_production_status",
                        "mes_production_request_id",
                        "mes_production_external_id",
                        "mes_production_synced_at",
                        "mes_production_message"
                );
        assertThat(entity)
                .contains(
                        "private Integer ticketQty;",
                        "private String mesProductionStatus;",
                        "private String mesProductionRequestId;",
                        "private String mesProductionExternalId;",
                        "private LocalDateTime mesProductionSyncedAt;",
                        "private String mesProductionMessage;"
                );
        assertThat(dto)
                .contains(
                        "private Integer ticketQty;",
                        "private String mesProductionStatus;",
                        "private String mesProductionRequestId;",
                        "private String mesProductionExternalId;",
                        "private LocalDateTime mesProductionSyncedAt;",
                        "private String mesProductionMessage;"
                );
        assertThat(queryReq)
                .as("Locked-item list filtering must wire the frontend MES production status filter into the backend query")
                .contains("private String mesProductionStatus;");

        assertThat(mapper)
                .contains(
                        "alloc.ticket_qty",
                        "alloc.mes_production_status",
                        "alloc.mes_production_request_id",
                        "alloc.mes_production_external_id",
                        "alloc.mes_production_synced_at",
                        "alloc.mes_production_message"
                );
    }

    @Test
    void lockedItemMesProductionStatusHasDedicatedPagingIndex() throws IOException {
        String migration = read(MES_PRODUCTION_STATUS_INDEX_MIGRATION);
        String schema = read(SCHEMA_SQL);

        assertThat(migration)
                .as("Direct production-status filtering should not depend on the status+type composite index for paging")
                .contains(
                        "idx_delivery_allocation_mes_production_status_id",
                        "ON order_delivery_ticket_allocation USING btree (mes_production_status, id DESC)"
                );
        assertThat(schema)
                .as("Fresh schema should include the MES production status snapshot fields and the same paging index")
                .contains(
                        "ticket_qty integer",
                        "mes_production_status character varying(30) DEFAULT 'PENDING'",
                        "mes_production_request_id character varying(80)",
                        "idx_delivery_allocation_mes_production_status_id",
                        "ON order_delivery_ticket_allocation USING btree (mes_production_status, id DESC)"
                );
    }

    @Test
    void lockedItemCommonQueryIndexesStayFocusedOnRecommendedFields() throws IOException {
        String migration = read(QUERY_INDEX_MIGRATION);

        assertThat(migration)
                .contains(
                        "idx_delivery_allocation_rfq_no",
                        "ON order_delivery_ticket_allocation USING btree (rfq_no, id)",
                        "idx_delivery_allocation_internal_project",
                        "ON order_delivery_ticket_allocation USING btree (internal_project_no, id)",
                        "idx_delivery_allocation_delivery_date",
                        "ON order_delivery_ticket_allocation USING btree (delivery_date, id)"
                )
                .doesNotContain(
                        "idx_delivery_allocation_customer_order_no",
                        "idx_delivery_allocation_customer_project_no"
                );
    }

    @Test
    void wmsProductionOrderNumberRemovedFromRuntimeSourcesAndV11DropsColumn() throws IOException {
        String camel = "wms" + "ProductionOrderNo";
        String snake = "wms_" + "production_order_no";

        for (Path path : List.of(
                ALLOCATION_ENTITY,
                LOCKED_ITEM_DTO,
                LOCKED_ITEM_QUERY_REQ,
                ALLOCATION_MAPPER,
                LOCKED_ITEM_SERVICE,
                LOCKED_ITEM_PAGE,
                PRODUCTION_ITEM_PAGE)) {
            assertThat(read(path))
                    .as("%s must not retain the retired WMS production order number", path)
                    .doesNotContain(camel, snake);
        }

        assertThat(read(PRODUCTION_ORDER_REMOVAL_MIGRATION))
                .as("V11 should remove the retired production order number index and column")
                .contains(
                        "DROP INDEX IF EXISTS idx_delivery_allocation_wms_order",
                        "DROP COLUMN IF EXISTS " + snake
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void assertNoLegacyProductionOrderNumber(String source, Path path) {
        assertThat(source)
                .as("%s should not expose the retired WMS production order number", path)
                .doesNotContain("wms" + "ProductionOrderNo", "wms_" + "production_order_no");
    }
}
