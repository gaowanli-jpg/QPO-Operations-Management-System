package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryIntegrationBatchService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import com.machining.quote.service.wms.WmsReservationBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryIntegrationBatchTaskTest {
    @Test
    @SuppressWarnings("unchecked")
    void submitMesMaterialBatchTaskCreatesOneSystemTaskForManyTickets() {
        SystemTaskService service = new SystemTaskService();
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        AuditService auditService = mock(AuditService.class);
        DeliveryIntegrationBatchService batchService = mock(DeliveryIntegrationBatchService.class);
        AtomicReference<SystemTask> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(801L);
            inserted.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(801L)).thenAnswer(invocation -> inserted.get());
        List<Integer> requestedTicketIds = Arrays.asList(101, 102, 101, null);
        when(batchService.normalizeTicketIds(requestedTicketIds)).thenReturn(List.of(101, 102));
        wireTaskService(service, taskMapper, taskExecutor, auditService, batchService);

        SystemTask task = service.submitMesMaterialBatchSyncTask(requestedTicketIds);

        assertThat(task.getId()).isEqualTo(801L);
        assertThat(task.getTaskType()).isEqualTo("MES_MATERIAL_BATCH_SYNC");
        Map<String, Object> requestJson = new LinkedHashMap<>((Map<String, Object>) task.getRequestJson());
        assertThat(requestJson)
                .containsEntry("ticketCount", 2)
                .containsEntry("ticketIds", List.of(101, 102));
        verify(batchService).assertCanAccessTickets(List.of(101, 102));
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_SUBMIT"),
                eq("system_task"),
                eq(801L),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("taskId=801"),
                isNull()
        );
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitWmsAllocationBatchTaskCreatesOneSystemTaskForManyTickets() {
        SystemTaskService service = new SystemTaskService();
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        AuditService auditService = mock(AuditService.class);
        DeliveryIntegrationBatchService batchService = mock(DeliveryIntegrationBatchService.class);
        AtomicReference<SystemTask> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(802L);
            inserted.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(802L)).thenAnswer(invocation -> inserted.get());
        when(batchService.normalizeTicketIds(List.of(201, 202, 201))).thenReturn(List.of(201, 202));
        wireTaskService(service, taskMapper, taskExecutor, auditService, batchService);

        SystemTask task = service.submitWmsAllocationBatchTask(List.of(201, 202, 201));

        assertThat(task.getId()).isEqualTo(802L);
        assertThat(task.getTaskType()).isEqualTo("WMS_ALLOCATION_BATCH");
        Map<String, Object> requestJson = new LinkedHashMap<>((Map<String, Object>) task.getRequestJson());
        assertThat(requestJson)
                .containsEntry("ticketCount", 2)
                .containsEntry("ticketIds", List.of(201, 202));
        verify(batchService).assertCanAccessTickets(List.of(201, 202));
        verify(auditService).record(
                eq("DELIVERY_WMS_ALLOCATION_SUBMIT"),
                eq("system_task"),
                eq(802L),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("taskId=802"),
                isNull()
        );
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mesBatchDoesNotFallbackToLegacySingleRequestsWhenBatchReturnsNoRows() {
        DeliveryIntegrationBatchService service = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        MesMaterialSyncService mesService = mock(MesMaterialSyncService.class);
        wireBatchService(service, ticketMapper, mesService, null, null);
        when(ticketMapper.selectById(1)).thenReturn(ticket(1, "SUCCESS", "MAT-1", "PENDING", "MAT-1", 10));
        when(ticketMapper.selectById(2)).thenReturn(ticket(2, "PROCESSING", null, "PENDING", "MAT-2", 10));
        when(ticketMapper.selectById(3)).thenReturn(ticket(3, "PENDING", null, "PENDING", "MAT-3", 10));
        when(ticketMapper.selectById(4)).thenReturn(ticket(4, "FAILED", null, "PENDING", "MAT-4", 10));
        when(ticketMapper.selectById(5)).thenReturn(ticket(5, "PENDING", null, "PENDING", "MAT-5", 10));
        when(ticketMapper.selectById(6)).thenReturn(ticket(6, "CALLBACK_PENDING", null, "PENDING", "MAT-6", 10));
        when(ticketMapper.selectById(99)).thenReturn(null);
        when(mesService.syncMaterialBatch(any(), eq(9001L))).thenReturn(List.of());

        Map<String, Object> result = service.syncMesMaterialBatch(List.of(1, 2, 3, 4, 5, 6, 99), 9001L);

        assertThat(result)
                .containsEntry("taskId", 9001L)
                .containsEntry("totalCount", 7)
                .containsEntry("successCount", 0)
                .containsEntry("callbackPendingCount", 1)
                .containsEntry("failedCount", 3)
                .containsEntry("skippedCount", 3);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows)
                .extracting(row -> row.get("status"))
                .containsExactly("SKIPPED", "SKIPPED", "FAILED", "FAILED", "FAILED", "CALLBACK_PENDING", "SKIPPED");
        verify(mesService).syncMaterialBatch(any(), eq(9001L));
        verify(mesService, never()).syncMaterialNo(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void wmsBatchSkipsInvalidRowsAndSummarizesPartialSuccess() {
        DeliveryIntegrationBatchService service = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        WmsReservationBatchService wmsService = mock(WmsReservationBatchService.class);
        wireBatchService(service, ticketMapper, null, wmsService, null);
        Map<String, Object> expected = Map.of(
                "taskType", "WMS_ALLOCATION_BATCH",
                "taskId", 9002L,
                "totalCount", 5,
                "successCount", 4,
                "failedCount", 1,
                "results", List.of());
        when(wmsService.allocateBatch(List.of(1, 2, 3, 4, 5), 9002L)).thenReturn(expected);

        Map<String, Object> result = service.allocateWmsBatch(List.of(1, 2, 3, 4, 5), 9002L);

        assertThat(result).isSameAs(expected);
        verify(wmsService).allocateBatch(List.of(1, 2, 3, 4, 5), 9002L);
    }

    @Test
    void batchAccessChecksEveryExistingTicketBeforeTaskSubmission() {
        DeliveryIntegrationBatchService service = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        wireBatchService(service, ticketMapper, null, null, dataScopeService);
        when(ticketMapper.selectById(1)).thenReturn(ticket(1, "PENDING", null, "PENDING", "MAT-1", 10));
        when(ticketMapper.selectById(2)).thenReturn(ticket(2, "PENDING", null, "PENDING", "MAT-2", 10));
        when(ticketMapper.selectById(3)).thenReturn(null);

        service.assertCanAccessTickets(List.of(1, 2, 3));

        verify(dataScopeService, times(2)).assertCanAccessDeliveryTicket(any(OrderDeliveryTicket.class));
    }

    private void wireTaskService(
            SystemTaskService service,
            SystemTaskMapper taskMapper,
            ThreadPoolTaskExecutor taskExecutor,
            AuditService auditService,
            DeliveryIntegrationBatchService batchService) {
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "deliveryIntegrationBatchService", batchService);
    }

    private void wireBatchService(
            DeliveryIntegrationBatchService service,
            OrderDeliveryTicketMapper ticketMapper,
            MesMaterialSyncService mesService,
            WmsReservationBatchService wmsService,
            DataScopeService dataScopeService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesMaterialSyncService", mesService);
        ReflectionTestUtils.setField(service, "wmsReservationBatchService", wmsService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
    }

    private OrderDeliveryTicket ticket(
            Integer id,
            String mesStatus,
            String mesMaterialNo,
            String wmsStatus,
            String wmsMaterialNo,
            Integer qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(87 + id);
        ticket.setPartNo("P-" + id);
        ticket.setMesSyncStatus(mesStatus);
        ticket.setMaterialNo(wmsMaterialNo == null ? mesMaterialNo : wmsMaterialNo);
        ticket.setWmsAllocateStatus(wmsStatus);
        ticket.setQty(qty);
        return ticket;
    }
}
