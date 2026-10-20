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
        verify(fixture.sourceSyncService).syncConfirmedMaterialNo(eq(ticket), eq("MAT-101"), any());
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(101);
        assertThat(updateCaptor.getValue().getMaterialNo()).isEqualTo("MAT-101");
        assertThat(updateCaptor.getValue().getMesSyncStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void convertsInterruptedProcessingRequestToFailedSoUserCanRetry() {
        Fixture fixture = fixture();
        OrderDeliveryTicket ticket = processingTicket(102, "MES-102");
        when(fixture.requestMapper.selectOne(any(Wrapper.class)))
                .thenReturn(request("MES-102", "PROCESSING", null, 9002L));
        when(fixture.taskMapper.selectById(9002L)).thenReturn(task("SUCCESS"));

        MesMaterialProcessingRecoveryService.RecoveryResult result =
                fixture.service.recoverProcessing(ticket, 9100L);

        assertThat(result.isHandled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("interrupted");
        verify(fixture.sourceSyncService, never()).syncConfirmedMaterialNo(any(), any(), any());
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getId()).isEqualTo(102);
        assertThat(ticketCaptor.getValue().getMesSyncStatus()).isEqualTo("FAILED");
        verify(fixture.requestMapper).update(any(OrderDeliveryTicketMesRequest.class), any(Wrapper.class));
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
        verify(fixture.sourceSyncService, never()).syncConfirmedMaterialNo(any(), any(), any());
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new MesMaterialProcessingRecoveryService();
        fixture.ticketMapper = mock(OrderDeliveryTicketMapper.class);
        fixture.requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        fixture.waiterMapper = mock(OrderDeliveryTicketMesWaiterMapper.class);
        fixture.taskMapper = mock(SystemTaskMapper.class);
        fixture.sourceSyncService = mock(SourceItemMaterialNoSyncService.class);
        when(fixture.sourceSyncService.syncConfirmedMaterialNo(any(), any(), any()))
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
