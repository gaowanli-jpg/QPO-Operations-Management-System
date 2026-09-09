package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.LeadTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryMaterialNoInheritanceStage4Test {

    @Test
    void salesOrderTicketDoesNotInheritConfirmedMaterialNoAndStartsMesPending() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        List<OrderDeliveryTicket> inserted = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        SalesOrderItem item = salesOrderItem(3001L, "SO-MES-001", "已确认");
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 7, 11, 15);
        item.setMaterialNoSyncedAt(syncedAt);

        when(headerMapper.selectByIdForUpdate(1101L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(ticketMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(inserted));
        captureInsertedTicket(ticketMapper, inserted, 8101);
        wireSalesOrder(service, headerMapper, itemMapper, ticketMapper);

        OrderDeliveryTicket ticket = service.generateImportedSalesOrderTickets(1101L).get(0);

        assertThat(ticket.getMaterialNo()).isNull();
        assertThat(ticket.getMesSyncStatus()).isEqualTo("PENDING");
        assertThat(ticket.getMesSyncMessage()).isNull();
        assertThat(ticket.getMesSyncedAt()).isNull();
    }

    @Test
    void salesOrderTicketLeavesUnconfirmedSourcePending() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        List<OrderDeliveryTicket> inserted = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        SalesOrderItem item = salesOrderItem(3002L, "SO-UNCONFIRMED", "未确认");

        when(headerMapper.selectByIdForUpdate(1101L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(ticketMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(inserted));
        captureInsertedTicket(ticketMapper, inserted, 8102);
        wireSalesOrder(service, headerMapper, itemMapper, ticketMapper);

        OrderDeliveryTicket ticket = service.generateImportedSalesOrderTickets(1101L).get(0);

        assertThat(ticket.getMaterialNo()).isNull();
        assertThat(ticket.getMesSyncStatus()).isEqualTo("PENDING");
    }

    private void wireSalesOrder(
            DeliveryService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper) {
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
    }

    private void captureInsertedTicket(
            OrderDeliveryTicketMapper ticketMapper,
            List<OrderDeliveryTicket> inserted,
            int firstId) {
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(firstId + inserted.size());
            inserted.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));
    }

    private SalesOrderHeader salesOrderHeader() {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(1101L);
        header.setSalesOrderNo("SO-STAGE4");
        header.setCustomerName("测试客户");
        header.setOrderType("正式订单");
        header.setInternalProjectNo("IP-SO-1");
        header.setCustomerOrderNo("CO-SO-1");
        header.setStatus(QuoteStatusWorkflow.QUOTED);
        return header;
    }

    private SalesOrderItem salesOrderItem(Long id, String materialNo, String materialNoStatus) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(1101L);
        item.setPartNo("SO-PART-" + id);
        item.setVersion("A");
        item.setDescription("Sales order test item");
        item.setQty(2);
        item.setRemainingQty(2);
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(materialNoStatus);
        item.setTaxIncludedUnitPrice(java.math.BigDecimal.TEN);
        item.setInternalDeliveryDate(java.time.LocalDate.of(2026, 8, 15));
        item.setInternalProjectNo("IP-SO-1");
        item.setCustomerOrderNo("CO-SO-1");
        item.setOrderType("正式订单");
        return item;
    }
}
