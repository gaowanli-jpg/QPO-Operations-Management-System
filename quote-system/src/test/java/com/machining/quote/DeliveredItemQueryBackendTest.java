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
                        "item.drawing_url AS drawing_url"
                );
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
                        "return deliveredItemService.getTicket(ticketId)"
                )
                .doesNotContain("ticketMapper.selectPage")
                .doesNotContain("new QueryWrapper<OrderDeliveryTicket>");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
