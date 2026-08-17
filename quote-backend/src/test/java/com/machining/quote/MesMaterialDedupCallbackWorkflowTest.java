package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
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
import com.machining.quote.service.AuditService;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialDedupCallbackWorkflowTest {
    @Test
    void callbackSuccessFansOutMaterialNumberToFollowerTicketsAndSalesOrderItems() {
        Fixture fixture = fixture(masterTicket("CALLBACK_PENDING", null));
        OrderDeliveryTicket follower = salesOrderFollowerTicket(201, 6001L, "CALLBACK_PENDING");
        when(fixture.ticketMapper.selectById(123)).thenReturn(fixture.masterTicket);
        when(fixture.ticketMapper.selectById(201)).thenReturn(follower);
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(masterRequest("CALLBACK_PENDING"));
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(701L, 201, "CALLBACK_PENDING")));
        SalesOrderItem masterItem = new SalesOrderItem();
        masterItem.setId(5001L);
        SalesOrderItem followerItem = new SalesOrderItem();
        followerItem.setId(6001L);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(masterItem);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(6001L)).thenReturn(followerItem);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.update(any(), any())).thenReturn(1);

        fixture.syncService.handleMaterialCallback(callback("MAT-FANOUT"));

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper, org.mockito.Mockito.atLeast(2)).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(201);
                    assertThat(update.getMaterialNo()).isEqualTo("MAT-FANOUT");
                    assertThat(update.getMesSyncStatus()).isEqualTo("SUCCESS");
                    assertThat(update.getMesSyncMessage()).contains("MES completed");
                });
        ArgumentCaptor<OrderDeliveryTicketMesWaiter> waiterCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesWaiter.class);
        verify(fixture.waiterMapper).update(waiterCaptor.capture(), any());
        assertThat(waiterCaptor.getValue().getWaitStatus()).isEqualTo("SUCCESS");
        assertThat(waiterCaptor.getValue().getMaterialNo()).isEqualTo("MAT-FANOUT");
        assertThat(waiterCaptor.getValue().getCompletedAt()).isNotNull();
        ArgumentCaptor<SalesOrderItem> salesCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.salesOrderItemMapper, org.mockito.Mockito.atLeast(2)).updateById(salesCaptor.capture());
        assertThat(salesCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(6001L);
                    assertThat(update.getMaterialNo()).isEqualTo("MAT-FANOUT");
                    assertThat(update.getMaterialNoStatus()).isEqualTo("\u5df2\u786e\u8ba4");
                    assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
                    assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(201L);
                });
        ArgumentCaptor<Object> auditSummaryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("DELIVERY_MES_MATERIAL_DEDUP_FANOUT"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                auditSummaryCaptor.capture(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("mesRequestId=MES-REQ-123"),
                isNull()
        );
        assertThat((Map<String, Object>) auditSummaryCaptor.getValue())
                .containsEntry("sourceSyncTargetCount", 1)
                .containsEntry("sourceSyncUpdatedCount", 1)
                .containsEntry("sourceSyncIdempotentCount", 0)
                .containsEntry("rfqSourceUpdatedCount", 0)
                .containsEntry("salesOrderSourceUpdatedCount", 1);
    }

    @Test
    void immediateMasterSuccessFansOutToFollowerTickets() {
        Fixture fixture = fixture(masterTicket("PENDING", null));
        OrderDeliveryTicket follower = rfqFollowerTicket(202, "WAITING");
        when(fixture.ticketMapper.selectById(123)).thenReturn(fixture.masterTicket);
        when(fixture.ticketMapper.selectById(202)).thenReturn(follower);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        when(fixture.requestMapper.selectOne(any())).thenReturn(null);
        when(fixture.requestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(702L, 202, "WAITING")));
        when(fixture.waiterMapper.update(any(), any())).thenReturn(1);
        SalesOrderItem masterItem = new SalesOrderItem();
        masterItem.setId(5001L);
        ItemQuoteVersion followerItem = new ItemQuoteVersion();
        followerItem.setId(77);
        when(fixture.salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(masterItem);
        when(fixture.itemMapper.selectByIdForUpdate(77)).thenReturn(followerItem);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = successResponse("MAT-FAST");
            resp.setMesRequestId(req.getMesRequestId());
            return resp;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(123, 9301L);

        assertThat(result).containsEntry("status", "SUCCESS").containsEntry("materialNo", "MAT-FAST");
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper, org.mockito.Mockito.atLeast(2)).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(202);
                    assertThat(update.getMaterialNo()).isEqualTo("MAT-FAST");
                    assertThat(update.getMesSyncStatus()).isEqualTo("SUCCESS");
                });
    }

    @Test
    void callbackPendingMasterMovesFollowersToCallbackPending() {
        Fixture fixture = fixture(masterTicket("PENDING", null));
        OrderDeliveryTicket follower = rfqFollowerTicket(203, "PROCESSING");
        when(fixture.ticketMapper.selectById(123)).thenReturn(fixture.masterTicket);
        when(fixture.ticketMapper.selectById(203)).thenReturn(follower);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        when(fixture.requestMapper.selectOne(any())).thenReturn(null);
        when(fixture.requestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(703L, 203, "WAITING")));
        when(fixture.waiterMapper.update(any(), any())).thenReturn(1);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp resp = new MesMaterialResolveResp();
            resp.setSuccess(true);
            resp.setMesRequestId(req.getMesRequestId());
            resp.setStatus("CALLBACK_PENDING");
            resp.setCallbackRequired(true);
            resp.setExternalTraceId("MES-TASK-PENDING");
            resp.setMessage("accepted");
            return resp;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(123, 9302L);

        assertThat(result).containsEntry("status", "CALLBACK_PENDING");
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper, org.mockito.Mockito.atLeast(2)).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(203);
                    assertThat(update.getMesSyncStatus()).isEqualTo("CALLBACK_PENDING");
                    assertThat(update.getMaterialNo()).isNull();
                });
        ArgumentCaptor<OrderDeliveryTicketMesWaiter> waiterCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesWaiter.class);
        verify(fixture.waiterMapper).update(waiterCaptor.capture(), any());
        assertThat(waiterCaptor.getValue().getWaitStatus()).isEqualTo("CALLBACK_PENDING");
        assertThat(waiterCaptor.getValue().getMaterialNo()).isNull();
    }

    @Test
    void masterFailureMovesFollowersToFailed() {
        Fixture fixture = fixture(masterTicket("PENDING", null));
        OrderDeliveryTicket follower = rfqFollowerTicket(204, "PROCESSING");
        when(fixture.ticketMapper.selectById(123)).thenReturn(fixture.masterTicket);
        when(fixture.ticketMapper.selectById(204)).thenReturn(follower);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        when(fixture.requestMapper.selectOne(any())).thenReturn(null);
        when(fixture.requestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(704L, 204, "WAITING")));
        when(fixture.waiterMapper.update(any(), any())).thenReturn(1);
        when(fixture.client.resolveMaterial(any())).thenThrow(new RuntimeException("MES down"));

        assertThatThrownBy(() -> fixture.syncService.syncMaterialNo(123, 9303L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MES down");

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper, org.mockito.Mockito.atLeast(2)).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(204);
                    assertThat(update.getMesSyncStatus()).isEqualTo("FAILED");
                    assertThat(update.getMesSyncMessage()).contains("MES down");
                });
        ArgumentCaptor<OrderDeliveryTicketMesWaiter> waiterCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesWaiter.class);
        verify(fixture.waiterMapper).update(waiterCaptor.capture(), any());
        assertThat(waiterCaptor.getValue().getWaitStatus()).isEqualTo("FAILED");
    }

    @Test
    void conflictingCallbackDoesNotFanOutToFollowers() {
        Fixture fixture = fixture(masterTicket("SUCCESS", "MAT-OLD"));
        when(fixture.ticketMapper.selectById(123)).thenReturn(fixture.masterTicket);
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(masterRequest("SUCCESS"));
        when(fixture.waiterMapper.selectList(any())).thenReturn(List.of(waiter(705L, 205, "CALLBACK_PENDING")));

        assertThatThrownBy(() -> fixture.syncService.handleMaterialCallback(callback("MAT-NEW")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("冲突");

        verify(fixture.waiterMapper, never()).update(any(), any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
    }

    private Fixture fixture(OrderDeliveryTicket masterTicket) {
        Fixture fixture = new Fixture();
        fixture.masterTicket = masterTicket;
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.businessFileService = mock(BusinessFileService.class);
        fixture.auditService = mock(AuditService.class);
        fixture.sourceSyncService = new SourceItemMaterialNoSyncService();
        fixture.dedupService = new MesMaterialDedupService();
        fixture.syncService = new MesMaterialSyncService();

        when(fixture.orderMapper.selectById(88)).thenReturn(order());
        when(fixture.itemMapper.selectById(77)).thenReturn(item());
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
        ReflectionTestUtils.setField(fixture.dedupService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(
                fixture.dedupService, "sourceItemMaterialNoSyncService", fixture.sourceSyncService);
    }

    private void wireSync(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.syncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesRequestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.syncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.syncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.syncService, "businessFileService", fixture.businessFileService);
        ReflectionTestUtils.setField(fixture.syncService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.syncService, "dedupService", fixture.dedupService);
        ReflectionTestUtils.setField(
                fixture.syncService, "sourceItemMaterialNoSyncService", fixture.sourceSyncService);
    }

    private OrderDeliveryTicket masterTicket(String status, String materialNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setDrawingUrl("http://qpo.local/p100.pdf");
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9001L);
        ticket.setSourceItemId(5001L);
        ticket.setMesRequestId("MES-REQ-123");
        ticket.setMesSyncStatus(status);
        ticket.setMaterialNo(materialNo);
        ticket.setMesDedupKey("P-100|A");
        ticket.setMesRequestRole("MASTER");
        return ticket;
    }

    private OrderDeliveryTicket salesOrderFollowerTicket(Integer id, Long sourceItemId, String status) {
        OrderDeliveryTicket ticket = rfqFollowerTicket(id, status);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9002L);
        ticket.setSourceItemId(sourceItemId);
        return ticket;
    }

    private OrderDeliveryTicket rfqFollowerTicket(Integer id, String status) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setMesSyncStatus(status);
        ticket.setMesDedupKey("P-100|A");
        ticket.setMesRequestRole("FOLLOWER");
        ticket.setMesMasterTicketId(123);
        ticket.setMesMasterRequestId("MES-REQ-123");
        return ticket;
    }

    private OrderDeliveryTicketMesWaiter waiter(Long id, Integer ticketId, String status) {
        OrderDeliveryTicketMesWaiter waiter = new OrderDeliveryTicketMesWaiter();
        waiter.setId(id);
        waiter.setDedupKey("P-100|A");
        waiter.setMasterTicketId(123);
        waiter.setMasterMesRequestId("MES-REQ-123");
        waiter.setWaiterTicketId(ticketId);
        waiter.setWaitStatus(status);
        return waiter;
    }

    private OrderDeliveryTicketMesRequest masterRequest(String status) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setId(9001L);
        request.setTicketId(123);
        request.setMesRequestId("MES-REQ-123");
        request.setRequestStatus(status);
        request.setDedupKey("P-100|A");
        request.setRequestRole("MASTER");
        return request;
    }

    private MesMaterialCallbackReq callback(String materialNo) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("MES-REQ-123");
        req.setExternalTraceId("MES-TASK-1");
        req.setStatus("SUCCESS");
        req.setMaterialNo(materialNo);
        req.setMessage("MES completed");
        return req;
    }

    private MesMaterialResolveResp successResponse(String materialNo) {
        MesMaterialResolveResp resp = new MesMaterialResolveResp();
        resp.setSuccess(true);
        resp.setStatus("SUCCESS");
        resp.setMaterialNo(materialNo);
        resp.setExternalTraceId("MES-TASK-FAST");
        resp.setMessage("created");
        return resp;
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerName("ACME");
        return order;
    }

    private ItemQuoteVersion item() {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setDrawingUrl("http://qpo.local/p100.pdf");
        return item;
    }

    private static class Fixture {
        private OrderDeliveryTicket masterTicket;
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper requestMapper;
        private OrderDeliveryTicketMesWaiterMapper waiterMapper;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private SalesOrderItemMapper salesOrderItemMapper;
        private MesMaterialClient client;
        private BusinessFileService businessFileService;
        private AuditService auditService;
        private SourceItemMaterialNoSyncService sourceSyncService;
        private MesMaterialDedupService dedupService;
        private MesMaterialSyncService syncService;
    }
}
