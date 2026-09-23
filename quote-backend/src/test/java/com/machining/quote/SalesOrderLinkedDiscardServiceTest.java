package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.SalesOrderLinkedDiscardResult;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderLinkedDiscardService;
import com.machining.quote.service.SalesOrderManageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SalesOrderLinkedDiscardServiceTest {
    @Test
    void discardsOnlyActiveRowsRelatedToTheRequestedSalesOrderItem() {
        SalesOrderHeader order = order(100L, "未同步");
        SalesOrderItem item = item(200L, 100L, 10);
        item.setSourceTicketId(11);

        OrderDeliveryTicket activeTicket = ticket(11, "M-11", "FAILED", "PENDING", false);
        OrderDeliveryTicket completedTicket = ticket(12, "M-12", "SUCCESS", "SUCCESS", false);
        OrderDeliveryTicketAllocation activeProduction = allocation(21, 11, "PRODUCTION_REQUIRED", "PENDING", null, false);
        OrderDeliveryTicketAllocation activeLocked = allocation(22, 11, "IN_STOCK_LOCK", null, "FAILED", false);
        OrderDeliveryTicketAllocation completedLocked = allocation(23, 12, "IN_STOCK_LOCK", null, "SUCCESS", false);
        Fixture fixture = fixture(order, item, List.of(activeTicket, completedTicket),
                List.of(activeProduction, activeLocked, completedLocked));

        SalesOrderLinkedDiscardResult result = fixture.service.discard(100L, 200L, reason("同步废弃"));

        assertThat(result.isLinkedDiscarded()).isTrue();
        assertThat(result.getDeliveredItemCount()).isEqualTo(1);
        assertThat(result.getProductionItemCount()).isEqualTo(1);
        assertThat(result.getLockedItemCount()).isEqualTo(1);
        assertThat(result.getSkippedCompletedCount()).isEqualTo(2);
        assertThat(activeTicket.getDiscardSource()).isEqualTo(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        assertThat(activeProduction.getDiscardSource()).isEqualTo(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        assertThat(activeLocked.getDiscardSource()).isEqualTo(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        assertThat(completedTicket.getDiscarded()).isFalse();
        assertThat(completedLocked.getDiscarded()).isFalse();
        verify(fixture.salesOrderManageService).discardItemForU9Change(eq(100L), eq(200L), any(OrderStatusChangeReq.class));
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.auditService).record(eq("SALES_ORDER_ITEM_LINKED_DISCARD"), eq("sales_order_item"), eq(200L),
                isNull(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void restoreOnlyReversesLinkedActiveRowsAndLeavesManualRowsUntouched() {
        SalesOrderHeader order = order(101L, "已同步");
        SalesOrderItem item = item(201L, 101L, 10);
        item.setLinkedDiscarded(true);
        item.setLinkedU9ChangeExcluded(true);
        item.setU9ChangeExcluded(true);

        OrderDeliveryTicket linkedActive = ticket(31, "M-31", "FAILED", "PENDING", true);
        linkedActive.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedActive.setDiscardSourceSalesOrderItemId(201L);
        OrderDeliveryTicket manualTicket = ticket(32, "M-32", "FAILED", "PENDING", true);
        manualTicket.setDiscardSource("MANUAL");
        OrderDeliveryTicket linkedCompleted = ticket(33, "M-33", "SUCCESS", "SUCCESS", true);
        linkedCompleted.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedCompleted.setDiscardSourceSalesOrderItemId(201L);
        OrderDeliveryTicketAllocation linkedActiveAllocation = allocation(41, 31, "PRODUCTION_REQUIRED", "FAILED", null, true);
        linkedActiveAllocation.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedActiveAllocation.setDiscardSourceSalesOrderItemId(201L);
        OrderDeliveryTicketAllocation linkedCompletedAllocation = allocation(42, 33, "IN_STOCK_LOCK", null, "SUCCESS", true);
        linkedCompletedAllocation.setDiscardSource(SalesOrderLinkedDiscardService.LINKED_DISCARD_SOURCE);
        linkedCompletedAllocation.setDiscardSourceSalesOrderItemId(201L);
        OrderDeliveryTicketAllocation manualAllocation = allocation(43, 31, "IN_PRODUCTION_LOCK", null, "FAILED", true);
        manualAllocation.setDiscardSource("MANUAL");
        Fixture fixture = fixture(order, item, List.of(linkedActive, manualTicket, linkedCompleted),
                List.of(linkedActiveAllocation, linkedCompletedAllocation, manualAllocation));

        SalesOrderLinkedDiscardResult result = fixture.service.restore(101L, 201L, reason("恢复工作池"));

        assertThat(result.isLinkedDiscarded()).isFalse();
        assertThat(result.getDeliveredItemCount()).isEqualTo(1);
        assertThat(result.getProductionItemCount()).isEqualTo(1);
        assertThat(result.getLockedItemCount()).isEqualTo(0);
        assertThat(result.getSkippedCompletedCount()).isEqualTo(2);
        assertThat(result.getSkippedAlreadyDiscardedCount()).isEqualTo(2);
        assertThat(linkedActive.getDiscarded()).isFalse();
        assertThat(linkedActive.getDiscardSource()).isNull();
        assertThat(linkedActiveAllocation.getDiscarded()).isFalse();
        assertThat(linkedActiveAllocation.getDiscardSource()).isNull();
        assertThat(manualTicket.getDiscarded()).isTrue();
        assertThat(manualAllocation.getDiscarded()).isTrue();
        assertThat(linkedCompleted.getDiscarded()).isTrue();
        assertThat(linkedCompletedAllocation.getDiscarded()).isTrue();
        verify(fixture.salesOrderManageService).restoreItemForU9Change(eq(101L), eq(201L), any(OrderStatusChangeReq.class));
        verify(fixture.auditService).record(eq("SALES_ORDER_ITEM_LINKED_RESTORE"), eq("sales_order_item"), eq(201L),
                isNull(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void callbackPendingActiveRowsBlockTheWholeTransaction() {
        SalesOrderHeader order = order(102L, "未同步");
        SalesOrderItem item = item(202L, 102L, 10);
        OrderDeliveryTicket pendingTicket = ticket(51, "", "CALLBACK_PENDING", "PENDING", false);
        Fixture fixture = fixture(order, item, List.of(pendingTicket), List.of());

        assertThatThrownBy(() -> fixture.service.discard(102L, 202L, reason("阻断回调中")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("待回调");

        verify(fixture.salesOrderManageService, never()).discardItemForU9Change(any(), any(), any());
        verify(fixture.itemMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void runningU9StatusesAreRejectedBeforeDownstreamWrites() {
        SalesOrderHeader order = order(103L, "同步中");
        SalesOrderItem item = item(203L, 103L, 10);
        Fixture fixture = fixture(order, item, List.of(), List.of());

        assertThatThrownBy(() -> fixture.service.discard(103L, 203L, reason("暂缓")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能关联废弃或恢复");

        verify(fixture.ticketMapper, never()).selectList(any(QueryWrapper.class));
        verify(fixture.salesOrderManageService, never()).discardItemForU9Change(any(), any(), any());
    }

    private Fixture fixture(
            SalesOrderHeader order,
            SalesOrderItem item,
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations) {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        SalesOrderManageService salesOrderManageService = mock(SalesOrderManageService.class);
        AuditService auditService = mock(AuditService.class);

        when(headerMapper.selectByIdForUpdate(order.getId())).thenReturn(order);
        when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(tickets);
        for (OrderDeliveryTicket ticket : tickets) {
            when(ticketMapper.selectByIdForUpdate(ticket.getId())).thenReturn(ticket);
        }
        when(allocationMapper.selectList(any(QueryWrapper.class))).thenReturn(allocations);
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            when(allocationMapper.selectByIdForUpdate(allocation.getId())).thenReturn(allocation);
        }
        when(ticketMapper.updateById(any(OrderDeliveryTicket.class))).thenReturn(1);
        when(allocationMapper.updateById(any(OrderDeliveryTicketAllocation.class))).thenReturn(1);
        when(itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        SalesOrderLinkedDiscardService service = new SalesOrderLinkedDiscardService();
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "salesOrderManageService", salesOrderManageService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return new Fixture(service, headerMapper, itemMapper, ticketMapper, allocationMapper,
                salesOrderManageService, auditService);
    }

    private OrderStatusChangeReq reason(String value) {
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(value);
        return req;
    }

    private SalesOrderHeader order(Long id, String u9Status) {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(id);
        order.setSalesOrderNo("SO-" + id);
        order.setU9SyncStatus(u9Status);
        return order;
    }

    private SalesOrderItem item(Long id, Long orderId, int lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(orderId);
        item.setLineNo(lineNo);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        return item;
    }

    private OrderDeliveryTicket ticket(
            int id,
            String materialNo,
            String mesStatus,
            String wmsStatus,
            boolean discarded) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(100L);
        ticket.setSourceItemId(200L);
        ticket.setMaterialNo(materialNo);
        ticket.setMesSyncStatus(mesStatus);
        ticket.setWmsAllocateStatus(wmsStatus);
        ticket.setDiscarded(discarded);
        return ticket;
    }

    private OrderDeliveryTicketAllocation allocation(
            int id,
            int ticketId,
            String type,
            String productionStatus,
            String stockStatus,
            boolean discarded) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(ticketId);
        allocation.setAllocationType(type);
        allocation.setMesProductionStatus(productionStatus);
        allocation.setStockPickingStatus(stockStatus);
        allocation.setDiscarded(discarded);
        return allocation;
    }

    private record Fixture(
            SalesOrderLinkedDiscardService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            SalesOrderManageService salesOrderManageService,
            AuditService auditService) {
    }
}
