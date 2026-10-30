package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesWaiterMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialProcessingRecoveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialProcessingRecoveryServiceTest {

    @Test
    void recoversProcessingTicketFromOwnSuccessfulRequestLedger() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = processingTicket(101, "MES-101");
        when(fixture.requestMapper.selectOne(any(Wrapper.class)))
                .thenReturn(request("MES-101", "SUCCESS", "MAT-101", 9001L));

        MesMaterialProcessingRecoveryService.RecoveryResult result =
                fixture.service.recoverProcessing(ticket, 9100L);

        assertThat(result.isHandled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getMessage()).contains("recovered");
        verify(fixture.sourceSyncService).syncConfirmedMaterialNo(eq(ticket), eq("MAT-101"), any(), eq(true));
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(101);
        assertThat(updateCaptor.getValue().getMaterialNo()).isEqualTo("MAT-101");
        assertThat(updateCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void convertsInterruptedProcessingRequestToResultUnknownInsteadOfRetryableFailure() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = processingTicket(102, "MES-102");
        when(fixture.requestMapper.selectOne(any(Wrapper.class)))
                .thenReturn(request("MES-102", "PROCESSING", null, 9002L));
        when(fixture.taskMapper.selectById(9002L)).thenReturn(task("SUCCESS"));

        MesMaterialProcessingRecoveryService.RecoveryResult result =
                fixture.service.recoverProcessing(ticket, 9100L);

        assertThat(result.isHandled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(result.getMessage()).contains("interrupted").contains("unknown");
        verify(fixture.sourceSyncService, never()).syncConfirmedMaterialNo(any(), any(), any(), anyBoolean());
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getId()).isEqualTo(102);
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("RESULT_UNKNOWN");
        // 台账行必须继续活跃（不写 completed_at），否则身份去重位被释放、会重复提交。
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(fixture.requestMapper).update(requestCaptor.capture(), any(Wrapper.class));
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(requestCaptor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void keepsProcessingWhenLinkedSystemTaskIsStillRunning() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = processingTicket(103, "MES-103");
        when(fixture.requestMapper.selectOne(any(Wrapper.class)))
                .thenReturn(request("MES-103", "PROCESSING", null, 9003L));
        when(fixture.taskMapper.selectById(9003L)).thenReturn(task("RUNNING"));

        MesMaterialProcessingRecoveryService.RecoveryResult result =
                fixture.service.recoverProcessing(ticket, 9100L);

        assertThat(result.isHandled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SKIPPED");
        assertThat(result.getMessage()).contains("processing");
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.sourceSyncService, never()).syncConfirmedMaterialNo(any(), any(), any(), anyBoolean());
    }

    @Test
    void restoresUnknownRequestAsUnknownAndNeverClosesItAsRetryableFailure() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = processingTicket(104, "MES-104");
        OrderDeliveryTicketMesRequest request = request("MES-104", "RESULT_UNKNOWN", null, 9004L);
        request.setResponseMessage("response parse failed after submit");
        request.setMesExternalTaskId("MES-TASK-104");
        when(fixture.requestMapper.selectOne(any(Wrapper.class))).thenReturn(request);

        MesMaterialProcessingRecoveryService.RecoveryResult result =
                fixture.service.recoverProcessing(ticket, 9100L);

        assertThat(result.isHandled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(result.getMessage()).contains("response parse failed");
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(ticketCaptor.getValue().getMesRequestId()).isEqualTo("MES-104");
        assertThat(ticketCaptor.getValue().getMesExternalTaskId()).isEqualTo("MES-TASK-104");
        verify(fixture.requestMapper, never()).update(any(), any());
        verify(fixture.waiterMapper, never()).update(any(), any());
        verify(fixture.taskMapper, never()).selectById(any());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new MesMaterialProcessingRecoveryService();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.taskMapper = mock(SystemTaskMapper.class);
        fixture.sourceSyncService = mock(SourceItemMaterialNoSyncService.class);
        when(fixture.sourceSyncService.syncConfirmedMaterialNo(any(), any(), any(), eq(true)))
                .thenReturn(SourceItemMaterialNoSyncService.MaterialNoSyncResult.empty());
        ReflectionTestUtils.setField(fixture.service, "ticketMapper", fixture.ticketMapper);
        ReflectionTestUtils.setField(fixture.service, "requestMapper", fixture.requestMapper);
        ReflectionTestUtils.setField(fixture.service, "waiterMapper", fixture.waiterMapper);
        ReflectionTestUtils.setField(fixture.service, "taskMapper", fixture.taskMapper);
        ReflectionTestUtils.setField(fixture.service, "sourceSyncService", fixture.sourceSyncService);
        return fixture;
    }

    private OrderDeliveryTicket processingTicket(Integer id, String mesRequestId) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setPartNo("P-" + id);
        ticket.setVersion("A");
        ticket.setMesRequestId(mesRequestId);
        ticket.setMesSyncStatus("PROCESSING");
        return ticket;
    }

    private OrderDeliveryTicketMesRequest request(String mesRequestId, String status, String materialNo, Long taskId) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setMesRequestId(mesRequestId);
        request.setTicketId(101);
        request.setRequestStatus(status);
        request.setMaterialNo(materialNo);
        request.setSystemTaskId(taskId);
        return request;
    }

    private SystemTask task(String status) {
        SystemTask task = new SystemTask();
        task.setId(9000L);
        task.setStatus(status);
        return task;
    }

    private static class Fixture {
        private MesMaterialProcessingRecoveryService service;
        private OrderDeliveryTicketMapper ticketMapper;
        private OrderDeliveryTicketMesRequestMapper requestMapper;
        private OrderDeliveryTicketMesWaiterMapper waiterMapper;
        private SystemTaskMapper taskMapper;
        private SourceItemMaterialNoSyncService sourceSyncService;
    }
}
