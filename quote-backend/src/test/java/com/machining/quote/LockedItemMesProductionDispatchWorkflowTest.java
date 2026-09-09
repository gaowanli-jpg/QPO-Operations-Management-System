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

class LockedItemMesProductionDispatchWorkflowTest extends LockedItemMesProductionDispatchTestSupport {

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
        assertThat(request.getEngineeringMaterialCode()).isEqualTo("MAT-7075");
        assertThat(request.getEngineeringMaterialName()).isEqualTo("7075");
        assertThat(request.getEngineeringMaterialTypeCode()).isEqualTo("RAW_MATERIAL");
        assertThat(request.getEngineeringMaterialTypeName()).isEqualTo("原材料");
        assertThat(request.getEngineeringMaterialCategory()).isEqualTo("铝材");
        assertThat(request.getMaterialShape()).isEqualTo("板材");
        assertThat(request.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(request.getDescription()).isEqualTo("Bracket");
        assertThat(request.getItems()).singleElement().satisfies(item ->
                assertThat(item.getSaleOrderItemNo()).isNull());
        assertThat(request.getItems()).singleElement().satisfies(item ->
                assertThat(item.getCustomerRequirements()).isEqualTo("材料名称：6061\n表面处理：Anodized"));
        assertThat(request.getRemark()).isEqualTo("Rush");
        assertThat(request.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(request.getProcessRoute()).singleElement().satisfies(route -> {
            assertThat(route.getProcessCode()).isNull();
            assertThat(route.getCategoryName()).isNull();
            assertThat(route.getDetailName()).isEqualTo("CNC");
            assertThat(route.getUnitWorkHours()).isNull();
            assertThat(route.getSortNo()).isNull();
        });
        assertThat(request.getMaterialGrossWeight()).isEqualTo(12.3);
        assertThat(request.getPartNetWeight()).isEqualTo(10.2);
        assertThat(request.getBom()).singleElement().satisfies(bom -> {
            assertThat(bom.getItemCode()).isEqualTo("MAT-7075");
            assertThat(bom.getQuantity()).isEqualByComparingTo(new BigDecimal("12.3"));
        });
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
        assertThat(requestCaptor.getValue().getBom()).singleElement().satisfies(bom -> {
            assertThat(bom.getItemCode()).isEqualTo("MAT-7075");
            assertThat(bom.getQuantity()).isEqualByComparingTo(new BigDecimal("12.3"));
        });
    }

    @Test
    void dispatchBatchAllowsAssemblyParentWithoutMaterialBomAndStillSendsRelations() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        ItemAssemblyRelationMapper relationMapper = mock(ItemAssemblyRelationMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketAllocation allocation = allocation(14, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setSourceType("RFQ");
        allocation.setSourceOrderId(214L);
        allocation.setSourceItemId(1501L);
        allocation.setItemType("组合件");
        allocation.setPartNo("ASM-1501");
        allocation.setVersion("B");
        allocation.setEngineeringMaterialCode(null);
        allocation.setMaterialGrossWeight(0.0);
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setParentItemId(1501);
        relation.setChildItemId(1502);
        relation.setChildQtyPerParent(2.0);
        relation.setSortNo(3);
        when(allocationMapper.selectById(14)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        when(itemMapper.selectById(1501)).thenReturn(rfqItem(1501, "ASM-1501", "B"));
        when(itemMapper.selectById(1502)).thenReturn(rfqItem(1502, "CH-1502", "A"));
        when(client.dispatch(any())).thenReturn(success("MES-PROD-ASM", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                relationMapper, itemMapper, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(14), 9114L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBom()).isEmpty();
        assertThat(requestCaptor.getValue().getAssemblyRelations()).hasSize(1);
    }

    @Test
    void dispatchBatchAllowsSalesOrderNormalItemWithMaterialNoButNoBomSnapshot() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        OrderDeliveryTicketAllocation allocation = allocation(15, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setItemType("NORMAL");
        allocation.setEngineeringMaterialCode(null);
        allocation.setMaterialGrossWeight(null);
        allocation.setMaterialData(null);
        allocation.setProcessRoute(null);
        when(allocationMapper.selectById(15)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-NORMAL", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(15), 9115L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq request = requestCaptor.getValue();
        assertThat(request.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(request.getBom()).isEmpty();
        assertThat(request.getProcessRoute()).isEmpty();
        assertThat(request.getAssemblyRelations()).isEmpty();
    }

    @Test
    void dispatchBatchAllowsSalesOrderAssemblyWithoutBomOrChildRelations() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        SalesOrderItemAssemblyRelationMapper relationMapper = mock(SalesOrderItemAssemblyRelationMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketAllocation allocation = allocation(16, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setEngineeringMaterialCode(null);
        allocation.setMaterialGrossWeight(null);
        allocation.setProcessRoute(null);
        when(allocationMapper.selectById(16)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(client.dispatch(any())).thenReturn(success("MES-PROD-ASM-EMPTY", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null,
                null, null, relationMapper, itemMapper);

        Map<String, Object> result = service.dispatchBatch(List.of(16), 9116L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq request = requestCaptor.getValue();
        assertThat(request.getBom()).isEmpty();
        assertThat(request.getProcessRoute()).isEmpty();
        assertThat(request.getAssemblyRelations()).isEmpty();
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
                .containsEntry("processTypeCode", "PACKAGING")
                .containsEntry("programNum", "10")
                .doesNotContainKeys("processCode", "categoryName", "detailName", "unitWorkHours", "sortNo",
                        "processDetailId", "hourlyRate", "unitRate", "lineCost", "cost", "amount");
        assertThat(serialized.get("workHour").toString()).isEqualTo("1.5");
    }

    @Test
    void dispatchBatchUsesMesSyncedDictionarySnapshotForProductionPayload() {
        MesProductionDispatchService service = new MesProductionDispatchService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionDispatchClient client = mock(MesProductionDispatchClient.class);
        OrderDeliveryTicketAllocation allocation = allocation(9, "PRODUCTION_REQUIRED", "PENDING");
        allocation.setEngineeringMaterialCode("MES-MAT-42CRMO");
        allocation.setEngineeringMaterialName("42CrMo MES 同步材料");
        allocation.setMaterialGrossWeight(18.75);
        allocation.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "MES"),
                Map.entry("sortNo", 20),
                Map.entry("processCode", "MES-PT-CNC"),
                Map.entry("categoryName", "MES 数控加工"),
                Map.entry("detailName", "工时工艺-CNC精加工"),
                Map.entry("unitWorkHours", 2.25)
        )));
        when(allocationMapper.selectById(9)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-SYNCED", "accepted"));
        wireDispatchService(service, allocationMapper, null, client, null, null);

        Map<String, Object> result = service.dispatchBatch(List.of(9), 9109L);

        assertThat(result).containsEntry("successCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq request = requestCaptor.getValue();
        assertThat(request.getProcessRoute()).singleElement().satisfies(route -> {
            assertThat(route.getProcessTypeCode()).isEqualTo("MES-PT-CNC");
            assertThat(route.getProcessName()).isEqualTo("工时工艺-CNC精加工");
            assertThat(route.getWorkHour()).isEqualByComparingTo(new BigDecimal("2.25"));
        });
        assertThat(request.getBom()).singleElement().satisfies(bom -> {
            assertThat(bom.getItemCode()).isEqualTo("MES-MAT-42CRMO");
            assertThat(bom.getQuantity()).isEqualByComparingTo(new BigDecimal("18.75"));
        });
    }
}
