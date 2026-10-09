package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.SalesOrderWmsAllocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderWmsAllocationServiceTest {
    @Test
    void resolvesEveryOrderTicketAndEligibleRowsWithSkipReasons() {
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        SalesOrderHeaderMapper orderMapper = mock(SalesOrderHeaderMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        WmsReservationTicketMapper reservationMapper = mock(WmsReservationTicketMapper.class);
        SalesOrderWmsAllocationService service = new SalesOrderWmsAllocationService();
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "wmsReservationTicketMapper", reservationMapper);

        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(77L);
        when(orderMapper.selectById(77L)).thenReturn(order);
        SalesOrderItem linkedItem = new SalesOrderItem();
        linkedItem.setSourceTicketId(105);
        when(itemMapper.selectList(any())).thenReturn(List.of(linkedItem));

        OrderDeliveryTicket eligibleDirect = ticket(101, "SUCCESS", "MAT-101", "PENDING", false);
        eligibleDirect.setSourceType("SALES_ORDER");
        eligibleDirect.setSourceOrderId(77L);
        OrderDeliveryTicket eligibleRfqDerived = ticket(105, "SUCCESS", "MAT-105", "FAILED", false);
        OrderDeliveryTicket alreadyAllocated = ticket(102, "SUCCESS", "MAT-102", "SUCCESS", false);
        OrderDeliveryTicket missingMaterial = ticket(103, "FAILED", null, "PENDING", false);
        OrderDeliveryTicket discarded = ticket(104, "SUCCESS", "MAT-104", "PENDING", true);
        OrderDeliveryTicket processing = ticket(106, "SUCCESS", "MAT-106", "PROCESSING", false);
        when(reservationMapper.selectActiveByTicketId(106)).thenReturn(new WmsReservationTicket());
        when(ticketMapper.selectList(any())).thenReturn(List.of(
                eligibleDirect, eligibleRfqDerived, alreadyAllocated, missingMaterial, discarded, processing));
        when(allocationMapper.selectByTicketId(101)).thenReturn(List.of());
        when(allocationMapper.selectByTicketId(105)).thenReturn(List.of());
        when(allocationMapper.selectByTicketId(102)).thenReturn(List.of());
        when(allocationMapper.selectByTicketId(103)).thenReturn(List.of());
        when(allocationMapper.selectByTicketId(104)).thenReturn(List.of());
        when(allocationMapper.selectByTicketId(106)).thenReturn(List.of());

        assertThat(service.resolveTicketIds(77L)).containsExactly(101, 105, 102, 103, 104, 106);
        assertThat(service.resolveEligibleTicketIds(77L)).containsExactly(101, 105);
        assertThat(service.resolveSkippedReasons(77L))
                .containsExactly("提单行102：已获取过库存信息", "提单行103：物料号尚未获取成功",
                        "提单行104：已废弃，不允许获取库存信息", "提单行106：库存信息正在处理中或等待核对");
    }

    @Test
    void skipsWmsWhenMesProductionHasStarted() {
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        SalesOrderHeaderMapper orderMapper = mock(SalesOrderHeaderMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        WmsReservationTicketMapper reservationMapper = mock(WmsReservationTicketMapper.class);
        SalesOrderWmsAllocationService service = new SalesOrderWmsAllocationService();
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "wmsReservationTicketMapper", reservationMapper);
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(77L);
        when(orderMapper.selectById(77L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(ticketMapper.selectList(any())).thenReturn(List.of(ticket(101, "SUCCESS", "MAT-101", "PENDING", false)));
        com.machining.quote.entity.OrderDeliveryTicketAllocation allocation = new com.machining.quote.entity.OrderDeliveryTicketAllocation();
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setStatus("ACTIVE");
        allocation.setMesProductionStatus("RESULT_UNKNOWN");
        when(allocationMapper.selectByTicketId(101)).thenReturn(List.of(allocation));

        assertThat(service.resolveEligibleTicketIds(77L)).isEmpty();
        assertThat(service.resolveSkippedReasons(77L)).containsExactly("提单行101：MES生产已开始或完成，不能再获取库存信息");
    }

    private OrderDeliveryTicket ticket(Integer id, String mesStatus, String materialNo, String wmsStatus, boolean discarded) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setMesSyncStatus(mesStatus);
        ticket.setMaterialNo(materialNo);
        ticket.setWmsAllocateStatus(wmsStatus);
        ticket.setDiscarded(discarded);
        return ticket;
    }
}
