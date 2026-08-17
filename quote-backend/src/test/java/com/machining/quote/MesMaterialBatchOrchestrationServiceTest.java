package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DeliveryIntegrationBatchService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialBatchOrchestrationServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void materialBatchCallsMesOnceForSameCustomerProjectAndKeepsPendingCreateOpen() {
        Fixture fixture = fixture();
        OrderDeliveryTicket existing = ticket(601, "P-EXIST", "A", "IP-BATCH-1");
        OrderDeliveryTicket pending = ticket(602, "P-PENDING", "B", "IP-BATCH-1");
        fixture.withTicket(existing);
        fixture.withTicket(pending);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req,
                    item("P-EXIST", "A", "EXISTING_PRODUCT", "MAT-EXIST-601", "TASK-601"),
                    item("P-PENDING", "B", "PENDING_CREATE", null, "TASK-602"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString()))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(601, 602), 9601L);

        assertThat(result)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 1)
                .containsEntry("callbackPendingCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("batchCount", 1)
                .containsEntry("submittedToMesCount", 2);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "CALLBACK_PENDING");
        Map<String, Object> firstRowResult = (Map<String, Object>) rows.get(0).get("result");
        Object bizId = firstRowResult.get("bizId");
        assertThat(firstRowResult)
                .containsEntry("materialNo", "MAT-EXIST-601")
                .containsKey("bizId");
        assertThat(String.valueOf(bizId)).startsWith("QPO-MAT-");
        assertThat(rows.get(1).get("result")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "CALLBACK_PENDING")
                .containsEntry("bizId", bizId)
                .doesNotContainKey("materialNo");

        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(fixture.client, times(1)).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq req = reqCaptor.getValue();
        assertThat(req.getBizId()).isEqualTo(bizId);
        assertThat(req.getBizId()).startsWith("QPO-MAT-");
        assertThat(req.getItems()).hasSize(2);
        assertThat(req.getItems()).extracting(MesMaterialResolveReq.Item::getDrawingNo)
                .containsExactly("P-EXIST", "P-PENDING");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(existing, "MAT-EXIST-601", "MES material resolved");
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchDoesNotLetOneFailedGroupBlockAnotherGroup() {
        Fixture fixture = fixture();
        OrderDeliveryTicket failedGroup = ticket(611, "P-FAIL", "A", "IP-FAIL");
        OrderDeliveryTicket okGroup = ticket(612, "P-OK", "A", "IP-OK");
        fixture.withTicket(failedGroup);
        fixture.withTicket(okGroup);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            if ("IP-FAIL".equals(req.getInternalProjectNo())) {
                throw new RuntimeException("MES group failed");
            }
            return response(req, item("P-OK", "A", "EXISTING_PRODUCT", "MAT-OK-612", "TASK-612"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString()))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(611, 612), 9602L);

        assertThat(result)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(611, 612);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("FAILED", "SUCCESS");
        verify(fixture.client, times(2)).resolveMaterial(any());
        verify(fixture.sourceSync).syncConfirmedMaterialNo(okGroup, "MAT-OK-612", "MES material resolved");
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchFailsOnlyTheItemWhoseResponseRowIsMissing() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ok = ticket(621, "P-OK-MISSING", "A", "IP-BATCH-MISSING");
        OrderDeliveryTicket missing = ticket(622, "P-MISSING", "A", "IP-BATCH-MISSING");
        fixture.withTicket(ok);
        fixture.withTicket(missing);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req, item("P-OK-MISSING", "A", "EXISTING_PRODUCT", "MAT-OK-621", "TASK-621"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString()))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(621, 622), 9603L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(621, 622);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "FAILED");
        assertThat(rows.get(1).get("message")).asString().contains("missing row");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(ok, "MAT-OK-621", "MES material resolved");
        verify(fixture.sourceSync, times(1)).syncConfirmedMaterialNo(any(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialBatchFailsOnlyTheItemWhoseResponseRowIsDuplicated() {
        Fixture fixture = fixture();
        OrderDeliveryTicket duplicated = ticket(631, "P-DUP-RESP", "A", "IP-BATCH-DUP");
        OrderDeliveryTicket ok = ticket(632, "P-OK-DUP", "A", "IP-BATCH-DUP");
        fixture.withTicket(duplicated);
        fixture.withTicket(ok);
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return response(req,
                    item("P-DUP-RESP", "A", "EXISTING_PRODUCT", "MAT-DUP-FIRST", "TASK-631-A"),
                    item("P-DUP-RESP", "A", "EXISTING_PRODUCT", "MAT-DUP-SECOND", "TASK-631-B"),
                    item("P-OK-DUP", "A", "EXISTING_PRODUCT", "MAT-OK-632", "TASK-632"));
        });
        when(fixture.sourceSync.syncConfirmedMaterialNo(any(), anyString(), anyString()))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());

        Map<String, Object> result = fixture.batchService.syncMesMaterialBatch(List.of(631, 632), 9604L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(631, 632);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("FAILED", "SUCCESS");
        assertThat(rows.get(0).get("message")).asString().contains("duplicate rows");
        verify(fixture.sourceSync).syncConfirmedMaterialNo(ok, "MAT-OK-632", "MES material resolved");
        verify(fixture.sourceSync, times(1)).syncConfirmedMaterialNo(any(), anyString(), anyString());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.orderMapper = mock(OrderRfqMapper.class);
        fixture.itemMapper = mock(ItemQuoteVersionMapper.class);
        fixture.client = mock(MesMaterialClient.class);
        fixture.businessFileService = mock(BusinessFileService.class);
        fixture.sourceSync = mock(SourceItemMaterialNoSyncService.class);
        fixture.syncService = new MesMaterialSyncService();
        fixture.batchService = new DeliveryIntegrationBatchService();

        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setCustomerCode("GSS1");
        order.setCustomerName("Gold Sample");
        when(fixture.orderMapper.selectById(88)).thenReturn(order);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any(OrderDeliveryTicket.class))).thenReturn(1);
        when(fixture.businessFileService.externalReadableUrl(anyString()))
                .thenAnswer(invocation -> "http://qpo.test" + invocation.getArgument(0, String.class));

        ReflectionTestUtils.setField(fixture.syncService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.syncService, "orderRfqMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.syncService, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.syncService, "mesMaterialClient", fixture.client);
        ReflectionTestUtils.setField(fixture.syncService, "businessFileService", fixture.businessFileService);
        ReflectionTestUtils.setField(fixture.syncService, "sourceItemMaterialNoSyncService", fixture.sourceSync);

        ReflectionTestUtils.setField(fixture.batchService, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.batchService, "mesMaterialSyncService", fixture.syncService);
        return fixture;
    }

    private OrderDeliveryTicket ticket(Integer id, String partNo, String version, String projectNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setItemId(id + 1000);
        ticket.setSourceType("RFQ");
        ticket.setPartNo(partNo);
        ticket.setVersion(version);
        ticket.setInternalProjectNo(projectNo);
        ticket.setDescription("Precision bracket " + partNo);
        ticket.setSurfaceTreatment("Anodized");
        ticket.setRemark("Remark " + partNo);
        ticket.setMesSyncStatus("PENDING");
        ticket.setQty(1);
        return ticket;
    }

    private MesMaterialResolveResp response(MesMaterialResolveReq req, MesMaterialResolveResp.Item... items) {
        MesMaterialResolveResp resp = new MesMaterialResolveResp();
        resp.setCode(0);
        MesMaterialResolveResp.Data data = new MesMaterialResolveResp.Data();
        data.setBizId(req.getBizId());
        data.setItems(List.of(items));
        resp.setData(data);
        return resp;
    }

    private MesMaterialResolveResp.Item item(
            String drawingNo,
            String drawingVersion,
            String status,
            String productCode,
            String taskNo) {
        MesMaterialResolveResp.Item item = new MesMaterialResolveResp.Item();
        item.setDrawingNo(drawingNo);
        item.setDrawingVersion(drawingVersion);
        item.setStatus(status);
        item.setProductCode(productCode);
        item.setProductId(productCode == null ? null : 100L);
        item.setProductName(productCode == null ? null : "Material " + productCode);
        item.setTaskNo(taskNo);
        return item;
    }

    private final class Fixture {
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderRfqMapper orderMapper;
        private ItemQuoteVersionMapper itemMapper;
        private MesMaterialClient client;
        private BusinessFileService businessFileService;
        private SourceItemMaterialNoSyncService sourceSync;
        private MesMaterialSyncService syncService;
        private DeliveryIntegrationBatchService batchService;

        private void withTicket(OrderDeliveryTicket ticket) {
            when(ticketMapper.selectById(ticket.getId())).thenReturn(ticket);
            when(ticketMapper.selectByIdForUpdate(ticket.getId())).thenReturn(ticket);
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setId(ticket.getItemId());
            item.setDrawingUrl("/drawings/" + ticket.getPartNo() + ".pdf");
            when(itemMapper.selectById(ticket.getItemId())).thenReturn(item);
            when(itemMapper.selectByIdForUpdate(ticket.getItemId())).thenReturn(item);
        }
    }
}
