package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceItemMaterialNoSyncServiceTest {

    @Test
    void rfqSourceItemWithBlankMaterialNoCanBeConfirmed() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket();
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(item);

        fixture.service.syncConfirmedMaterialNo(ticket, "  RFQ-MAT-001  ", "MES created");

        ArgumentCaptor<ItemQuoteVersion> captor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(captor.capture());
        ItemQuoteVersion update = captor.getValue();
        assertThat(update.getId()).isEqualTo(77);
        assertThat(update.getMaterialNo()).isEqualTo("RFQ-MAT-001");
        assertThat(update.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(123L);
        assertThat(update.getMaterialNoSyncedAt()).isNotNull();
        assertThat(update.getMaterialNoMessage()).isEqualTo("MES created");
    }

    @Test
    void rfqSourceItemWithSameMaterialNoIsIdempotent() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket();
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setMaterialNo("RFQ-MAT-001");
        item.setMaterialNoStatus("已确认");
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(item);

        fixture.service.syncConfirmedMaterialNo(ticket, "RFQ-MAT-001", "callback replay");

        ArgumentCaptor<ItemQuoteVersion> captor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(captor.capture());
        assertThat(captor.getValue().getMaterialNo()).isEqualTo("RFQ-MAT-001");
        assertThat(captor.getValue().getMaterialNoMessage()).isEqualTo("callback replay");
    }

    @Test
    void rfqSourceItemWithDifferentMaterialNoRejectsOverwrite() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket();
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setMaterialNo("RFQ-MAT-OLD");
        item.setMaterialNoStatus("已确认");
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(item);

        assertThatThrownBy(() -> fixture.service.syncConfirmedMaterialNo(ticket, "RFQ-MAT-NEW", "conflict"))
                .isInstanceOf(SourceItemMaterialNoSyncService.MaterialNoConflictException.class)
                .hasMessageContaining("不能覆盖");

        verify(fixture.itemMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rfqSourceFallsBackToTicketItemIdWhenSourceItemIdIsBlank() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket();
        ticket.setSourceItemId(null);
        ticket.setItemId(88);
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(88);
        when(fixture.itemMapper.selectByIdForUpdate(88)).thenReturn(item);

        fixture.service.syncConfirmedMaterialNo(ticket, "RFQ-MAT-088", null);

        ArgumentCaptor<ItemQuoteVersion> captor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(88);
        assertThat(captor.getValue().getMaterialNo()).isEqualTo("RFQ-MAT-088");
    }

    @Test
    void salesOrderSourceItemUsesSameConfirmationRules() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = salesOrderTicket();
        SalesOrderItem item = new SalesOrderItem();
        item.setId(5001L);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(item);

        fixture.service.syncConfirmedMaterialNo(ticket, "SO-MAT-001", "MES created");

        ArgumentCaptor<SalesOrderItem> captor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.salesOrderItemMapper).updateById(captor.capture());
        SalesOrderItem update = captor.getValue();
        assertThat(update.getId()).isEqualTo(5001L);
        assertThat(update.getMaterialNo()).isEqualTo("SO-MAT-001");
        assertThat(update.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(123L);
        assertThat(update.getMaterialNoSyncedAt()).isNotNull();
        assertThat(update.getMaterialNoMessage()).isEqualTo("MES created");
    }

    @Test
    void materialNoLongerThanColumnLimitIsRejected() {
        Fixture fixture = fixture();
        String tooLong = "A".repeat(121);

        assertThatThrownBy(() -> fixture.service.assertMaterialWritable(rfqTicket(), tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度超过 120");
    }

    private Fixture fixture() {
        SourceItemMaterialNoSyncService service = new SourceItemMaterialNoSyncService();
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        return new Fixture(service, itemMapper, salesOrderItemMapper);
    }

    private OrderDeliveryTicket rfqTicket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setSourceType("RFQ");
        ticket.setSourceItemId(77L);
        ticket.setItemId(77);
        return ticket;
    }

    private OrderDeliveryTicket salesOrderTicket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceItemId(5001L);
        return ticket;
    }

    private record Fixture(
            SourceItemMaterialNoSyncService service,
            ItemQuoteVersionMapper itemMapper,
            SalesOrderItemMapper salesOrderItemMapper) {
    }
}
