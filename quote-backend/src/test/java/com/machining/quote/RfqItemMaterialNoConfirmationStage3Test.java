package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderDeliveryTicketMesWaiter;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialDedupService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfqItemMaterialNoConfirmationStage3Test {
    private static final String MATERIAL_CONFIRMED = "\u5df2\u786e\u8ba4";

    @Test
    void immediateMesSuccessConfirmsRfqSourceItem() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket(123, 77, "PENDING");
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        when(fixture.orderMapper.selectById(88)).thenReturn(order());
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(rfqSourceItem(77, null, null));
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveResp response = successResponse("RFQ-MAT-001", "MES immediate success");
            response.setMesRequestId(invocation.getArgument(0, com.machining.quote.dto.mes.MesMaterialResolveReq.class).getMesRequestId());
            return response;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(123, 9101L);

        assertThat(result)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "RFQ-MAT-001");
        assertRfqSourceConfirmed(fixture.itemMapper, 77, "RFQ-MAT-001", 123L, "MES immediate success");
    }

    @Test
    void callbackMesSuccessConfirmsRfqSourceItem() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket(123, 77, "CALLBACK_PENDING");
        ticket.setMesRequestId("MES-REQ-123");
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger(123));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(rfqSourceItem(77, null, null));

        fixture.syncService.handleMaterialCallback(callback("RFQ-MAT-CB"));

        assertRfqSourceConfirmed(fixture.itemMapper, 77, "RFQ-MAT-CB", 123L, "MES callback success");
    }

    @Test
    void fanOutFollowerSuccessConfirmsRfqSourceItem() {
        Fixture fixture = fixture();
        OrderDeliveryTicket follower = rfqTicket(202, 88, "CALLBACK_PENDING");
        follower.setMesRequestRole("FOLLOWER");
        follower.setMesMasterTicketId(123);
        follower.setMesMasterRequestId("MES-REQ-123");
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(702L, 202)));
        when(fixture.ticketMapper.selectById(202)).thenReturn(follower);
        when(fixture.itemMapper.selectByIdForUpdate(88)).thenReturn(rfqSourceItem(88, null, null));
        SalesOrderItem derivedSalesItem = new SalesOrderItem();
        derivedSalesItem.setId(6002L);
        when(fixture.salesOrderItemMapper.selectBySourceTicketIdForUpdate(202)).thenReturn(derivedSalesItem);

        fixture.dedupService.completeMasterSuccess("MES-REQ-123", "RFQ-MAT-FANOUT", "MES completed", "MES-TASK-1");

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getId()).isEqualTo(202);
        assertThat(ticketCaptor.getValue().getMaterialNo()).isEqualTo("RFQ-MAT-FANOUT");
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
        assertRfqSourceConfirmed(fixture.itemMapper, 88, "RFQ-MAT-FANOUT", 202L, "MES completed");
        ArgumentCaptor<SalesOrderItem> salesCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.salesOrderItemMapper).updateById(salesCaptor.capture());
        assertThat(salesCaptor.getValue().getId()).isEqualTo(6002L);
        assertThat(salesCaptor.getValue().getMaterialNo()).isEqualTo("RFQ-MAT-FANOUT");
        assertThat(salesCaptor.getValue().getMaterialNoStatus()).isEqualTo(MATERIAL_CONFIRMED);
    }

    @Test
    void conflictingCallbackDoesNotOverwriteRfqSourceItem() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = rfqTicket(123, 77, "CALLBACK_PENDING");
        ticket.setMesRequestId("MES-REQ-123");
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger(123));
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(rfqSourceItem(77, "RFQ-MAT-OLD", MATERIAL_CONFIRMED));

        assertThatThrownBy(() -> fixture.syncService.handleMaterialCallback(callback("RFQ-MAT-NEW")))
                .isInstanceOf(RuntimeException.class);

        verify(fixture.ticketMapper, never()).updateById(any(OrderDeliveryTicket.class));
        verify(fixture.itemMapper, never()).updateById(any(ItemQuoteVersion.class));
    }

    @Test
    void immediateMesSuccessStillConfirmsSalesOrderSourceItem() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = salesOrderTicket();
        when(fixture.ticketMapper.selectById(123)).thenReturn(ticket);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        SalesOrderItem sourceItem = new SalesOrderItem();
        sourceItem.setId(5001L);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(sourceItem);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveResp response = successResponse("SO-MAT-001", "MES sales success");
            response.setMesRequestId(invocation.getArgument(0, com.machining.quote.dto.mes.MesMaterialResolveReq.class).getMesRequestId());
            return response;
        });

        fixture.syncService.syncMaterialNo(123, 9103L);

        ArgumentCaptor<SalesOrderItem> captor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.salesOrderItemMapper).updateById(captor.capture());
        SalesOrderItem update = captor.getValue();
        assertThat(update.getId()).isEqualTo(5001L);
        assertThat(update.getMaterialNo()).isEqualTo("SO-MAT-001");
        assertThat(update.getMaterialNoStatus()).isEqualTo(MATERIAL_CONFIRMED);
        assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(123L);
        assertThat(update.getMaterialNoSyncedAt()).isNotNull();
    }

    private static void assertRfqSourceConfirmed(
            ItemQuoteVersionMapper mapper,
            Integer itemId,
            String materialNo,
            Long ticketId,
            String message) {
        ArgumentCaptor<ItemQuoteVersion> captor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(mapper).updateById(captor.capture());
        ItemQuoteVersion update = captor.getValue();
        assertThat(update.getId()).isEqualTo(itemId);
        assertThat(update.getMaterialNo()).isEqualTo(materialNo);
        assertThat(update.getMaterialNoStatus()).isEqualTo(MATERIAL_CONFIRMED);
        assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(ticketId);
        assertThat(update.getMaterialNoSyncedAt()).isNotNull();
        assertThat(update.getMaterialNoMessage()).isEqualTo(message);
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.businessFileService = mock(BusinessFileService.class);
        fixture.sourceSyncService = new SourceItemMaterialNoSyncService();
        fixture.dedupService = new MesMaterialDedupService();
        fixture.syncService = new MesMaterialSyncService();

        when(fixture.businessFileService.externalReadableUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        wireSourceSync(fixture);
        wireDedup(fixture);
        wireSync(fixture);
        return fixture;
    }

    private void wireSourceSync(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.sourceSyncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.sourceSyncService, "salesOrderItemMapper", fixture.salesOrderItemMapper);
    }

    private void wireDedup(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.dedupService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "mesRequestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "waiterMapper", fixture.waiterMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "sourceItemMaterialNoSyncService", fixture.sourceSyncService);
    }

    private void wireSync(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.syncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesRequestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.syncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.syncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.syncService, "businessFileService", fixture.businessFileService);
        ReflectionTestUtils.setField(fixture.syncService, "sourceItemMaterialNoSyncService", fixture.sourceSyncService);
    }

    private OrderDeliveryTicket rfqTicket(Integer ticketId, Integer itemId, String status) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(ticketId);
        ticket.setRfqId(88);
        ticket.setItemId(itemId);
        ticket.setSourceType("RFQ");
        ticket.setSourceItemId(itemId == null ? null : itemId.longValue());
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setDrawingUrl("http://qpo.local/p100.pdf");
        ticket.setMesSyncStatus(status);
        ticket.setMesDedupKey("P-100|A");
        return ticket;
    }

    private OrderDeliveryTicket salesOrderTicket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9001L);
        ticket.setSourceItemId(5001L);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setDrawingUrl("http://qpo.local/sales/p100.pdf");
        ticket.setMesSyncStatus("PENDING");
        return ticket;
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerCode("C-ACME");
        order.setCustomerName("ACME");
        return order;
    }

    private ItemQuoteVersion rfqSourceItem(Integer id, String materialNo, String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(status);
        return item;
    }

    private MesMaterialResolveResp successResponse(String materialNo, String message) {
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setStatus("SUCCESS");
        response.setMaterialNo(materialNo);
        response.setMessage(message);
        response.setExternalTraceId("MES-TASK-1");
        return response;
    }

    private MesMaterialCallbackReq callback(String materialNo) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("MES-REQ-123");
        req.setExternalTraceId("MES-TASK-1");
        req.setStatus("SUCCESS");
        req.setMaterialNo(materialNo);
        req.setMessage("MES callback success");
        return req;
    }

    private OrderDeliveryTicketMesRequest requestLedger(Integer ticketId) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setId(9001L);
        request.setTicketId(ticketId);
        request.setMesRequestId("MES-REQ-123");
        request.setRequestStatus("CALLBACK_PENDING");
        return request;
    }

    private OrderDeliveryTicketMesWaiter waiter(Long id, Integer ticketId) {
        OrderDeliveryTicketMesWaiter waiter = new OrderDeliveryTicketMesWaiter();
        waiter.setId(id);
        waiter.setDedupKey("P-100|A");
        waiter.setMasterTicketId(123);
        waiter.setMasterMesRequestId("MES-REQ-123");
        waiter.setWaiterTicketId(ticketId);
        waiter.setWaitStatus("CALLBACK_PENDING");
        return waiter;
    }

    private static class Fixture {
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper requestMapper;
        private OrderDeliveryTicketMesWaiterMapper waiterMapper;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private SalesOrderItemMapper salesOrderItemMapper;
        private MesMaterialClient client;
        private BusinessFileService businessFileService;
        private SourceItemMaterialNoSyncService sourceSyncService;
        private MesMaterialDedupService dedupService;
        private MesMaterialSyncService syncService;
    }
}
