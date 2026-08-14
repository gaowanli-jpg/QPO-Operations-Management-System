package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DeliveryIntegrationBatchService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialDedupService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialDedupStage3Test {
    @Test
    @SuppressWarnings("unchecked")
    void mesBatchDeduplicatesSamePartVersionAndReportsFollowerStatistics() {
        Fixture fixture = fixture();
        OrderDeliveryTicket first = ticket(301, "P-DUP", "A");
        OrderDeliveryTicket second = ticket(302, "P-DUP", "A");
        AtomicReference<OrderDeliveryTicketMesRequest> activeMaster = new AtomicReference<>();
        when(fixture.ticketMapper.selectById(301)).thenReturn(first);
        when(fixture.ticketMapper.selectById(302)).thenReturn(second);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any(OrderDeliveryTicket.class))).thenReturn(1);
        when(fixture.mesRequestMapper.selectOne(any())).thenAnswer(invocation -> activeMaster.get());
        when(fixture.mesRequestMapper.insert(any(OrderDeliveryTicketMesRequest.class))).thenAnswer(invocation -> {
            OrderDeliveryTicketMesRequest request = invocation.getArgument(0);
            activeMaster.set(request);
            return 1;
        });
        when(fixture.mesRequestMapper.update(any(), any())).thenAnswer(invocation -> {
            OrderDeliveryTicketMesRequest update = invocation.getArgument(0);
            if (activeMaster.get() != null && update != null && update.getRequestStatus() != null) {
                activeMaster.get().setRequestStatus(update.getRequestStatus());
            }
            return 1;
        });
        when(fixture.waiterMapper.insert(any())).thenReturn(1);
        MesMaterialResolveResp pending = new MesMaterialResolveResp();
        pending.setSuccess(true);
        pending.setStatus("CALLBACK_PENDING");
        pending.setCallbackRequired(true);
        pending.setMessage("accepted");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            pending.setMesRequestId(req.getMesRequestId());
            return pending;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(301, 302), 9201L);

        assertThat(result)
                .containsEntry("taskId", 9201L)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 0)
                .containsEntry("callbackPendingCount", 2)
                .containsEntry("failedCount", 0)
                .containsEntry("skippedCount", 0)
                .containsEntry("dedupFollowerCount", 1)
                .containsEntry("submittedToMesCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows)
                .extracting(row -> row.get("status"))
                .containsExactly("CALLBACK_PENDING", "DEDUP_FOLLOWING");
        assertThat(rows.get(1).get("message")).asString().contains("关联同图号版本");
        Map<String, Object> followerResult = (Map<String, Object>) rows.get(1).get("result");
        assertThat(followerResult)
                .containsEntry("dedupResult", "FOLLOW_EXISTING_ACTIVE")
                .containsEntry("dedupKey", "P-DUP|A");
        verify(fixture.client, times(1)).resolveMaterial(any());
        verify(fixture.waiterMapper, times(1)).insert(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mesBatchFollowsExistingActiveMasterWithoutSubmittingThatItemToMes() {
        Fixture fixture = fixture();
        OrderDeliveryTicket follower = ticket(311, "P-ACTIVE", "A");
        OrderDeliveryTicket normal = ticket(312, "P-NORMAL", "A");
        when(fixture.ticketMapper.selectById(311)).thenReturn(follower);
        when(fixture.ticketMapper.selectById(312)).thenReturn(normal);
        OrderDeliveryTicketMesRequest activeMaster = activeMaster(201, "MES-MASTER-201", "CALLBACK_PENDING", "P-ACTIVE|A");
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(activeMaster).thenReturn(null);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any(OrderDeliveryTicket.class))).thenReturn(1);
        when(fixture.waiterMapper.insert(any())).thenReturn(1);
        when(fixture.mesRequestMapper.insert(any(OrderDeliveryTicketMesRequest.class))).thenReturn(1);
        MesMaterialResolveResp success = new MesMaterialResolveResp();
        success.setSuccess(true);
        success.setMaterialNo("MAT-312");
        success.setStatus("SUCCESS");
        success.setMessage("created");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            success.setMesRequestId(req.getMesRequestId());
            return success;
        });

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(311, 312), 9203L);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("DEDUP_FOLLOWING", "SUCCESS");
        Map<String, Object> followerResult = (Map<String, Object>) rows.get(0).get("result");
        assertThat(followerResult)
                .containsEntry("dedupResult", "FOLLOW_EXISTING_ACTIVE")
                .containsEntry("mesRequestId", "MES-MASTER-201");
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(fixture.client, times(1)).resolveMaterial(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getItems()).hasSize(1);
        assertThat(reqCaptor.getValue().getItems().get(0).getDrawingNo()).isEqualTo("P-NORMAL");
        verify(fixture.waiterMapper, times(1)).insert(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mesBatchReportsOnlyNormalSuccessAndFollowerRows() {
        DeliveryIntegrationBatchService batchService = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        MesMaterialSyncService mesSyncService = mock(MesMaterialSyncService.class);
        OrderDeliveryTicket first = ticket(401, "P-REUSE", "A");
        OrderDeliveryTicket second = ticket(402, "P-REUSE", "A");
        ReflectionTestUtils.setField(batchService, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(batchService, "mesMaterialSyncService", mesSyncService);
        when(ticketMapper.selectById(401)).thenReturn(first);
        when(ticketMapper.selectById(402)).thenReturn(second);
        when(mesSyncService.syncMaterialBatch(any(), any())).thenReturn(List.of(Map.of(
                "ticketId", 401,
                "status", "SUCCESS",
                "materialNo", "MAT-401"
        ), Map.of(
                "ticketId", 402,
                "status", "SUCCESS",
                "materialNo", "MAT-401",
                "dedupResult", "MASTER_CREATED",
                "dedupKey", "P-REUSE|A"
        )));

        Map<String, Object> result = batchService.syncMesMaterialBatch(List.of(401, 402), 9202L);

        assertThat(result)
                .containsEntry("successCount", 2)
                .containsEntry("callbackPendingCount", 0)
                .containsEntry("dedupFollowerCount", 0)
                .containsEntry("submittedToMesCount", 2)
                .doesNotContainKey("reusedMaterialCount");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows)
                .extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "SUCCESS");
        assertThat(rows.get(1).get("message")).asString().contains("MES 物料号同步成功");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.auditService = mock(AuditService.class);
        fixture.businessFileService = mock(BusinessFileService.class);
        fixture.dedupService = new MesMaterialDedupService();
        fixture.mesSyncService = new MesMaterialSyncService();
        fixture.batchService = new DeliveryIntegrationBatchService();

        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerName("ACME");
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setDrawingUrl("http://qpo.local/drawing.pdf");
        when(fixture.orderMapper.selectById(88)).thenReturn(order);
        when(fixture.itemMapper.selectById(77)).thenReturn(item);
        when(fixture.businessFileService.externalReadableUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(fixture.dedupService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "mesRequestMapper", fixture.mesRequestMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "waiterMapper", fixture.waiterMapper);
        ReflectionTestUtils.setField(fixture.dedupService, "auditService", fixture.auditService);

        ReflectionTestUtils.setField(fixture.mesSyncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.mesSyncService, "mesRequestMapper", fixture.mesRequestMapper);
        ReflectionTestUtils.setField(fixture.mesSyncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.mesSyncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.mesSyncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.mesSyncService, "businessFileService", fixture.businessFileService);
        ReflectionTestUtils.setField(fixture.mesSyncService, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.mesSyncService, "dedupService", fixture.dedupService);

        ReflectionTestUtils.setField(fixture.batchService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.batchService, "mesMaterialSyncService", fixture.mesSyncService);
        return fixture;
    }

    private OrderDeliveryTicket ticket(Integer id, String partNo, String version) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo(partNo);
        ticket.setVersion(version);
        ticket.setInternalProjectNo("IP-9");
        ticket.setMesSyncStatus("PENDING");
        return ticket;
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
        private BusinessFileService businessFileService;
        private MesMaterialDedupService dedupService;
        private MesMaterialSyncService mesSyncService;
        private DeliveryIntegrationBatchService batchService;
    }
}
