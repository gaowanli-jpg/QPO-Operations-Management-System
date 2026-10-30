package com.machining.quote;

import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesMaterialBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesMaterialBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.mes.MesMaterialCallbackTimeoutService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 只在成功时回调，失败不回调。等不到的回调必须由 QPO 自己收口成「结果待核对」，
 * 既不能一直停在待回调（用户没法重试也没人知道），也不能记成可重试失败（重发会造重复任务）。
 */
class MesMaterialCallbackTimeoutServiceTest {

    @Test
    void expiredCallbackPendingRequestBecomesResultUnknownEverywhere() {
        Fixture fixture = fixture();
        OrderDeliveryTicketMesRequest request = request(9001L, 701, "MES-TIMEOUT-1");
        when(fixture.requestMapper.selectList(any())).thenReturn(List.of(request));
        when(fixture.requestMapper.update(any(OrderDeliveryTicketMesRequest.class), any())).thenReturn(1);
        when(fixture.ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        MesMaterialBatchItem pendingItem = batchItem(11L, 501L, 701, "CALLBACK_PENDING");
        MesMaterialBatchItem expiredItem = batchItem(11L, 501L, 701, "RESULT_UNKNOWN");
        when(fixture.batchItemMapper.selectList(any()))
                .thenReturn(List.of(pendingItem))
                .thenReturn(List.of(expiredItem));

        int expired = fixture.service.expireOverdueCallbacks();

        assertThat(expired).isEqualTo(1);

        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.requestMapper).update(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("RESULT_UNKNOWN");
        // 仍活跃：不写 completed_at，继续占住身份去重位，避免被当成可重试失败而重复提交。
        assertThat(requestCaptor.getValue().getCompletedAt()).isNull();

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).update(ticketCaptor.capture(), any());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(ticketCaptor.getValue().getMesSyncMessage()).contains("4 小时");

        ArgumentCaptor<MesMaterialBatchItem> itemCaptor = ArgumentCaptor.forClass(MesMaterialBatchItem.class);
        verify(fixture.batchItemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo("RESULT_UNKNOWN");

        ArgumentCaptor<MesIntegrationBatch> batchCaptor = ArgumentCaptor.forClass(MesIntegrationBatch.class);
        verify(fixture.batchMapper).updateById(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(batchCaptor.getValue().getCompletedAt()).isNull();

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.auditService).record(
                actionCaptor.capture(), anyString(), any(), any(), any(), anyString(), anyString(), anyString(), any());
        assertThat(actionCaptor.getValue()).isEqualTo("DELIVERY_MES_MATERIAL_CALLBACK_TIMEOUT");
    }

    /**
     * 回调与超时扫描同时到达时，回调已经把请求写成 SUCCESS，扫描必须让路：
     * 条件更新命中 0 行就放弃，不能把真结果改成待核对。
     */
    @Test
    void doesNothingWhenCallbackWinsTheRace() {
        Fixture fixture = fixture();
        when(fixture.requestMapper.selectList(any()))
                .thenReturn(List.of(request(9002L, 702, "MES-TIMEOUT-2")));
        when(fixture.requestMapper.update(any(OrderDeliveryTicketMesRequest.class), any())).thenReturn(0);

        int expired = fixture.service.expireOverdueCallbacks();

        assertThat(expired).isZero();
        verify(fixture.ticketMapper, never()).update(any(OrderDeliveryTicket.class), any());
        verify(fixture.batchItemMapper, never()).updateById(any());
        verify(fixture.auditService, never()).record(
                anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNothingWhenNoRequestIsOverdue() {
        Fixture fixture = fixture();
        when(fixture.requestMapper.selectList(any())).thenReturn(List.of());

        assertThat(fixture.service.expireOverdueCallbacks()).isZero();
        verify(fixture.requestMapper, never()).update(any(OrderDeliveryTicketMesRequest.class), any());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.batchItemMapper = mock(MesMaterialBatchItemMapper.class);
        fixture.batchMapper = mock(MesIntegrationBatchMapper.class);
        fixture.auditService = mock(AuditService.class);
        fixture.service = new MesMaterialCallbackTimeoutService();
        ReflectionTestUtils.setField(fixture.service, "requestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.service, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.service, "batchItemMapper", fixture.batchItemMapper);
        ReflectionTestUtils.setField(fixture.service, "batchMapper", fixture.batchMapper);
        ReflectionTestUtils.setField(fixture.service, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(fixture.service, "timeoutHours", 4L);
        ReflectionTestUtils.setField(fixture.service, "batchSize", 200);
        return fixture;
    }

    private OrderDeliveryTicketMesRequest request(Long id, Integer ticketId, String mesRequestId) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setId(id);
        request.setTicketId(ticketId);
        request.setMesRequestId(mesRequestId);
        request.setRequestStatus("CALLBACK_PENDING");
        request.setMesExternalTaskId("TASK-" + ticketId);
        return request;
    }

    private MesMaterialBatchItem batchItem(Long id, Long batchId, Integer ticketId, String status) {
        MesMaterialBatchItem item = new MesMaterialBatchItem();
        item.setId(id);
        item.setBatchId(batchId);
        item.setTicketId(ticketId);
        item.setLineRequestId("MES-TIMEOUT-1");
        item.setStatus(status);
        return item;
    }

    private final class Fixture {
        private OrderDeliveryTicketMesRequestMapper requestMapper;
        private OrderDeliveryTicketMapper ticketMapper;
        private MesMaterialBatchItemMapper batchItemMapper;
        private MesIntegrationBatchMapper batchMapper;
        private AuditService auditService;
        private MesMaterialCallbackTimeoutService service;
    }
}
