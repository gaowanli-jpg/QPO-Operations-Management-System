package com.machining.quote;

import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.SalesOrderAllocationLinkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderAllocationLinkServiceTest {
    @Test
    void quantityChangesRemainAllowedWhileWmsResultIsUnresolved() {
        SalesOrderAllocationLinkService service = new SalesOrderAllocationLinkService();
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);

        SalesOrderItem item = new SalesOrderItem();
        item.setId(700L);
        item.setAllocationId(601L);
        item.setQty(8);
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(601);
        allocation.setTicketId(501);
        OrderDeliveryTicket ticket = ticket(501, 700L, 8);
        ticket.setWmsAllocateStatus("RESULT_UNKNOWN");
        when(allocationMapper.selectById(601)).thenReturn(allocation);
        when(allocationMapper.selectByIdForUpdate(601)).thenReturn(allocation);
        when(ticketMapper.selectByIdForUpdate(501)).thenReturn(ticket);

        SalesOrderAllocationLinkService.AllocationQuantitySyncResult result =
                service.syncAllocationQtyFromItem(null, item, 10);

        assertThat(result).isNotNull();
        assertThat(result.allocationId()).isEqualTo(601);
        assertThat(result.salesOrderItemId()).isEqualTo(700L);
        assertThat(result.oldQty()).isEqualTo(10);
        assertThat(result.newQty()).isEqualTo(8);
        assertThat(result.ticketSynced()).isFalse();
        verify(allocationMapper).selectByIdForUpdate(601);
        verify(allocationMapper).updateById(any(OrderDeliveryTicketAllocation.class));
        verify(itemMapper).updateById(any(SalesOrderItem.class));
    }

    @Test
    void splitsDirectSalesOrderSourceAcrossTicketsAndPreservesUnreleasedQuantity() {
        SalesOrderAllocationLinkService service = new SalesOrderAllocationLinkService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);

        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(90L);
        SalesOrderItem source = new SalesOrderItem();
        source.setId(100L);
        source.setSalesOrderId(90L);
        source.setLineNo(1);
        source.setSourceType("DIRECT_IMPORT");
        source.setPartNo("PART-1");
        source.setQty(10);
        source.setReleasedQty(10);
        source.setRemainingQty(0);
        source.setShippedQty(0);
        source.setTaxIncludedUnitPrice(new BigDecimal("12.50"));
        source.setUntaxedUnitPrice(new BigDecimal("10.00"));
        source.setTaxIncludedTotalPrice(new BigDecimal("125.00"));
        source.setUntaxedTotalPrice(new BigDecimal("100.00"));

        OrderDeliveryTicket firstTicket = ticket(501, 100L, 4);
        OrderDeliveryTicket secondTicket = ticket(502, 100L, 6);
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(601);
        allocation.setTicketId(501);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setQty(4);
        List<SalesOrderItem> inserted = new ArrayList<>();

        when(itemMapper.selectById(100L)).thenReturn(source);
        when(ticketMapper.selectSalesOrderTicketsBySourceItemIdForUpdate(100L))
                .thenReturn(List.of(firstTicket, secondTicket));
        when(itemMapper.selectBySourceTicketIdForUpdate(501)).thenReturn(null);
        when(itemMapper.selectBySourceTicketIdForUpdate(502)).thenReturn(null);
        when(itemMapper.selectBySalesOrderIdForUpdate(90L)).thenReturn(List.of(source));
        when(itemMapper.selectCount(any())).thenReturn(2L);
        when(headerMapper.selectById(90L)).thenReturn(header);
        doAnswer(invocation -> {
            SalesOrderItem row = invocation.getArgument(0);
            row.setId(200L);
            inserted.add(row);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));

        service.linkMaterialAllocation(firstTicket, allocation);

        assertThat(source.getAllocationId()).isEqualTo(601L);
        assertThat(source.getQty()).isEqualTo(4);
        assertThat(source.getSourceTicketId()).isEqualTo(501);
        assertThat(source.getTaxIncludedTotalPrice()).isEqualByComparingTo("50.00");
        assertThat(secondTicket.getSourceItemId()).isEqualTo(200L);
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getQty()).isEqualTo(6);
        assertThat(inserted.get(0).getSourceTicketId()).isEqualTo(502);
        assertThat(inserted.get(0).getRemainingQty()).isZero();
        assertThat(source.getQty() + inserted.get(0).getQty()).isEqualTo(10);
        verify(ticketMapper).updateById(secondTicket);
        verify(headerMapper).updateById(header);
        verify(itemMapper).updateById(source);
    }

    private OrderDeliveryTicket ticket(int id, long sourceItemId, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType(ReleaseSourceType.SALES_ORDER.name());
        ticket.setSourceOrderId(90L);
        ticket.setSourceItemId(sourceItemId);
        ticket.setQty(qty);
        return ticket;
    }
}
