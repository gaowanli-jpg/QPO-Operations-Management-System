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
            "src", "main", "resources", "db", "migration", "V51__delivery_common_query_indexes.sql");
    private static final Path QUERY_OPTIMIZATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V52__delivery_part_version_and_exact_customer_filters.sql");

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
                        "item.drawing_url AS drawing_url"
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
                        "dataScopeService.applyRfqScope(query, \"ticket.rfq_id\")",
                        "countDeliveredItems",
                        "return PageResult.of(List.of(), 0, safePage, safePageSize)",
                        "selectDeliveredItems",
                        "dataScopeService.assertCanAccessOrder(item.getRfqId(), orderRfqMapper)"
                );
        assertThat(source)
                .as("Common delivered-item filters must cover customer order/project numbers and delivery date")
                .contains(
                        "applyEquals(query, \"ticket.customer_name\", req.getCustomerName())",
                        "applyLike(query, \"ticket.customer_order_no\", req.getCustomerOrderNo())",
                        "applyLike(query, \"ticket.customer_project_no\", req.getCustomerProjectNo())",
                        "query.ge(\"ticket.delivery_date\", req.getDateFrom())",
                        "query.le(\"ticket.delivery_date\", req.getDateTo())",
                        "listCustomers"
                );
        assertThat(source)
                .as("Customer picker passes exact customer names, so the delivered-item query should not use fuzzy customer matching")
                .doesNotContain("applyLike(query, \"ticket.customer_name\", req.getCustomerName())");
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
                        "return deliveredItemService.listCustomers()"
                )
                .doesNotContain("ticketMapper.selectPage")
                .doesNotContain("new QueryWrapper<OrderDeliveryTicket>");
    }

    @Test
    void deliveredItemCommonQueryIndexesStayFocusedOnRecommendedFields() throws IOException {
        String migration = read(QUERY_INDEX_MIGRATION);
        String optimizationMigration = read(QUERY_OPTIMIZATION_MIGRATION);

        assertThat(migration)
                .contains(
                        "idx_order_delivery_ticket_rfq_no",
                        "ON order_delivery_ticket(rfq_no, id)",
                        "idx_order_delivery_ticket_delivery_date",
                        "ON order_delivery_ticket(delivery_date, id)"
                )
                .doesNotContain(
                        "idx_order_delivery_ticket_customer_order_no",
                        "idx_order_delivery_ticket_customer_project_no"
                );
        assertThat(optimizationMigration)
                .as("Delivered items now support direct part number + version filtering")
                .contains(
                        "idx_order_delivery_ticket_part_version",
                        "ON order_delivery_ticket(part_no, version, id)"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
