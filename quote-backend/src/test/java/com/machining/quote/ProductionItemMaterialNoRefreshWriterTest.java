package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.mes.ProductionItemMaterialNoRefreshWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionItemMaterialNoRefreshWriterTest {

    private final OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
    private final OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
    private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
    private final ItemQuoteVersionMapper itemQuoteVersionMapper = mock(ItemQuoteVersionMapper.class);

    @Test
    void writesTicketSalesOrderItemAndEveryAllocationOfTheTicket() {
        when(ticketMapper.selectByIdForUpdate(1)).thenReturn(ticket(1, "SALES_ORDER", "CS-OLD-001", 501L));
        when(allocationMapper.selectByTicketIdForUpdate(1)).thenReturn(List.of(
                allocation(11, "CS-OLD-001", "FAILED"),
                allocation(12, "CS-OLD-001", "PENDING")));
        when(salesOrderItemMapper.selectByIdForUpdate(501L)).thenReturn(salesItem(501L, "CS-OLD-001"));

        ProductionItemMaterialNoRefreshWriter.RefreshWriteResult result = writer().apply(
                1, "CS-NEW-002", "MES 删除旧料号并重新建号", "QPO-MATR-1", "TRACE-1", "ok");

        assertThat(result.previousMaterialNo()).isEqualTo("CS-OLD-001");
        assertThat(result.newMaterialNo()).isEqualTo("CS-NEW-002");
        assertThat(result.allocationUpdated()).isEqualTo(2);
        assertThat(result.salesOrderUpdated()).isEqualTo(1);
        assertThat(result.rfqUpdated()).isZero();

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("CS-NEW-002");
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketCaptor.getValue().getMesSyncMessage())
                .contains("CS-OLD-001 → CS-NEW-002")
                .contains("MES 删除旧料号并重新建号");

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(salesOrderItemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getMaterialNo()).isEqualTo("CS-NEW-002");
        assertThat(itemCaptor.getValue().getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(itemCaptor.getValue().getMaterialNoSource()).isEqualTo("MES_REFRESH");

        verify(allocationMapper, times(2)).updateById(any(OrderDeliveryTicketAllocation.class));
        verify(itemQuoteVersionMapper, never()).updateById(any(ItemQuoteVersion.class));
    }

    @Test
    void writesRfqItemAndDerivedSalesOrderItemForRfqSource() {
        when(ticketMapper.selectByIdForUpdate(2)).thenReturn(ticket(2, "RFQ", "CS-OLD-001", null));
        when(allocationMapper.selectByTicketIdForUpdate(2)).thenReturn(List.of(allocation(21, "CS-OLD-001", "FAILED")));
        when(itemQuoteVersionMapper.selectByIdForUpdate(2)).thenReturn(rfqItem(2, "CS-OLD-001"));
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(2)).thenReturn(salesItem(601L, "CS-OLD-001"));

        ProductionItemMaterialNoRefreshWriter.RefreshWriteResult result = writer().apply(
                2, "CS-NEW-002", "料号填写错误后已更正", "QPO-MATR-2", null, null);

        assertThat(result.rfqUpdated()).isEqualTo(1);
        assertThat(result.salesOrderUpdated()).isEqualTo(1);
        verify(itemQuoteVersionMapper).updateById(any(ItemQuoteVersion.class));
        verify(salesOrderItemMapper).updateById(any(SalesOrderItem.class));
    }

    @Test
    void rejectsTicketWhoseAllocationAlreadyDispatchedSuccessfully() {
        when(ticketMapper.selectByIdForUpdate(3)).thenReturn(ticket(3, "SALES_ORDER", "CS-OLD-001", 701L));
        when(allocationMapper.selectByTicketIdForUpdate(3)).thenReturn(List.of(allocation(31, "CS-OLD-001", "SUCCESS")));

        assertThatThrownBy(() -> writer().apply(3, "CS-NEW-002", "reason", "QPO-MATR-3", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已成功下发");

        verify(ticketMapper, never()).updateById(any(OrderDeliveryTicket.class));
        verify(salesOrderItemMapper, never()).updateById(any(SalesOrderItem.class));
        verify(allocationMapper, never()).updateById(any(OrderDeliveryTicketAllocation.class));
    }

    @Test
    void rejectsDiscardedTicket() {
        OrderDeliveryTicket discarded = ticket(4, "SALES_ORDER", "CS-OLD-001", 801L);
        discarded.setDiscarded(true);
        when(ticketMapper.selectByIdForUpdate(4)).thenReturn(discarded);

        assertThatThrownBy(() -> writer().apply(4, "CS-NEW-002", "reason", "QPO-MATR-4", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已废弃");

        verify(ticketMapper, never()).updateById(any(OrderDeliveryTicket.class));
    }

    private ProductionItemMaterialNoRefreshWriter writer() {
        ProductionItemMaterialNoRefreshWriter writer = new ProductionItemMaterialNoRefreshWriter();
        ReflectionTestUtils.setField(writer, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(writer, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(writer, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(writer, "itemQuoteVersionMapper", itemQuoteVersionMapper);
        return writer;
    }

    private OrderDeliveryTicket ticket(Integer id, String sourceType, String materialNo, Long sourceItemId) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType(sourceType);
        ticket.setMaterialNo(materialNo);
        ticket.setSourceItemId(sourceItemId);
        ticket.setItemId(2);
        ticket.setDiscarded(false);
        return ticket;
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String materialNo, String productionStatus) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(1);
        allocation.setMaterialNo(materialNo);
        allocation.setMesProductionStatus(productionStatus);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        return allocation;
    }

    private SalesOrderItem salesItem(Long id, String materialNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setMaterialNo(materialNo);
        return item;
    }

    private ItemQuoteVersion rfqItem(Integer id, String materialNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setMaterialNo(materialNo);
        return item;
    }
}
