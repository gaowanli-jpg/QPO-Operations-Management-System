package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
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
    void rfqTicketInheritsConfirmedMaterialNoAndStartsMesSuccess() {
        DeliveryService service = new DeliveryService();
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        List<OrderDeliveryTicket> inserted = new ArrayList<>();
        OrderRfq order = rfqOrder();
        ItemQuoteVersion item = rfqItem(2001, "RFQ-MES-001", "已确认");
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 7, 10, 30);
        item.setMaterialNoSyncedAt(syncedAt);

        when(orderMapper.selectByIdForUpdate(1001)).thenReturn(order);
        when(orderMapper.selectById(1001)).thenReturn(order);
        when(itemMapper.selectByIdForUpdate(2001)).thenReturn(item);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(ticketMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(inserted));
        captureInsertedTicket(ticketMapper, inserted, 8001);
        wireRfq(service, orderMapper, itemMapper, ticketMapper);

        OrderDeliveryTicket ticket = service.generateTickets(rfqRequest(1001, 2001)).get(0);

        assertThat(ticket.getMaterialNo()).isEqualTo("RFQ-MES-001");
        assertThat(ticket.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticket.getMesSyncMessage()).isEqualTo("前置匹配复用已确认 MES 物料号");
        assertThat(ticket.getMesSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void rfqTicketLeavesUnconfirmedSourcePendingEvenWhenSourceHasMaterialNo() {
        DeliveryService service = new DeliveryService();
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        List<OrderDeliveryTicket> inserted = new ArrayList<>();
        OrderRfq order = rfqOrder();
        ItemQuoteVersion item = rfqItem(2002, "RFQ-UNCONFIRMED", "未确认");

        when(orderMapper.selectByIdForUpdate(1001)).thenReturn(order);
        when(orderMapper.selectById(1001)).thenReturn(order);
        when(itemMapper.selectByIdForUpdate(2002)).thenReturn(item);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(ticketMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(inserted));
        captureInsertedTicket(ticketMapper, inserted, 8002);
        wireRfq(service, orderMapper, itemMapper, ticketMapper);

        OrderDeliveryTicket ticket = service.generateTickets(rfqRequest(1001, 2002)).get(0);

        assertThat(ticket.getMaterialNo()).isNull();
        assertThat(ticket.getMesSyncStatus()).isEqualTo("PENDING");
        assertThat(ticket.getMesSyncedAt()).isNull();
    }

    @Test
    void salesOrderTicketInheritsConfirmedMaterialNoAndStartsMesSuccess() {
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

        assertThat(ticket.getMaterialNo()).isEqualTo("SO-MES-001");
        assertThat(ticket.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticket.getMesSyncMessage()).isEqualTo("前置匹配复用已确认 MES 物料号");
        assertThat(ticket.getMesSyncedAt()).isEqualTo(syncedAt);
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

    private void wireRfq(
            DeliveryService service,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper) {
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
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

    private DeliveryGenerateReq rfqRequest(int rfqId, int itemId) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(rfqId);
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IP-RFQ-1");
        req.setCustomerOrderNo("CO-RFQ-1");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(itemId);
        line.setAcceptedQty(2);
        req.setItems(List.of(line));
        return req;
    }

    private OrderRfq rfqOrder() {
        OrderRfq order = new OrderRfq();
        order.setId(1001);
        order.setRfqNo("RFQ-STAGE4");
        order.setCustomerName("测试客户");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private ItemQuoteVersion rfqItem(int id, String materialNo, String materialNoStatus) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(1001);
        item.setPartNo("RFQ-PART-" + id);
        item.setVersion("A");
        item.setDescription("RFQ test item");
        item.setQty(2);
        item.setTaxIncludedUnitPrice(10.0);
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(materialNoStatus);
        item.setLeadTime("2026-08-15");
        return item;
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
