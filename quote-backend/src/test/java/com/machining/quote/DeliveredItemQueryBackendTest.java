package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveredItemQueryBackendTest {
    private static final Path DELIVERED_ITEM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "DeliveredItemDTO.java");
    private static final Path DELIVERED_ITEM_QUERY_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "DeliveredItemQueryReq.java");
    private static final Path DELIVERED_ITEM_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DeliveredItemService.java");
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path ORDER_DELIVERY_TICKET_MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketMapper.java");
    private static final Path QUERY_INDEX_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path QUERY_OPTIMIZATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ACTIVE_POOL_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path INDEX_CLEANUP_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SCHEMA_SQL = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");

    @Test
    void deliveredItemDtosExposeJoinedOrderItemAndExternalSystemFields() throws IOException {
        String dto = read(DELIVERED_ITEM_DTO);
        String req = read(DELIVERED_ITEM_QUERY_REQ);

        assertThat(dto)
                .contains(
                        "private Integer id;",
                        "private Integer rfqId;",
                        "private String rfqNo;",
                        "private String customerName;",
                        "private Integer itemId;",
                        "private String partNo;",
                        "private String version;",
                        "private String orderType;",
                        "private String remark;",
                        "private String drawingUrl;",
                        "private Object processRoute;",
                        "private Object materialData;",
                        "private String materialNo;",
                        "private String mesSyncStatus;",
                        "private String wmsAllocateStatus;"
                );
        assertThat(req)
                .contains(
                        "private Integer page;",
                        "private Integer pageSize;",
                        "private String flowScope;",
                        "private String flowStage;",
                        "private String customerCode;",
                        "private String customerName;",
                        "private String rfqNo;",
                        "private String partNo;",
                        "private String materialNo;",
                        "private String internalProjectNo;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String mesSyncStatus;",
                        "private String wmsAllocateStatus;"
                );
    }

    @Test
    void deliveredItemMapperUsesJoinedQueryToHydrateListRows() throws IOException {
        String source = read(ORDER_DELIVERY_TICKET_MAPPER);

        assertThat(source)
                .contains(
                        "selectDeliveredItems",
                        "countDeliveredItems",
                        "selectDeliveredItem",
                        "FROM order_delivery_ticket ticket",
                        "LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id",
                        "LEFT JOIN item_quote_version item ON item.id = ticket.item_id",
                        "COALESCE(ticket.rfq_no, rfq.rfq_no) AS rfq_no",
                        "COALESCE(ticket.customer_name, rfq.customer_name) AS customer_name",
                        "ticket.order_type AS order_type",
                        "ticket.remark",
                        "COALESCE(ticket.drawing_url, sales_item.drawing_url, item.drawing_url) AS drawing_url"
                );
        assertThat(source)
                .as("Delivered-item order type is the immutable delivery-ticket batch type, not an RFQ/item fallback")
                .doesNotContain("COALESCE(ticket.order_type, rfq.order_type, item.order_type)");
    }

    @Test
    void deliveredItemServiceAppliesDataScopeAndReturnsEmptyPageWhenScopeFiltersEverything() throws IOException {
        String source = read(DELIVERED_ITEM_SERVICE);

        assertThat(source)
                .contains(
                        "dataScopeService.applyDeliveryTicketScope(query, \"ticket\")",
                        "applyFlowScope(query, req.getFlowScope())",
                        "applyFlowStage(query, req.getFlowStage())",
                        "normalizeFlowStage",
                        "normalizeFlowScope",
                        "FLOW_SCOPE_ACTIVE",
                        "FLOW_SCOPE_COMPLETED",
                        "FLOW_SCOPE_ALL",
                        "countDeliveredItems",
                        "return PageResult.of(List.of(), 0, safePage, safePageSize)",
                        "selectDeliveredItems",
                        "dataScopeService.assertCanAccessDeliveryTicket(ticketId)"
                );
        assertThat(source)
                .as("Common delivered-item filters must cover customer order/project numbers and delivery date")
                .contains(
                        "applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName())",
                        "rfq.customer_code = {0}",
                        "sales_order_header so WHERE so.customer_code = {1}",
                        "applyLike(query, \"ticket.customer_order_no\", req.getCustomerOrderNo())",
                        "applyLike(query, \"ticket.customer_project_no\", req.getCustomerProjectNo())",
                        "query.ge(\"ticket.delivery_date\", req.getDateFrom())",
                        "query.le(\"ticket.delivery_date\", req.getDateTo())",
                        "listCustomers"
                );
        assertThat(source)
                .as("Flow stage is a backend business filter so the frontend does not combine MES/WMS statuses")
                .contains(
                        "FLOW_STAGE_NEED_MATERIAL",
                        "FLOW_STAGE_NEED_WMS",
                        "COALESCE(ticket.mes_sync_status, 'PENDING') <> 'SUCCESS'",
                        "NULLIF(BTRIM(ticket.material_no), '') IS NOT NULL",
                        "query.eq(\"ticket.mes_sync_status\", \"SUCCESS\")",
                        "COALESCE(ticket.wms_allocate_status, 'PENDING') <> 'SUCCESS'"
                );
        assertThat(source)
                .as("Customer picker passes exact customer names, so the delivered-item query should not use fuzzy customer matching")
                .doesNotContain("applyLike(query, \"ticket.customer_name\", req.getCustomerName())");
    }

    @Test
    void deliveredItemActivePoolKeepsCompletedRowsOutOfDefaultListWithoutHidingDetails() throws IOException {
        String req = read(DELIVERED_ITEM_QUERY_REQ);
        String service = read(DELIVERED_ITEM_SERVICE);
        String mapper = read(ORDER_DELIVERY_TICKET_MAPPER);

        assertThat(req)
                .contains("private String flowScope;");
        assertThat(service)
                .contains(
                        "applyFlowScope(query, req.getFlowScope())",
                        "FLOW_SCOPE_ACTIVE",
                        "FLOW_SCOPE_COMPLETED",
                        "FLOW_SCOPE_ALL",
                        "NULLIF(BTRIM(ticket.material_no), '') IS NULL",
                        "COALESCE(ticket.mes_sync_status, 'PENDING') <> 'SUCCESS'",
                        "COALESCE(ticket.wms_allocate_status, 'PENDING') <> 'SUCCESS'",
                        "NOT EXISTS (SELECT 1 FROM order_delivery_ticket_allocation alloc_done WHERE alloc_done.ticket_id = ticket.id)",
                        "EXISTS (SELECT 1 FROM order_delivery_ticket_allocation alloc_done WHERE alloc_done.ticket_id = ticket.id)"
                );
        assertThat(service)
                .as("Detail lookups must remain usable from locked items and audit traces")
                .contains("public DeliveredItemDTO getTicket(Integer ticketId)")
                .doesNotContain("getTicket(Integer ticketId, String flowScope)");
        assertThat(mapper)
                .as("Delivered item filtering must stay in database pagination, not Java/frontend post filtering")
                .contains(
                        "ORDER BY ticket.id DESC",
                        "LIMIT #{limit} OFFSET #{offset}",
                        "SELECT COUNT(*)"
                );
    }

    @Test
    void deliveredItemControllerDelegatesToServiceAndKeepsPermissionBoundary() throws IOException {
        String source = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(source)
                .contains(
                        "private DeliveredItemService deliveredItemService;",
                        "@RequirePermission(\"market:delivered-item:view\")",
                        "public PageResult<DeliveredItemDTO> listTickets(DeliveredItemQueryReq req)",
                        "return deliveredItemService.listTickets(req)",
                        "return deliveredItemService.getTicket(ticketId)",
                        "@GetMapping(\"/tickets/customers\")",
                        "@RequirePermission(\"market:delivered-item:customer-select\")",
                        "return deliveredItemService.pageCustomerOptions(field, keyword, page, pageSize)"
                )
                .doesNotContain("ticketMapper.selectPage")
                .doesNotContain("new QueryWrapper<OrderDeliveryTicket>");
    }

    @Test
    void deliveredItemCommonQueryIndexesStayFocusedOnRecommendedFields() throws IOException {
        String migration = read(QUERY_INDEX_MIGRATION);
        String optimizationMigration = read(QUERY_OPTIMIZATION_MIGRATION);
        String activePoolMigration = read(ACTIVE_POOL_MIGRATION);
        String indexCleanupMigration = read(INDEX_CLEANUP_MIGRATION);
        String schema = read(SCHEMA_SQL);

        assertThat(migration)
                .contains(
                        "idx_order_delivery_ticket_rfq_no",
                        "ON order_delivery_ticket USING btree (rfq_no, id)",
                        "idx_order_delivery_ticket_delivery_date",
                        "ON order_delivery_ticket USING btree (delivery_date, id)"
                )
                .doesNotContain(
                        "idx_order_delivery_ticket_customer_order_no",
                        "idx_order_delivery_ticket_customer_project_no"
                );
        assertThat(optimizationMigration)
                .as("Delivered items now support direct part number + version filtering")
                .contains(
                        "idx_order_delivery_ticket_part_version",
                        "ON order_delivery_ticket USING btree (part_no, version, id)"
                );
        assertThat(activePoolMigration)
                .as("Default delivered-item page is an indexed active pool, not a table move")
                .contains(
                        "idx_order_delivery_ticket_active_flow_id",
                        "ON order_delivery_ticket USING btree (id DESC)",
                        "NULLIF(btrim((material_no)::text), ''::text) IS NULL",
                        "COALESCE(mes_sync_status, 'PENDING'::character varying))::text <> 'SUCCESS'::text",
                        "COALESCE(wms_allocate_status, 'PENDING'::character varying))::text <> 'SUCCESS'::text",
                        "idx_order_delivery_ticket_completed_flow_id",
                        "ON order_delivery_ticket USING btree (mes_sync_status, wms_allocate_status, id DESC)",
                        "idx_delivery_allocation_ticket",
                        "ON order_delivery_ticket_allocation USING btree (ticket_id, id)"
                )
                .doesNotContain(
                        "CREATE TABLE delivered_item_history",
                        "DELETE FROM order_delivery_ticket",
                        "INSERT INTO delivered_item_history"
                );
        assertThat(indexCleanupMigration)
                .as("Historical RFQ indexes should be normalized to one canonical paging/detail shape")
                .contains(
                        "CREATE INDEX idx_order_delivery_ticket_rfq_item_id",
                        "ON order_delivery_ticket USING btree (rfq_id, item_id)"
                )
                .doesNotContain(
                        "idx_order_delivery_ticket_rfq_item ON order_delivery_ticket",
                        "idx_order_delivery_ticket_customer_name",
                        "idx_delivery_allocation_customer_name"
                );
        assertThat(schema)
                .as("Fresh schema should include delivered/locked item list indexes that Flyway adds for upgraded databases")
                .contains(
                        "idx_order_delivery_ticket_material_no",
                        "ON order_delivery_ticket USING btree (material_no, id)",
                        "idx_order_delivery_ticket_mes_status",
                        "ON order_delivery_ticket USING btree (mes_sync_status, id)",
                        "idx_order_delivery_ticket_wms_status",
                        "ON order_delivery_ticket USING btree (wms_allocate_status, id)",
                        "idx_order_delivery_ticket_internal_project",
                        "ON order_delivery_ticket USING btree (internal_project_no, id)",
                        "idx_order_delivery_ticket_rfq_no",
                        "ON order_delivery_ticket USING btree (rfq_no, id)",
                        "idx_order_delivery_ticket_delivery_date",
                        "ON order_delivery_ticket USING btree (delivery_date, id)",
                        "idx_order_delivery_ticket_active_flow_id",
                        "idx_order_delivery_ticket_completed_flow_id",
                        "idx_delivery_allocation_ticket",
                        "ON order_delivery_ticket_allocation USING btree (ticket_id, id)",
                        "idx_delivery_allocation_material",
                        "ON order_delivery_ticket_allocation USING btree (material_no, id)",
                        "idx_delivery_allocation_part_version",
                        "ON order_delivery_ticket_allocation USING btree (part_no, version, id)",
                        "idx_delivery_allocation_type",
                        "ON order_delivery_ticket_allocation USING btree (allocation_type, id)",
                        "idx_delivery_allocation_rfq_no",
                        "ON order_delivery_ticket_allocation USING btree (rfq_no, id)",
                        "idx_delivery_allocation_delivery_date",
                        "ON order_delivery_ticket_allocation USING btree (delivery_date, id)"
                )
                .doesNotContain(
                        "idx_order_delivery_ticket_customer_name",
                        "idx_delivery_allocation_customer_name"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
