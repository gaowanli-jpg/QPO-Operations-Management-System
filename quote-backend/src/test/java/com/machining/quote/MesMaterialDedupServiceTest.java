package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderDeliveryTicketMesWaiter;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialDedupService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialDedupServiceTest {
    @Test
    void syncMaterialNoLongerReusesHistoricalSuccessfulMaterial() {
        Fixture fixture = fixture(ticket(201, "P-100", "A", "PENDING"));
        OrderDeliveryTicket historical = ticket(101, "P-100", "A", "SUCCESS");
        historical.setMaterialNo("MAT-777");
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of(historical));
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null);
        when(fixture.mesRequestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        MesMaterialResolveResp response = successResponse("MES-NEW");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(201, 9101L);

        assertThat(result)
                .containsEntry("ticketId", 201)
                .containsEntry("taskId", 9101L)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-NEW");
        verify(fixture.client).resolveMaterial(any());
        verify(fixture.mesRequestMapper).insert(any());
        verify(fixture.ticketMapper, never()).selectList(any());
    }

    @Test
    void syncMaterialNoFollowsActiveCallbackPendingMasterWithoutCallingMes() {
        Fixture fixture = fixture(ticket(202, "P-100", "A", "PENDING"));
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        OrderDeliveryTicketMesRequest master = activeMaster(101, "MES-MASTER-101", "CALLBACK_PENDING", "P-100|A");
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(master);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.insert(any())).thenReturn(1);

        Map<String, Object> result = fixture.syncService.syncMaterialNo(202, 9102L);

        assertThat(result)
                .containsEntry("ticketId", 202)
                .containsEntry("taskId", 9102L)
                .containsEntry("status", "CALLBACK_PENDING")
                .containsEntry("dedupResult", "FOLLOW_EXISTING_ACTIVE")
                .containsEntry("mesRequestId", "MES-MASTER-101")
                .containsEntry("dedupKey", "P-100|A");
        verify(fixture.client, never()).resolveMaterial(any());
        verify(fixture.mesRequestMapper, never()).insert(any());
        ArgumentCaptor<OrderDeliveryTicketMesWaiter> waiterCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesWaiter.class);
        verify(fixture.waiterMapper).insert(waiterCaptor.capture());
        assertThat(waiterCaptor.getValue().getMasterTicketId()).isEqualTo(101);
        assertThat(waiterCaptor.getValue().getMasterMesRequestId()).isEqualTo("MES-MASTER-101");
        assertThat(waiterCaptor.getValue().getWaiterTicketId()).isEqualTo(202);
        assertThat(waiterCaptor.getValue().getWaitStatus()).isEqualTo("CALLBACK_PENDING");
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(updateCaptor.capture());
        OrderDeliveryTicket update = updateCaptor.getValue();
        assertThat(update.getMesSyncStatus()).isEqualTo("CALLBACK_PENDING");
        assertThat(update.getMesRequestRole()).isEqualTo("FOLLOWER");
        assertThat(update.getMesMasterTicketId()).isEqualTo(101);
        assertThat(update.getMesMasterRequestId()).isEqualTo("MES-MASTER-101");
    }

    @Test
    void syncMaterialNoDoesNotReuseMultipleDifferentSuccessfulMaterialsAndCallsMes() {
        Fixture fixture = fixture(ticket(203, "P-100", "A", "PENDING"));
        OrderDeliveryTicket oldA = ticket(101, "P-100", "A", "SUCCESS");
        oldA.setMaterialNo("MAT-888");
        OrderDeliveryTicket oldB = ticket(102, "P-100", "A", "SUCCESS");
        oldB.setMaterialNo("MAT-999");
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of(oldA, oldB));
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null);
        when(fixture.mesRequestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        MesMaterialResolveResp response = successResponse("MES-NEW");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(203, 9103L);

        assertThat(result)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-NEW");
        verify(fixture.client).resolveMaterial(any());
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.mesRequestMapper).insert(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getDedupKey()).isEqualTo("P-100|A");
        assertThat(requestCaptor.getValue().getRequestRole()).isEqualTo("MASTER");
        ArgumentCaptor<OrderDeliveryTicket> processingCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).update(processingCaptor.capture(), any());
        assertThat(processingCaptor.getValue().getMesSyncStatus()).isEqualTo("PROCESSING");
        assertThat(processingCaptor.getValue().getMesRequestRole()).isEqualTo("MASTER");
        assertThat(processingCaptor.getValue().getMesDedupKey()).isEqualTo("P-100|A");
    }

    @Test
    void failedTicketCanCreateNewMasterRequestWhenNoReusableOrActiveRequestExists() {
        OrderDeliveryTicket failed = ticket(204, "P-100", "B", "FAILED");
        Fixture fixture = fixture(failed);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null);
        when(fixture.mesRequestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        MesMaterialResolveResp response = successResponse("MES-B");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(204, 9104L);

        assertThat(result)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-B");
        verify(fixture.client).resolveMaterial(any());
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.mesRequestMapper).insert(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getDedupKey()).isEqualTo("P-100|B");
    }

    @Test
    void activeMasterUniqueConflictConvertsCurrentTicketToFollowerWithoutCallingMes() {
        Fixture fixture = fixture(ticket(205, "P-200", "C", "PENDING"));
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        OrderDeliveryTicketMesRequest master = activeMaster(105, "MES-MASTER-105", "PROCESSING", "P-200|C");
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null, master);
        when(fixture.mesRequestMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("uk_delivery_mes_request_active_dedup"));
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        when(fixture.waiterMapper.insert(any())).thenReturn(1);

        Map<String, Object> result = fixture.syncService.syncMaterialNo(205, 9105L);

        assertThat(result)
                .containsEntry("ticketId", 205)
                .containsEntry("taskId", 9105L)
                .containsEntry("status", "PROCESSING")
                .containsEntry("dedupResult", "FOLLOW_EXISTING_ACTIVE")
                .containsEntry("mesRequestId", "MES-MASTER-105")
                .containsEntry("dedupKey", "P-200|C");
        verify(fixture.client, never()).resolveMaterial(any());
        ArgumentCaptor<OrderDeliveryTicketMesWaiter> waiterCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesWaiter.class);
        verify(fixture.waiterMapper).insert(waiterCaptor.capture());
        assertThat(waiterCaptor.getValue().getWaiterTicketId()).isEqualTo(205);
        assertThat(waiterCaptor.getValue().getWaitStatus()).isEqualTo("WAITING");
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getMesRequestRole()).isEqualTo("FOLLOWER");
        assertThat(updateCaptor.getValue().getMesMasterRequestId()).isEqualTo("MES-MASTER-105");
    }

    @Test
    void dedupKeyUsesPartNumberAndVersionOnly() {
        MesMaterialDedupService service = new MesMaterialDedupService();

        assertThat(service.buildDedupKey(ticket(1, " P-100 ", " A ", "PENDING"))).isEqualTo("P-100|A");
        assertThat(service.buildDedupKey(ticket(2, "P-100", "B", "PENDING"))).isEqualTo("P-100|B");
        assertThat(service.buildDedupKey(ticket(3, "P-100", null, "PENDING"))).isEqualTo("P-100|-");
    }

    private Fixture fixture(OrderDeliveryTicket ticket) {
        Fixture fixture = new Fixture();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.auditService = mock(AuditService.class);
        fixture.dedupService = new MesMaterialDedupService();
        fixture.syncService = new MesMaterialSyncService();

        when(fixture.ticketMapper.selectById(ticket.getId())).thenReturn(ticket);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.orderMapper.selectById(88)).thenReturn(order());
        when(fixture.itemMapper.selectById(77)).thenReturn(item());
        wireDedup(fixture);
        wireSync(fixture);
        return fixture;
    }

    private void wireDedup(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.dedupService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "mesRequestMapper", fixture.mesRequestMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "waiterMapper", fixture.waiterMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "auditService", fixture.auditService);
    }

    private void wireSync(Fixture fixture) {
        ReflectionTestUtils.setField(fixture.syncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesRequestMapper", fixture.mesRequestMapper);
        ReflectionTestUtils.setField(fixture.syncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.syncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.syncService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.syncService, "dedupService", fixture.dedupService);
    }

    private OrderDeliveryTicket ticket(Integer id, String partNo, String version, String status) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo(partNo);
        ticket.setVersion(version);
        ticket.setInternalProjectNo("IP-9");
        ticket.setMesSyncStatus(status);
        return ticket;
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

    private MesMaterialResolveResp successResponse(String materialNo) {
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo(materialNo);
        response.setStatus("SUCCESS");
        response.setMessage("created");
        return response;
    }

    private OrderDeliveryTicketMesRequest activeMaster(Integer ticketId, String mesRequestId, String status, String dedupKey) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setTicketId(ticketId);
        request.setMesRequestId(mesRequestId);
        request.setRequestStatus(status);
        request.setDedupKey(dedupKey);
        request.setRequestRole("MASTER");
        return request;
    }

    private static class Fixture {
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper mesRequestMapper;
        private OrderDeliveryTicketMesWaiterMapper waiterMapper;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private MesMaterialClient client;
        private AuditService auditService;
        private MesMaterialDedupService dedupService;
        private MesMaterialSyncService syncService;
    }
}
