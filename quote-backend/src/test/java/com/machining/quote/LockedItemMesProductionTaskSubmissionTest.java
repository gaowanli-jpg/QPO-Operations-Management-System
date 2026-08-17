package com.machining.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.LockedItemMesProductionBatchReq;
import com.machining.quote.dto.mes.MesProductionAssemblyRelationDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SystemTaskService;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.machining.quote.service.mes.MesProductionDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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

class LockedItemMesProductionTaskSubmissionTest extends LockedItemMesProductionDispatchTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void submitMesProductionBatchTaskCreatesSystemTaskAndDispatchesAsyncWork() {
        SystemTaskService taskService = new SystemTaskService();
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        AuditService auditService = mock(AuditService.class);
        MesProductionDispatchService dispatchService = mock(MesProductionDispatchService.class);
        AtomicReference<SystemTask> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(901L);
            inserted.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(901L)).thenAnswer(invocation -> inserted.get());
        List<Integer> requestedAllocationIds = Arrays.asList(10, 11, 10, null);
        when(dispatchService.normalizeAllocationIds(requestedAllocationIds)).thenReturn(List.of(10, 11));
        wireTaskService(taskService, taskMapper, taskExecutor, auditService, dispatchService);

        SystemTask task = taskService.submitMesProductionBatchTask(requestedAllocationIds);

        assertThat(task.getId()).isEqualTo(901L);
        assertThat(task.getTaskType()).isEqualTo("MES_PRODUCTION_DISPATCH");
        Map<String, Object> requestJson = new LinkedHashMap<>((Map<String, Object>) task.getRequestJson());
        assertThat(requestJson)
                .containsEntry("allocationCount", 2)
                .containsEntry("allocationIds", List.of(10, 11));
        verify(dispatchService).assertCanAccessAllocations(List.of(10, 11));
        verify(auditService).record(
                eq("PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT"),
                eq("system_task"),
                eq(901L),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("taskId=901"),
                isNull()
        );
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void productionItemMesProductionEndpointIsPermissionProtectedAndOldEndpointRemoved() throws Exception {
        String controller = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java"));

        assertThat(controller)
                .contains(
                        "LockedItemMesProductionBatchReq",
                        "@PostMapping(\"/production-items/mes-production\")",
                        "@RequirePermission(\"market:production-item:mes-production-submit\")",
                        "submitMesProductionBatchTask(req == null ? null : req.getAllocationIds())"
                )
                .doesNotContain(
                        "@PostMapping(\"/allocations/mes-production\")",
                        "@RequirePermission(\"market:locked-item:mes-production-submit\")"
                );
    }

    @Test
    void lockedItemMesProductionBatchReqCarriesAllocationIdsOnly() {
        LockedItemMesProductionBatchReq req = new LockedItemMesProductionBatchReq();
        req.setAllocationIds(List.of(1, 2));

        assertThat(req.getAllocationIds()).containsExactly(1, 2);
    }
}
