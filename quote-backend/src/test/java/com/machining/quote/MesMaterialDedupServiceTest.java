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
import static org.mockito.Mockito.times;
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
        OrderDeliveryTicketMesRequest master = activeMaster(101, "MES-MASTER-101", "CALLBACK_PENDING", "v3|5:P-100|1:A|0:");
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
                .containsEntry("dedupKey", "v3|5:P-100|1:A|0:");
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
        assertThat(requestCaptor.getValue().getDedupKey()).isEqualTo("v3|5:P-100|1:A|0:");
        assertThat(requestCaptor.getValue().getRequestRole()).isEqualTo("MASTER");
        ArgumentCaptor<OrderDeliveryTicket> processingCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).update(processingCaptor.capture(), any());
        assertThat(processingCaptor.getValue().getMesSyncStatus()).isEqualTo("PROCESSING");
        assertThat(processingCaptor.getValue().getMesRequestRole()).isEqualTo("MASTER");
        assertThat(processingCaptor.getValue().getMesDedupKey()).isEqualTo("v3|5:P-100|1:A|0:");
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
        assertThat(requestCaptor.getValue().getDedupKey()).isEqualTo("v3|5:P-100|1:B|0:");
    }

    @Test
    void activeMasterUniqueConflictConvertsCurrentTicketToFollowerWithoutCallingMes() {
        Fixture fixture = fixture(ticket(205, "P-200", "C", "PENDING"));
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());
        OrderDeliveryTicketMesRequest master = activeMaster(105, "MES-MASTER-105", "PROCESSING", "v3|5:P-200|1:C|0:");
        // 依次是：三字段键查空、旧两字段键查空、按 ticket 查空 → 才能走到 insert 撞唯一键；
        // 撞键后 followActiveMaster 再按三字段键查一次，拿到胜者。
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null, null, null, master);
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
                .containsEntry("dedupKey", "v3|5:P-200|1:C|0:");
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
    void dedupKeyUsesPartNumberVersionAndMaterialWithLengthPrefixes() {
        MesMaterialDedupService service = new MesMaterialDedupService();

        assertThat(service.buildDedupKey(ticket(1, " P-100 ", " A ", "PENDING")))
                .isEqualTo("v3|5:P-100|1:A|0:");
        assertThat(service.buildDedupKey(ticket(2, "P-100", "B", "PENDING")))
                .isEqualTo("v3|5:P-100|1:B|0:");
        assertThat(service.buildDedupKey(ticket(3, "P-100", null, "PENDING")))
                .isEqualTo("v3|5:P-100|0:|0:");

        // 同图号版本、不同材质必须是不同身份，否则会互相 fan-out 抢同一个 MES 任务。
        OrderDeliveryTicket steel = ticket(4, "P-100", "A", "PENDING");
        steel.setMaterialName("Q235");
        OrderDeliveryTicket aluminium = ticket(5, "P-100", "A", "PENDING");
        aluminium.setMaterialName("6061-T6");
        assertThat(service.buildDedupKey(steel)).isNotEqualTo(service.buildDedupKey(aluminium));
        assertThat(service.buildDedupKey(steel)).isEqualTo("v3|5:P-100|1:A|4:Q235");

        // 材质只 trim，不折叠大小写；图号版本仍 uppercase。
        OrderDeliveryTicket lowerMaterial = ticket(6, "p-100", "a", "PENDING");
        lowerMaterial.setMaterialName(" 6061-t6 ");
        assertThat(service.buildDedupKey(lowerMaterial)).isEqualTo("v3|5:P-100|1:A|7:6061-t6");

        // 长度前缀让含分隔符的原值不再有歧义。
        OrderDeliveryTicket piped = ticket(7, "A|B", "C", "PENDING");
        OrderDeliveryTicket pipedSplit = ticket(8, "A", "B|C", "PENDING");
        assertThat(service.buildDedupKey(piped)).isNotEqualTo(service.buildDedupKey(pipedSplit));

        // 旧两字段键与新的三字段键格式互斥，混合查询才能同时接受两者。
        assertThat(service.buildLegacyDedupKey(ticket(9, "P-100", null, "PENDING")))
                .isEqualTo("P-100|-");
    }

    /**
     * 「刷新料号」写入的台账行没有 request_role。共享身份查询若继续按 MASTER 过滤，
     * 批量入口就看不见这条在途请求，会对同一物料身份再发一次 POST。
     */
    @Test
    void refreshMaterialRequestBlocksBatchEntryInsteadOfCreatingFollower() {
        OrderDeliveryTicket ticket = ticket(206, "P-REFRESH", "A", "PENDING");
        Fixture fixture = fixture(ticket);
        OrderDeliveryTicketMesRequest refresh =
                activeMaster(101, "QPO-MATR-01J5ABCDEF", "PROCESSING", "v3|9:P-REFRESH|1:A|0:");
        refresh.setRequestRole(null);
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(refresh);

        MesMaterialDedupService.DedupResolveResult result = fixture.dedupService.resolveBeforeMes(ticket, 9106L);

        assertThat(result.getType()).isEqualTo(MesMaterialDedupService.RESULT_ACTIVE_REQUEST_EXISTS);
        assertThat(result.isActiveRequestExists()).isTrue();
        assertThat(result.isFollowExistingActive()).isFalse();
        assertThat(result.getMasterMesRequestId()).isEqualTo("QPO-MATR-01J5ABCDEF");
        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        verify(fixture.waiterMapper, never()).insert(any());
        verify(fixture.ticketMapper, never()).updateById(any());
    }

    /**
     * 本提单自身已有在途请求时不能建 follower（会变成自己等自己），只回在途状态。
     */
    @Test
    void ownActiveRequestIsReportedAsActiveInsteadOfSelfFollower() {
        OrderDeliveryTicket ticket = ticket(207, "P-SELF", "A", "PENDING");
        Fixture fixture = fixture(ticket);
        OrderDeliveryTicketMesRequest own =
                activeMaster(207, "QPO-MATL-01J5SELF", "CALLBACK_PENDING", "v3|6:P-SELF|1:A|0:");
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(own);

        MesMaterialDedupService.DedupResolveResult result = fixture.dedupService.resolveBeforeMes(ticket, 9107L);

        assertThat(result.getType()).isEqualTo(MesMaterialDedupService.RESULT_ACTIVE_REQUEST_EXISTS);
        assertThat(result.getStatus()).isEqualTo("CALLBACK_PENDING");
        verify(fixture.waiterMapper, never()).insert(any());
        verify(fixture.ticketMapper, never()).updateById(any());
    }

    /**
     * 三字段键与旧两字段键必须分成两次查询：只有三字段键能证明材质一致、才允许建 follower。
     * 混成一次 `dedup_key IN (新, 旧)` 会让调用方分不清命中的是哪一类键。
     */
    @Test
    void activeRequestLookupQueriesThreeFieldKeyBeforeLegacyTwoFieldKey() {
        OrderDeliveryTicket ticket = ticket(208, "P-MIXED", "A", "PENDING");
        Fixture fixture = fixture(ticket);
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null);

        fixture.dedupService.resolveBeforeMes(ticket, 9108L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderDeliveryTicketMesRequest>>
                captor = ArgumentCaptor.forClass(
                        (Class) com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        // 三字段键 → 旧两字段键 → ticket_id，共三次。
        verify(fixture.mesRequestMapper, times(3)).selectOne(captor.capture());
        List<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderDeliveryTicketMesRequest>> lookups =
                captor.getAllValues();
        String threeFieldKey = MesMaterialDedupService.identityKey("P-MIXED", "A", null);
        assertThat(lookupParams(lookups.get(0)))
                .contains(threeFieldKey)
                .doesNotContain("P-MIXED|A");
        assertThat(lookupParams(lookups.get(1)))
                .contains("P-MIXED|A")
                .doesNotContain(threeFieldKey);
    }

    /** {@code in(column, collection)} 会把整个集合塞进一个参数位；先渲染 SQL 再展平参数。 */
    private List<Object> lookupParams(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> wrapper) {
        wrapper.getSqlSegment();
        List<Object> values = new java.util.ArrayList<>();
        for (Object value : wrapper.getParamNameValuePairs().values()) {
            if (value instanceof java.util.Collection<?> collection) {
                values.addAll(collection);
            } else {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 旧两字段键只含图号+版本，材质不可证明。命中它时把本提单挂成 follower，
     * 会让同图号版本、不同材质的零件永远等不到自己的料号（MES 里是两个独立任务）。
     * 因此只回在途状态，不建立任何等待关系。
     */
    @Test
    void legacyTwoFieldKeyMatchReportsActiveRequestWithoutCreatingFollower() {
        OrderDeliveryTicket ticket = ticket(209, "P-LEGACY", "A", "PENDING");
        ticket.setMaterialName("Q235");
        Fixture fixture = fixture(ticket);
        OrderDeliveryTicketMesRequest legacyMaster =
                activeMaster(500, "QPO-MATL-OLD", "CALLBACK_PENDING", "P-LEGACY|A");
        when(fixture.mesRequestMapper.selectOne(any()))
                .thenReturn(null)          // 三字段键没有命中
                .thenReturn(legacyMaster); // 旧两字段键命中

        MesMaterialDedupService.DedupResolveResult result =
                fixture.dedupService.resolveBeforeMes(ticket, 9209L);

        assertThat(result.getType()).isEqualTo(MesMaterialDedupService.RESULT_ACTIVE_REQUEST_EXISTS);
        assertThat(result.isActiveRequestExists()).isTrue();
        assertThat(result.isFollowExistingActive()).isFalse();
        assertThat(result.getMasterMesRequestId()).isEqualTo("QPO-MATL-OLD");
        assertThat(result.getStatus()).isEqualTo("CALLBACK_PENDING");
        verify(fixture.waiterMapper, never()).insert(any());
        verify(fixture.ticketMapper, never()).updateById(any());
    }

    /**
     * 结果未知不是死状态：MES 按「图号+版本+材质」全局判重，换新 bizId 重发不会造出第二个
     * 物料准备任务，只会明确回「已存在带料号」或「还在待建料」。所以重新获取前先把旧台账行
     * 收口成 STALE 释放身份去重位，再走正常提交。
     */
    @Test
    void retiresUnresolvedLedgerRowThenResubmitsForUnknownResult() {
        Fixture fixture = fixture(ticket(301, "P-300", "A", "RESULT_UNKNOWN"));
        when(fixture.mesRequestMapper.selectList(any()))
                .thenReturn(List.of(unresolvedRow(9001L, 301, "QPO-MAT-OLD")));
        when(fixture.mesRequestMapper.update(any(OrderDeliveryTicketMesRequest.class), any())).thenReturn(1);
        when(fixture.mesRequestMapper.selectOne(any())).thenReturn(null);
        when(fixture.mesRequestMapper.insert(any())).thenReturn(1);
        when(fixture.ticketMapper.updateById(any())).thenReturn(1);
        MesMaterialResolveResp response = successResponse("MES-RETRY");
        when(fixture.client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });

        Map<String, Object> result = fixture.syncService.syncMaterialNo(301, 9301L);

        assertThat(result).containsEntry("status", "SUCCESS").containsEntry("materialNo", "MES-RETRY");
        ArgumentCaptor<OrderDeliveryTicketMesRequest> updateCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.mesRequestMapper, org.mockito.Mockito.atLeastOnce())
                .update(updateCaptor.capture(), any());
        assertThat(updateCaptor.getAllValues())
                .anySatisfy(row -> {
                    assertThat(row.getRequestStatus()).isEqualTo("STALE");
                    assertThat(row.getCompletedAt()).isNotNull();
                });
        verify(fixture.auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_RETRY_AFTER_UNKNOWN"),
                eq("order_delivery_ticket"),
                eq(301),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("PROCESSING"),
                anyString(),
                isNull());
    }

    /** 回调抢先把台账行写成 SUCCESS 时，收口必须让路，不能把真结果改成 STALE。 */
    @Test
    void retireUnresolvedRequestsSkipsRowAlreadyResolvedByLateCallback() {
        Fixture fixture = fixture(ticket(302, "P-301", "A", "RESULT_UNKNOWN"));
        when(fixture.mesRequestMapper.selectList(any()))
                .thenReturn(List.of(unresolvedRow(9002L, 302, "QPO-MAT-LATE")));
        when(fixture.mesRequestMapper.update(any(OrderDeliveryTicketMesRequest.class), any())).thenReturn(0);

        List<String> retired = fixture.dedupService.retireUnresolvedRequests(ticket(302, "P-301", "A", "RESULT_UNKNOWN"));

        assertThat(retired).isEmpty();
    }

    private OrderDeliveryTicketMesRequest unresolvedRow(Long id, Integer ticketId, String mesRequestId) {
        OrderDeliveryTicketMesRequest row = new OrderDeliveryTicketMesRequest();
        row.setId(id);
        row.setTicketId(ticketId);
        row.setMesRequestId(mesRequestId);
        row.setRequestStatus("RESULT_UNKNOWN");
        return row;
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
