package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.SalesOrderLinkedDiscardBatchResult;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderLinkedDiscardService;
import com.machining.quote.service.SalesOrderManageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SalesOrderLinkedDiscardBatchServiceTest {
    @Test
    void discardsWholeOrderWithOneBatchLoadPerDownstreamTableAndAllowsSyncedU9() {
        SalesOrderHeader order = order(100L, "已生成提单", "已同步");
        SalesOrderItem first = item(201L, order.getId(), 1, false, false, "已生成提单");
        SalesOrderItem second = item(202L, order.getId(), 2, false, true, "已生成提单");
        OrderDeliveryTicket activeTicket = ticket(301, order.getId(), 201L, "FAILED", "PENDING", false);
        OrderDeliveryTicketAllocation production = allocation(401, 301, order.getId(), 201L, "PRODUCTION_REQUIRED", "PENDING", null, false);
        Fixture fixture = fixture(order, List.of(first, second), List.of(activeTicket), List.of(production));

        SalesOrderLinkedDiscardBatchResult result = fixture.service.discardOrder(order.getId(), reason("整单关联废弃"));

        assertThat(result.isLinkedDiscarded()).isTrue();
        assertThat(result.getTotalItemCount()).isEqualTo(2);
        assertThat(result.getProcessedItemCount()).isEqualTo(2);
        assertThat(result.getDeliveredItemCount()).isEqualTo(1);
        assertThat(result.getProductionItemCount()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo("废弃");
        assertThat(order.getLinkedDiscarded()).isTrue();
        verify(fixture.itemMapper).selectBySalesOrderIdForUpdate(order.getId());
        verify(fixture.ticketMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(fixture.allocationMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(fixture.itemMapper).markItemsU9ChangeExcluded(eq(order.getId()), any(), eq("整单关联废弃"), isNull(), any());
        verify(fixture.itemMapper).markItemsLinkedDiscarded(eq(order.getId()), any(), any(), eq("整单关联废弃"), isNull(), any());
        verify(fixture.auditService).record(eq("SALES_ORDER_LINKED_DISCARD"), eq("sales_order_header"), eq(order.getId()), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void restoresLinkedDownstreamRowsAfterOrdinaryOrderRestoreWithoutRestoringManualRows() {
        SalesOrderHeader order = order(101L, "已生成提单", "已同步");
        order.setLinkedDiscarded(true);
        SalesOrderItem item = item(211L, order.getId(), 1, true, true, "已生成提单");
        OrderDeliveryTicket linkedTicket = ticket(311, order.getId(), item.getId(), "FAILED", "PENDING", true);
        linkedTicket.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedTicket.setDiscardSourceSalesOrderItemId(item.getId());
        OrderDeliveryTicket manualTicket = ticket(312, order.getId(), item.getId(), "FAILED", "PENDING", true);
        manualTicket.setDiscardSource("MANUAL");
        OrderDeliveryTicketAllocation linkedAllocation = allocation(411, 311, order.getId(), item.getId(), "IN_PRODUCTION_LOCK", null, "FAILED", true);
        linkedAllocation.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedAllocation.setDiscardSourceSalesOrderItemId(item.getId());
        Fixture fixture = fixture(order, List.of(item), List.of(linkedTicket, manualTicket), List.of(linkedAllocation));

        SalesOrderLinkedDiscardBatchResult result = fixture.service.restoreOrder(order.getId(), reason("整单关联恢复"));

        assertThat(result.isLinkedDiscarded()).isFalse();
        assertThat(result.getProcessedItemCount()).isEqualTo(1);
        assertThat(order.getLinkedDiscarded()).isFalse();
        verify(fixture.itemMapper).restoreItemsU9ChangeExcluded(eq(order.getId()), any(), eq("整单关联恢复"), isNull(), any());
        verify(fixture.itemMapper).restoreItemsLinkedDiscarded(eq(order.getId()), any(), eq("整单关联恢复"), isNull(), any());
        verify(fixture.ticketMapper).restoreLinkedDiscarded(any(), eq(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE), eq("整单关联恢复"), isNull(), any());
        verify(fixture.allocationMapper).restoreLinkedDiscarded(any(), eq(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE), eq("整单关联恢复"), isNull(), any());
        verify(fixture.auditService).record(eq("SALES_ORDER_LINKED_RESTORE"), eq("sales_order_header"), eq(order.getId()), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    private Fixture fixture(
            SalesOrderHeader order,
            List<SalesOrderItem> items,
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations) {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        WmsReservationTicketMapper wmsReservationTicketMapper = mock(WmsReservationTicketMapper.class);
        WmsReservationBatchMapper wmsReservationBatchMapper = mock(WmsReservationBatchMapper.class);
        SalesOrderManageService salesOrderManageService = mock(SalesOrderManageService.class);
        AuditService auditService = mock(AuditService.class);

        when(headerMapper.selectByIdForUpdate(order.getId())).thenReturn(order);
        when(itemMapper.selectBySalesOrderIdForUpdate(order.getId())).thenReturn(items);
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(tickets);
        when(allocationMapper.selectList(any(QueryWrapper.class))).thenReturn(allocations);
        when(itemMapper.markItemsU9ChangeExcluded(any(), any(), any(), any(), any())).thenReturn(1);
        when(itemMapper.markItemsLinkedDiscarded(any(), any(), any(), any(), any(), any())).thenReturn(items.size());
        when(itemMapper.restoreItemsU9ChangeExcluded(any(), any(), any(), any(), any())).thenReturn(1);
        when(itemMapper.restoreItemsLinkedDiscarded(any(), any(), any(), any(), any())).thenReturn(items.size());
        when(itemMapper.markOrderItemsStatus(any(), any(), any())).thenReturn((int) items.stream().filter(item -> !"废弃".equals(item.getStatus())).count());
        when(itemMapper.restoreOrderItemsStatus(any(), any(), any(), any())).thenReturn((int) items.stream().filter(item -> "废弃".equals(item.getStatus())).count());
        when(ticketMapper.markLinkedDiscarded(any(), any(), any(), any(), any())).thenReturn(1);
        when(allocationMapper.markLinkedDiscarded(any(), any(), any(), any(), any())).thenReturn(1);
        when(ticketMapper.restoreLinkedDiscarded(any(), any(), any(), any(), any())).thenReturn(1);
        when(allocationMapper.restoreLinkedDiscarded(any(), any(), any(), any(), any())).thenReturn(1);
        when(headerMapper.updateById(any(SalesOrderHeader.class))).thenReturn(1);

        SalesOrderLinkedDiscardService service = new SalesOrderLinkedDiscardService();
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "wmsReservationTicketMapper", wmsReservationTicketMapper);
        ReflectionTestUtils.setField(service, "wmsReservationBatchMapper", wmsReservationBatchMapper);
        ReflectionTestUtils.setField(service, "salesOrderManageService", salesOrderManageService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return new Fixture(service, headerMapper, itemMapper, ticketMapper, allocationMapper,
                wmsReservationTicketMapper, wmsReservationBatchMapper, auditService);
    }

    private SalesOrderHeader order(Long id, String status, String u9Status) {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(id);
        order.setSalesOrderNo("SO-" + id);
        order.setStatus(status);
        order.setU9SyncStatus(u9Status);
        return order;
    }

    private SalesOrderItem item(Long id, Long orderId, int lineNo, boolean linked, boolean u9Excluded, String status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(orderId);
        item.setLineNo(lineNo);
        item.setStatus(status);
        item.setLinkedDiscarded(linked);
        item.setLinkedU9ChangeExcluded(linked && u9Excluded);
        item.setU9ChangeExcluded(u9Excluded);
        return item;
    }

    private OrderDeliveryTicket ticket(int id, Long orderId, Long itemId, String mesStatus, String wmsStatus, boolean discarded) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(orderId);
        ticket.setSourceItemId(itemId);
        ticket.setMaterialNo("");
        ticket.setMesSyncStatus(mesStatus);
        ticket.setWmsAllocateStatus(wmsStatus);
        ticket.setDiscarded(discarded);
        return ticket;
    }

    private OrderDeliveryTicketAllocation allocation(
            int id,
            int ticketId,
            Long orderId,
            Long itemId,
            String type,
            String productionStatus,
            String stockStatus,
            boolean discarded) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(ticketId);
        allocation.setSourceType("SALES_ORDER");
        allocation.setSourceOrderId(orderId);
        allocation.setSourceItemId(itemId);
        allocation.setAllocationType(type);
        allocation.setMesProductionStatus(productionStatus);
        allocation.setStockPickingStatus(stockStatus);
        allocation.setDiscarded(discarded);
        return allocation;
    }

    private OrderStatusChangeReq reason(String value) {
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(value);
        return req;
    }

    private record Fixture(
            SalesOrderLinkedDiscardService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            WmsReservationTicketMapper wmsReservationTicketMapper,
            WmsReservationBatchMapper wmsReservationBatchMapper,
            AuditService auditService) {
    }
}
