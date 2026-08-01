package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderManageService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderManagementStage6RegressionTest {
    private SalesOrderHeaderMapper headerMapper;
    private SalesOrderItemMapper itemMapper;
    private OrderDeliveryTicketMapper ticketMapper;
    private DataScopeService dataScopeService;
    private AuditService auditService;
    private BusinessFileService businessFileService;
    private SalesOrderManageService service;

    @BeforeEach
    void setUp() {
        headerMapper = mock(SalesOrderHeaderMapper.class);
        itemMapper = mock(SalesOrderItemMapper.class);
        ticketMapper = mock(OrderDeliveryTicketMapper.class);
        dataScopeService = mock(DataScopeService.class);
        auditService = mock(AuditService.class);
        businessFileService = mock(BusinessFileService.class);
        service = new SalesOrderManageService();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
    }

    @Test
    void listOrdersAppliesSalesOrderDataScopeBeforeReturningPagedRows() {
        doAnswer(invocation -> {
            QueryWrapper<SalesOrderHeader> query = invocation.getArgument(0);
            query.apply("1 = 0");
            return null;
        }).when(dataScopeService).applySalesOrderScope(any(QueryWrapper.class));
        when(headerMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        PageResult<Map<String, Object>> result = service.listOrderSummaries(
                88L,
                "ACME",
                "SO-1001",
                QuoteStatusWorkflow.QUOTED,
                2,
                500);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<SalesOrderHeader>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(dataScopeService).applySalesOrderScope(queryCaptor.capture());
        verify(headerMapper).selectCount(queryCaptor.getValue());
        verify(headerMapper, never()).selectList(any(QueryWrapper.class));
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("1 = 0")
                .contains("customer_id")
                .contains("customer_name")
                .contains("status");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(88L, "ACME", "%SO-1001%", QuoteStatusWorkflow.QUOTED);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    void ticketAndProcessSheetPagesOnlyQuerySalesOrderSourceRows() {
        SalesOrderHeader order = salesOrder(501L, QuoteStatusWorkflow.RELEASED);
        OrderDeliveryTicket ticket = ticket(9001, 501L, "SO-PART-1");
        when(headerMapper.selectById(501L)).thenReturn(order);
        when(ticketMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(ticket));

        PageResult<OrderDeliveryTicket> ticketPage = service.pageOrderTickets(501L, 3, 20);
        PageResult<?> processSheetPage = service.pageDynamicProcessSheets(501L, 3, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<OrderDeliveryTicket>> ticketQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(ticketMapper, org.mockito.Mockito.times(2)).selectCount(ticketQueryCaptor.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<OrderDeliveryTicket>> ticketPageQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(ticketMapper, org.mockito.Mockito.times(2)).selectList(ticketPageQueryCaptor.capture());
        assertThat(ticketPage.getRecords()).containsExactly(ticket);
        assertThat(processSheetPage.getTotal()).isEqualTo(1);
        assertThat(ticketQueryCaptor.getAllValues())
                .allSatisfy(query -> {
                    assertThat(query.getSqlSegment()).contains("source_type").contains("source_order_id");
                    assertThat(query.getSqlSegment()).doesNotContain("rfq_id");
                    assertThat(query.getParamNameValuePairs().values())
                            .contains(ReleaseSourceType.SALES_ORDER.name(), 501L);
                });
        assertThat(ticketPageQueryCaptor.getAllValues())
                .allSatisfy(query -> assertThat(query.getSqlSegment()).contains("LIMIT 20 OFFSET 40"));
        verify(dataScopeService, org.mockito.Mockito.times(2)).assertCanAccessSalesOrder(order);
    }

    @Test
    void quotedSalesOrderCanBeDiscardedAndDiscardedSalesOrderCanBeRestored() {
        SalesOrderHeader quoted = salesOrder(601L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem quotedItem = item(7001L, 601L, QuoteStatusWorkflow.QUOTED, 0);
        when(headerMapper.selectByIdForUpdate(601L)).thenReturn(quoted);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(quotedItem));
        when(ticketMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        assertThat(service.discard(601L, reason("customer cancelled")).getToStatus())
                .isEqualTo(QuoteStatusWorkflow.DISCARDED);
        verify(headerMapper).updateById(org.mockito.Mockito.argThat(header ->
                QuoteStatusWorkflow.DISCARDED.equals(header.getStatus())));
        verify(itemMapper).updateById(org.mockito.Mockito.argThat(item ->
                QuoteStatusWorkflow.DISCARDED.equals(item.getStatus())));
        verify(auditService).record(eq("SALES_ORDER_DISCARD"), eq("sales_order_header"), eq(601L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());

        SalesOrderHeader discarded = salesOrder(602L, QuoteStatusWorkflow.DISCARDED);
        SalesOrderItem discardedItem = item(7002L, 602L, QuoteStatusWorkflow.DISCARDED, 0);
        when(headerMapper.selectByIdForUpdate(602L)).thenReturn(discarded);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(discardedItem));

        assertThat(service.restore(602L, reason("resume")).getToStatus())
                .isEqualTo(QuoteStatusWorkflow.QUOTED);
        verify(headerMapper).updateById(org.mockito.Mockito.argThat(header ->
                QuoteStatusWorkflow.QUOTED.equals(header.getStatus())));
        verify(itemMapper).updateById(org.mockito.Mockito.argThat(item ->
                QuoteStatusWorkflow.QUOTED.equals(item.getStatus())));
        verify(auditService).record(eq("SALES_ORDER_RESTORE"), eq("sales_order_header"), eq(602L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void releasedSalesOrderStatusesCannotBeDiscarded() {
        for (String status : List.of(QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED)) {
            SalesOrderHeader order = salesOrder(701L, status);
            when(headerMapper.selectByIdForUpdate(701L)).thenReturn(order);

            assertThatThrownBy(() -> service.discard(701L, reason("blocked")))
                    .isInstanceOf(RuntimeException.class);
        }
        verify(headerMapper, never()).updateById(any());
        verify(itemMapper, never()).updateById(any());
    }

    @Test
    void supplementDrawingUpdatesOnlySalesOrderItemSnapshot() {
        SalesOrderHeader order = salesOrder(801L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem item = item(8801L, 801L, QuoteStatusWorkflow.QUOTED, 0);
        item.setPartNo("SO-DRAW-1");
        when(itemMapper.selectByIdForUpdate(8801L)).thenReturn(item);
        when(headerMapper.selectById(801L)).thenReturn(order);
        when(businessFileService.saveSalesOrderImportDrawing(eq("ACME"), eq("SO-801"), any(), eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("8801")))
                .thenReturn(new BusinessFileService.StoredBusinessFile(
                        "/uploads/sales-orders/SO-801/SO-DRAW-1.pdf",
                        StorageType.LOCAL,
                        1L,
                        "sales-orders/SO-801/SO-DRAW-1.pdf",
                        "SO-DRAW-1.pdf",
                        "application/pdf",
                        12L,
                        null));

        Map<String, Object> result = service.supplementItemDrawings(
                8801L,
                List.of(new MockMultipartFile("drawings", "SO-DRAW-1.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8))));

        assertThat(result).containsEntry("boundCount", 1);
        verify(itemMapper).updateById(org.mockito.Mockito.argThat(updated ->
                Boolean.TRUE.equals(updated.getHasDrawing())
                        && "/uploads/sales-orders/SO-801/SO-DRAW-1.pdf".equals(updated.getDrawingUrl())));
        verify(auditService).record(eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("sales_order_item"), eq(8801L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void missingSalesOrderDataAccessStopsDetailQueriesBeforeDownstreamReads() {
        SalesOrderHeader order = salesOrder(901L, QuoteStatusWorkflow.QUOTED);
        when(headerMapper.selectById(901L)).thenReturn(order);
        org.mockito.Mockito.doThrow(new RuntimeException("No data permission for sales order 901"))
                .when(dataScopeService).assertCanAccessSalesOrder(order);

        assertThatThrownBy(() -> service.pageOrderItems(901L, 1, 20))
                .hasMessageContaining("No data permission");

        verifyNoInteractions(itemMapper);
        verifyNoInteractions(ticketMapper);
    }

    private SalesOrderHeader salesOrder(Long id, String status) {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(id);
        order.setSalesOrderNo("SO-" + id);
        order.setCustomerId(88L);
        order.setCustomerName("ACME");
        order.setStatus(status);
        return order;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String status, int releasedQty) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setQty(10);
        item.setReleasedQty(releasedQty);
        item.setStatus(status);
        return item;
    }

    private OrderDeliveryTicket ticket(Integer id, Long salesOrderId, String partNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType(ReleaseSourceType.SALES_ORDER.name());
        ticket.setSourceOrderId(salesOrderId);
        ticket.setPartNo(partNo);
        ticket.setVersion("A");
        ticket.setQty(3);
        return ticket;
    }

    private OrderStatusChangeReq reason(String reason) {
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(reason);
        return req;
    }
}
