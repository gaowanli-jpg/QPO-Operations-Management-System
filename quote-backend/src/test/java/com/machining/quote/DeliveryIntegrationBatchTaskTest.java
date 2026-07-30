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
import com.machining.quote.service.wms.WmsAllocationService;
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
    void mesBatchSkipsInvalidRowsAndSummarizesPartialSuccess() {
        DeliveryIntegrationBatchService service = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        MesMaterialSyncService mesService = mock(MesMaterialSyncService.class);
        wireBatchService(service, ticketMapper, mesService, null, null);
        when(ticketMapper.selectById(1)).thenReturn(ticket(1, "SUCCESS", "MAT-1", "PENDING", "MAT-1", 10));
        when(ticketMapper.selectById(2)).thenReturn(ticket(2, "PROCESSING", null, "PENDING", "MAT-2", 10));
        when(ticketMapper.selectById(3)).thenReturn(ticket(3, "PENDING", null, "PENDING", "MAT-3", 10));
        when(ticketMapper.selectById(4)).thenReturn(ticket(4, "FAILED", null, "PENDING", "MAT-4", 10));
        when(ticketMapper.selectById(99)).thenReturn(null);
        when(mesService.syncMaterialNo(3, 9001L)).thenReturn(Map.of("ticketId", 3, "materialNo", "MAT-3"));
        when(mesService.syncMaterialNo(4, 9001L)).thenThrow(new RuntimeException("MES down"));

        Map<String, Object> result = service.syncMesMaterialBatch(List.of(1, 2, 3, 4, 99), 9001L);

        assertThat(result)
                .containsEntry("taskId", 9001L)
                .containsEntry("totalCount", 5)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1)
                .containsEntry("skippedCount", 3);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows)
                .extracting(row -> row.get("status"))
                .containsExactly("SKIPPED", "SKIPPED", "SUCCESS", "FAILED", "SKIPPED");
        verify(mesService).syncMaterialNo(3, 9001L);
        verify(mesService).syncMaterialNo(4, 9001L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wmsBatchSkipsInvalidRowsAndSummarizesPartialSuccess() {
        DeliveryIntegrationBatchService service = new DeliveryIntegrationBatchService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        WmsAllocationService wmsService = mock(WmsAllocationService.class);
        wireBatchService(service, ticketMapper, null, wmsService, null);
        when(ticketMapper.selectById(1)).thenReturn(ticket(1, "SUCCESS", null, "PENDING", null, 10));
        when(ticketMapper.selectById(2)).thenReturn(ticket(2, "SUCCESS", "MAT-2", "SUCCESS", "MAT-2", 10));
        when(ticketMapper.selectById(3)).thenReturn(ticket(3, "SUCCESS", "MAT-3", "PROCESSING", "MAT-3", 10));
        when(ticketMapper.selectById(4)).thenReturn(ticket(4, "SUCCESS", "MAT-4", "PENDING", "MAT-4", 10));
        when(ticketMapper.selectById(5)).thenReturn(ticket(5, "SUCCESS", "MAT-5", "FAILED", "MAT-5", 10));
        when(wmsService.allocate(4, 9002L)).thenReturn(Map.of("ticketId", 4, "allocationCount", 2));
        when(wmsService.allocate(5, 9002L)).thenThrow(new RuntimeException("WMS down"));

        Map<String, Object> result = service.allocateWmsBatch(List.of(1, 2, 3, 4, 5), 9002L);

        assertThat(result)
                .containsEntry("taskId", 9002L)
                .containsEntry("totalCount", 5)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 1)
                .containsEntry("skippedCount", 3);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows)
                .extracting(row -> row.get("status"))
                .containsExactly("SKIPPED", "SKIPPED", "SKIPPED", "SUCCESS", "FAILED");
        verify(wmsService).allocate(4, 9002L);
        verify(wmsService).allocate(5, 9002L);
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
            WmsAllocationService wmsService,
            DataScopeService dataScopeService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesMaterialSyncService", mesService);
        ReflectionTestUtils.setField(service, "wmsAllocationService", wmsService);
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
