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

class LockedItemMesProductionDispatchTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    @SuppressWarnings("unchecked")
    void dispatchBatchOnlySubmitsProductionRequiredRowsAndSendsLockedItemSnapshot() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicketAllocation production = allocation(1, "PRODUCTION_REQUIRED", "PENDING");
        OrderDeliveryTicketAllocation inStock = allocation(2, "IN_STOCK_LOCK", "PENDING");
        when(allocationMapper.selectById(1)).thenReturn(production);
        when(allocationMapper.selectById(2)).thenReturn(inStock);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-1", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, auditService, null);

        Map<String, Object> result = service.dispatchBatch(List.of(1, 2), 9001L);

        assertThat(result)
                .containsEntry("taskType", "MES_PRODUCTION_DISPATCH")
                .containsEntry("taskId", 9001L)
                .containsEntry("totalCount", 2)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("skippedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).extracting(row -> row.get("status")).containsExactly("SUCCESS", "SKIPPED");
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq request = requestCaptor.getValue();
        assertThat(request.getRequestId()).startsWith("QPO-MP-");
        assertThat(request.getAllocationId()).isEqualTo(1);
        assertThat(request.getTicketId()).isEqualTo(101);
        assertThat(request.getSourceType()).isEqualTo("SALES_ORDER");
        assertThat(request.getSourceOrderId()).isEqualTo(3001L);
        assertThat(request.getSourceItemId()).isEqualTo(9001L);
        assertThat(request.getSourceOrderNo()).isEqualTo("SO-1");
        assertThat(request.getCustomerName()).isEqualTo("ACME");
        assertThat(request.getPartNo()).isEqualTo("P-100");
        assertThat(request.getVersion()).isEqualTo("A");
        assertThat(request.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(request.getItemType()).isEqualTo("ASSEMBLY");
        assertThat(request.getAllocationType()).isEqualTo("PRODUCTION_REQUIRED");
        assertThat(request.getProductionQty()).isEqualTo(4);
        assertThat(request.getTicketQty()).isEqualTo(10);
        assertThat(request.getOrderType()).isEqualTo("SO01");
        assertThat(request.getInternalProjectNo()).isEqualTo("IP-9");
        assertThat(request.getCustomerOrderNo()).isEqualTo("CO-9");
        assertThat(request.getCustomerProjectNo()).isEqualTo("CP-9");
        assertThat(request.getMaterialName()).isEqualTo("6061");
        assertThat(request.getEngineeringMaterialName()).isEqualTo("7075");
        assertThat(request.getMaterialShape()).isEqualTo("板材");
        assertThat(request.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(request.getDescription()).isEqualTo("Bracket");
        assertThat(request.getRemark()).isEqualTo("Rush");
        assertThat(request.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(request.getProcessRoute()).singleElement().satisfies(route -> {
            assertThat(route.getProcessCode()).isNull();
            assertThat(route.getCategoryName()).isNull();
            assertThat(route.getDetailName()).isEqualTo("CNC");
            assertThat(route.getUnitWorkHours()).isNull();
            assertThat(route.getSortNo()).isNull();
        });
        assertThat(request.getMaterialData()).isEqualTo(Map.of("shape", "plate"));
        assertThat(request.getMaterialGrossWeight()).isEqualTo(12.3);
        assertThat(request.getPartNetWeight()).isEqualTo(10.2);
        assertThat(request.getAssemblyRelations()).isEmpty();
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateByIdCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateByIdCaptor.capture());
        OrderDeliveryTicketAllocation successUpdate = updateByIdCaptor.getValue();
        assertThat(successUpdate.getId()).isEqualTo(1);
        assertThat(successUpdate.getMesProductionStatus()).isEqualTo("SUCCESS");
        assertThat(successUpdate.getMesProductionExternalId()).isEqualTo("MES-PROD-1");
        assertThat(successUpdate.getMesProductionRequestId()).isEqualTo(request.getRequestId());
        verify(auditService).record(
                eq("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS"),
                eq("order_delivery_ticket_allocation"),
                eq(1),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("allocationId=1 taskId=9001"),
                isNull()
        );
    }

    @Test
    void dispatchBatchPackagesRfqAssemblyRelationsFromSourceItem() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        ItemAssemblyRelationMapper relationMapper = mock(ItemAssemblyRelationMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketAllocation allocation = allocation(4, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setSourceType("RFQ");
        allocation.setSourceOrderId(204L);
        allocation.setSourceItemId(501L);
        allocation.setPartNo("ASM-501");
        allocation.setVersion("B");
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setParentItemId(501);
        relation.setChildItemId(502);
        relation.setChildQtyPerParent(2.5);
        relation.setSortNo(7);
        ItemQuoteVersion parent = rfqItem(501, "ASM-501", "B");
        ItemQuoteVersion child = rfqItem(502, "CH-502", "C");
        when(allocationMapper.selectById(4)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(itemMapper.selectById(501)).thenReturn(parent);
        when(itemMapper.selectById(502)).thenReturn(child);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-RFQ", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                relationMapper, itemMapper, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(4), 9104L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAssemblyRelations()).hasSize(1);
        MesProductionAssemblyRelationDTO dto = requestCaptor.getValue().getAssemblyRelations().get(0);
        assertThat(dto.getParentItemId()).isEqualTo(501L);
        assertThat(dto.getChildItemId()).isEqualTo(502L);
        assertThat(dto.getParentPartNo()).isEqualTo("ASM-501");
        assertThat(dto.getParentVersion()).isEqualTo("B");
        assertThat(dto.getChildPartNo()).isEqualTo("CH-502");
        assertThat(dto.getChildVersion()).isEqualTo("C");
        assertThat(dto.getQtyPerParent()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(dto.getSortNo()).isEqualTo(7);
        assertThat(dto.getSourceParentItemId()).isNull();
        assertThat(dto.getSourceChildItemId()).isNull();
    }

    @Test
    void dispatchBatchPackagesSalesOrderAssemblyRelationsFromSourceItem() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        SalesOrderItemAssemblyRelationMapper relationMapper = mock(SalesOrderItemAssemblyRelationMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderItemAssemblyRelation relation = new SalesOrderItemAssemblyRelation();
        relation.setParentItemId(9001L);
        relation.setChildItemId(9002L);
        relation.setQtyPerParent(new BigDecimal("3.25"));
        relation.setSortNo(2);
        relation.setSourceParentItemId(5101);
        relation.setSourceChildItemId(5102);
        when(allocationMapper.selectById(5)).thenReturn(allocation(5, "PRODUCTION_REQUIRED", "PENDING"));
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(itemMapper.selectById(9001L)).thenReturn(salesItem(9001L, "SO-ASM", "A"));
        when(itemMapper.selectById(9002L)).thenReturn(salesItem(9002L, "SO-CH", "A1"));
        when(client.dispatch(any())).thenReturn(success("MES-PROD-SO", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                null, null, relationMapper, itemMapper);

        Map<String, Object> result = service.dispatchBatch(List.of(5), 9105L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAssemblyRelations()).hasSize(1);
        MesProductionAssemblyRelationDTO dto = requestCaptor.getValue().getAssemblyRelations().get(0);
        assertThat(dto.getParentItemId()).isEqualTo(9001L);
        assertThat(dto.getChildItemId()).isEqualTo(9002L);
        assertThat(dto.getParentPartNo()).isEqualTo("SO-ASM");
        assertThat(dto.getParentVersion()).isEqualTo("A");
        assertThat(dto.getChildPartNo()).isEqualTo("SO-CH");
        assertThat(dto.getChildVersion()).isEqualTo("A1");
        assertThat(dto.getQtyPerParent()).isEqualByComparingTo(new BigDecimal("3.25"));
        assertThat(dto.getSortNo()).isEqualTo(2);
        assertThat(dto.getSourceParentItemId()).isEqualTo(5101);
        assertThat(dto.getSourceChildItemId()).isEqualTo(5102);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchBatchSendsStructuredProcessRouteWithoutInternalCosts() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        OrderDeliveryTicketAllocation allocation = allocation(8, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("sortNo", 10),
                Map.entry("processDetailId", 7001L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("hourlyRate", 10.0),
                Map.entry("lineCost", 15.0)
        )));
        when(allocationMapper.selectById(8)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-ROUTE", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(8), 9108L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getProcessRoute()).isInstanceOf(List.class);
        List<?> routeRows = (List<?>) requestCaptor.getValue().getProcessRoute();
        assertThat(routeRows).hasSize(1);
        Object route = routeRows.get(0);
        assertThat(route.getClass().getSimpleName()).isEqualTo("MesProductionProcessRouteDTO");
        Map<String, Object> serialized = OBJECT_MAPPER.convertValue(route, new TypeReference<>() {
        });
        assertThat(serialized)
                .containsEntry("processCode", "PACKAGING")
                .containsEntry("categoryName", "包装")
                .containsEntry("detailName", "精细包装")
                .containsEntry("unitWorkHours", 1.5)
                .containsEntry("sortNo", 10)
                .doesNotContainKeys("processDetailId", "hourlyRate", "unitRate", "lineCost", "cost", "amount");
    }

    @Test
    void dispatchBatchContinuesWithEmptyAssemblyRelationsWhenSourceHasNoRelations() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        SalesOrderItemAssemblyRelationMapper relationMapper = mock(SalesOrderItemAssemblyRelationMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        when(allocationMapper.selectById(6)).thenReturn(allocation(6, "PRODUCTION_REQUIRED", "PENDING"));
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(client.dispatch(any())).thenReturn(success("MES-PROD-EMPTY", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                null, null, relationMapper, itemMapper);

        Map<String, Object> result = service.dispatchBatch(List.of(6), 9106L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAssemblyRelations()).isEmpty();
        verify(itemMapper, never()).selectById(any());
    }

    @Test
    void dispatchBatchMarksRowFailedWhenAssemblyRelationQueryFails() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        SalesOrderItemAssemblyRelationMapper relationMapper = mock(SalesOrderItemAssemblyRelationMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        when(allocationMapper.selectById(7)).thenReturn(allocation(7, "PRODUCTION_REQUIRED", "PENDING"));
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenThrow(new RuntimeException("relation query failed"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                null, null, relationMapper, itemMapper);

        Map<String, Object> result = service.dispatchBatch(List.of(7), 9107L);

        assertThat(result)
                .containsEntry("successCount", 0)
                .containsEntry("failedCount", 1)
                .containsEntry("skippedCount", 0);
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getMesProductionStatus()).isEqualTo("FAILED");
        assertThat(updateCaptor.getValue().getMesProductionMessage()).contains("relation query failed");
        verify(client, never()).dispatch(any());
    }

    @Test
    void dispatchBatchWritesFailedStatusWhenMesReturnsFailure() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        AuditService auditService = mock(AuditService.class);
        when(allocationMapper.selectById(1)).thenReturn(allocation(1, "PRODUCTION_REQUIRED", "PENDING"));
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        MesProductionDispatchResp failure = new MesProductionDispatchResp();
        failure.setSuccess(false);
        failure.setMessage("route missing");
        when(client.dispatch(any())).thenReturn(failure);
        wireDispatchService(service, allocationMapper, null, client, auditService, null);

        Map<String, Object> result = service.dispatchBatch(List.of(1), 9002L);

        assertThat(result)
                .containsEntry("successCount", 0)
                .containsEntry("failedCount", 1)
                .containsEntry("skippedCount", 0);
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateCaptor =
                ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getMesProductionStatus()).isEqualTo("FAILED");
        assertThat(updateCaptor.getValue().getMesProductionMessage()).contains("route missing");
        verify(auditService).record(
                eq("PRODUCTION_ITEM_MES_PRODUCTION_FAILED"),
                eq("order_delivery_ticket_allocation"),
                eq(1),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("allocationId=1 taskId=9002"),
                contains("route missing")
        );
    }

    @Test
    void dispatchBatchSkipsSuccessAndProcessingRowsWithoutCallingMes() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        when(allocationMapper.selectById(1)).thenReturn(allocation(1, "PRODUCTION_REQUIRED", "SUCCESS"));
        when(allocationMapper.selectById(2)).thenReturn(allocation(2, "PRODUCTION_REQUIRED", "PROCESSING"));
        wireDispatchService(service, allocationMapper, null, client, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(1, 2), 9003L);

        assertThat(result)
                .containsEntry("successCount", 0)
                .containsEntry("failedCount", 0)
                .containsEntry("skippedCount", 2);
        verify(client, never()).dispatch(any());
        verify(allocationMapper, never()).updateById(any());
    }

    @Test
    void failedRowsCanRetryAndDataScopeChecksParentTickets() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        OrderDeliveryTicketAllocation failed = allocation(3, "PRODUCTION_REQUIRED", "FAILED");
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(103);
        when(allocationMapper.selectById(3)).thenReturn(failed);
        when(ticketMapper.selectById(103)).thenReturn(ticket);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-3", "accepted"));
        wireDispatchService(service, allocationMapper, ticketMapper, client, null, dataScopeService);

        service.assertCanAccessAllocations(List.of(3));
        Map<String, Object> result = service.dispatchBatch(List.of(3), 9004L);

        assertThat(result).containsEntry("successCount", 1);
        verify(dataScopeService).assertCanAccessDeliveryTicket(ticket);
        verify(client).dispatch(any());
    }

    @Test
    void lockedItemMesProductionBatchReqCarriesAllocationIdsOnly() {
        LockedItemMesProductionBatchReq req = new LockedItemMesProductionBatchReq();
        req.setAllocationIds(List.of(1, 2));

        assertThat(req.getAllocationIds()).containsExactly(1, 2);
    }

    private void wireTaskService(
            SystemTaskService service,
            SystemTaskMapper taskMapper,
            ThreadPoolTaskExecutor taskExecutor,
            AuditService auditService,
            MesProductionDispatchService dispatchService) {
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "mesProductionDispatchService", dispatchService);
    }

    private void wireDispatchService(
            MesProductionDispatchService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderDeliveryTicketMapper ticketMapper,
            MesProductionDispatchClient client,
            AuditService auditService,
            DataScopeService dataScopeService) {
        wireDispatchService(service, allocationMapper, ticketMapper, client, auditService, dataScopeService,
                null, null, null, null);
    }

    private void wireDispatchService(
            MesProductionDispatchService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderDeliveryTicketMapper ticketMapper,
            MesProductionDispatchClient client,
            AuditService auditService,
            DataScopeService dataScopeService,
            ItemAssemblyRelationMapper itemAssemblyRelationMapper,
            ItemQuoteVersionMapper itemQuoteVersionMapper,
            SalesOrderItemAssemblyRelationMapper salesOrderItemAssemblyRelationMapper,
            SalesOrderItemMapper salesOrderItemMapper) {
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesProductionDispatchClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "itemAssemblyRelationMapper", itemAssemblyRelationMapper);
        ReflectionTestUtils.setField(service, "itemQuoteVersionMapper", itemQuoteVersionMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemAssemblyRelationMapper", salesOrderItemAssemblyRelationMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String allocationType, String productionStatus) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        row.setId(id);
        row.setTicketId(100 + id);
        row.setRfqId(88);
        row.setItemId(77);
        row.setSourceType("SALES_ORDER");
        row.setSourceOrderId(3001L);
        row.setSourceItemId(9001L);
        row.setAllocationType(allocationType);
        row.setQty(4);
        row.setTicketQty(10);
        row.setMaterialNo("MAT-001");
        row.setOrderType("SO01");
        row.setItemType("ASSEMBLY");
        row.setPartNo("P-100");
        row.setVersion("A");
        row.setRfqNo("SO-1");
        row.setCustomerName("ACME");
        row.setInternalProjectNo("IP-9");
        row.setCustomerOrderNo("CO-9");
        row.setCustomerProjectNo("CP-9");
        row.setMaterialName("6061");
        row.setEngineeringMaterialName("7075");
        row.setMaterialShape("板材");
        row.setSurfaceTreatment("Anodized");
        row.setRemark("Rush");
        row.setDescription("Bracket");
        row.setDeliveryDate(LocalDate.of(2026, 8, 12));
        row.setProcessRoute(List.of(Map.of("processName", "CNC")));
        row.setMaterialData(Map.of("shape", "plate"));
        row.setMaterialGrossWeight(12.3);
        row.setPartNetWeight(10.2);
        row.setMesProductionStatus(productionStatus);
        return row;
    }

    private MesProductionDispatchResp success(String externalId, String message) {
        MesProductionDispatchResp resp = new MesProductionDispatchResp();
        resp.setSuccess(true);
        resp.setExternalId(externalId);
        resp.setMessage(message);
        return resp;
    }

    private ItemQuoteVersion rfqItem(Integer id, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setPartNo(partNo);
        item.setVersion(version);
        return item;
    }

    private SalesOrderItem salesItem(Long id, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setPartNo(partNo);
        item.setVersion(version);
        return item;
    }
}
