package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesProductionDispatchService;
import com.machining.quote.service.mes.ProductionItemMaterialNoRefreshService;
import com.machining.quote.service.mes.ProductionItemMaterialNoRefreshWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionItemMaterialNoRefreshServiceTest {

    private final OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
    private final OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
    private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
    private final OrderDeliveryTicketMesRequestMapper mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
    private final MesMaterialClient mesMaterialClient = mock(MesMaterialClient.class);
    private final ProductionItemMaterialNoRefreshWriter refreshWriter = mock(ProductionItemMaterialNoRefreshWriter.class);
    private final MesProductionDispatchService dispatchService = mock(MesProductionDispatchService.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void refreshOverwritesConfirmedMaterialNoAndKeepsAuditTrail() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(1)).thenReturn(salesItem(101L, "CS-OLD-001"));
        when(mesMaterialClient.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return successResponse(req.getBizId(), "CS-NEW-002");
        });
        when(refreshWriter.apply(eq(1), eq("CS-NEW-002"), anyString(), anyString(), any(), any()))
                .thenReturn(new ProductionItemMaterialNoRefreshWriter.RefreshWriteResult("CS-OLD-001", "CS-NEW-002", 1, 1, 0));

        Map<String, Object> result = service().refreshBatch(List.of(11), "MES 删除旧料号并重新建号", 88L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("failedCount", 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("status", "SUCCESS")
                .containsEntry("previousMaterialNo", "CS-OLD-001")
                .containsEntry("newMaterialNo", "CS-NEW-002")
                .containsEntry("ticketId", 1);
        verify(refreshWriter).apply(eq(1), eq("CS-NEW-002"), anyString(), anyString(), any(), any());
        verify(auditService).record(
                eq("PRODUCTION_ITEM_MATERIAL_NO_REFRESH_SUCCESS"),
                eq("order_delivery_ticket"),
                eq(1),
                any(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                anyString(),
                isNull());
    }

    @Test
    void refreshSendsDrawingNoAndVersionToMesInsteadOfReusingStoredMaterialNo() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(1)).thenReturn(salesItem(101L, "CS-OLD-001"));
        when(mesMaterialClient.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return successResponse(req.getBizId(), "CS-NEW-002");
        });
        when(refreshWriter.apply(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ProductionItemMaterialNoRefreshWriter.RefreshWriteResult("CS-OLD-001", "CS-NEW-002", 1, 1, 0));

        service().refreshBatch(List.of(11), "料号填写错误后已更正", 89L);

        ArgumentCaptor<MesMaterialResolveReq> captor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(mesMaterialClient).resolveMaterial(captor.capture());
        MesMaterialResolveReq request = captor.getValue();
        assertThat(request.getBizId()).startsWith("QPO-MATR-");
        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getDrawingNo()).isEqualTo("DWG-001");
        assertThat(request.getItems().get(0).getDrawingVersion()).isEqualTo("A");
    }

    @Test
    void refreshRejectsRowThatAlreadyDispatchedSuccessfully() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "SUCCESS")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "SUCCESS")));

        Map<String, Object> result = service().refreshBatch(List.of(11), "MES 删除旧料号并重新建号", 90L);

        assertThat(result).containsEntry("successCount", 0).containsEntry("failedCount", 1);
        verify(mesMaterialClient, never()).resolveMaterial(any());
        verify(refreshWriter, never()).apply(any(), anyString(), anyString(), anyString(), any(), any());
        verify(auditService).record(
                eq("PRODUCTION_ITEM_MATERIAL_NO_REFRESH_FAILED"),
                eq("order_delivery_ticket"),
                eq(1),
                any(),
                any(),
                eq("DELIVERY"),
                eq("FAILED"),
                anyString(),
                anyString());
    }

    @Test
    void refreshRejectsRowWithInFlightDispatchRequest() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "RESULT_UNKNOWN")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "RESULT_UNKNOWN")));

        Map<String, Object> result = service().refreshBatch(List.of(11), "MES 删除旧料号并重新建号", 91L);

        assertThat(result).containsEntry("failedCount", 1);
        verify(mesMaterialClient, never()).resolveMaterial(any());
    }

    @Test
    void refreshFailsWithoutWritingWhenMesReturnsTheSameMaterialNo() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(1)).thenReturn(salesItem(101L, "CS-OLD-001"));
        when(mesMaterialClient.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return successResponse(req.getBizId(), "CS-OLD-001");
        });

        Map<String, Object> result = service().refreshBatch(List.of(11), "MES 删除旧料号并重新建号", 92L);

        assertThat(result).containsEntry("successCount", 0).containsEntry("failedCount", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(String.valueOf(rows.get(0).get("message"))).contains("未发生变化");
        verify(refreshWriter, never()).apply(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void refreshGroupsMultipleAllocationsOfTheSameTicketIntoOneMesCall() {
        stubTicket(1, "CS-OLD-001");
        List<OrderDeliveryTicketAllocation> allocations = List.of(
                allocation(11, 1, "FAILED"),
                allocation(12, 1, "PENDING"),
                allocation(13, 1, "FAILED"));
        when(allocationMapper.selectBatchIds(any())).thenReturn(allocations);
        when(allocationMapper.selectByTicketId(1)).thenReturn(allocations);
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(1)).thenReturn(salesItem(101L, "CS-OLD-001"));
        when(mesMaterialClient.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            return successResponse(req.getBizId(), "CS-NEW-002");
        });
        when(refreshWriter.apply(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ProductionItemMaterialNoRefreshWriter.RefreshWriteResult("CS-OLD-001", "CS-NEW-002", 3, 1, 0));

        Map<String, Object> result = service().refreshBatch(List.of(11, 12, 13), "MES 删除旧料号并重新建号", 93L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("totalCount", 1);
        verify(mesMaterialClient, times(1)).resolveMaterial(any());
    }

    @Test
    void refreshRequiresReason() {
        assertThatThrownBy(() -> service().refreshBatch(List.of(11), "  ", 94L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("请填写刷新料号的原因");
        verify(mesMaterialClient, never()).resolveMaterial(any());
    }

    @Test
    void refreshRecordsMesBusinessRejectionOnTheRequestRow() {
        stubTicket(1, "CS-OLD-001");
        when(allocationMapper.selectBatchIds(any())).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(allocationMapper.selectByTicketId(1)).thenReturn(List.of(allocation(11, 1, "PENDING")));
        when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(1)).thenReturn(salesItem(101L, "CS-OLD-001"));
        String mesMessage = "图号 DWG-001 版本 A 材质 用于法兰装调 已存在物料准备任务：MP202610270002";
        when(mesMaterialClient.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            MesMaterialResolveResp response = new MesMaterialResolveResp();
            response.setSuccess(false);
            response.setMesRequestId(req.getBizId());
            response.setMessage(mesMessage);
            return response;
        });

        Map<String, Object> result = service().refreshBatch(List.of(11), "MES 删除旧料号并重新建号", 95L);

        assertThat(result).containsEntry("successCount", 0).containsEntry("failedCount", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(String.valueOf(rows.get(0).get("message"))).isEqualTo(mesMessage);
        verify(refreshWriter, never()).apply(any(), anyString(), anyString(), anyString(), any(), any());

        ArgumentCaptor<OrderDeliveryTicketMesRequest> captor =
                ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(mesRequestMapper, times(1)).update(captor.capture(), any());
        assertThat(captor.getValue().getRequestStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getResponseMessage()).contains("已存在物料准备任务");
    }

    private ProductionItemMaterialNoRefreshService service() {
        ProductionItemMaterialNoRefreshService service = new ProductionItemMaterialNoRefreshService();
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "mesRequestMapper", mesRequestMapper);
        ReflectionTestUtils.setField(service, "mesMaterialClient", mesMaterialClient);
        ReflectionTestUtils.setField(service, "refreshWriter", refreshWriter);
        ReflectionTestUtils.setField(service, "mesProductionDispatchService", dispatchService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private void stubTicket(Integer ticketId, String materialNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(ticketId);
        ticket.setPartNo("DWG-001");
        ticket.setVersion("A");
        ticket.setMaterialNo(materialNo);
        ticket.setSourceType("SALES_ORDER");
        ticket.setDiscarded(false);
        ticket.setInternalProjectNo("PRJ-1");
        when(ticketMapper.selectById(ticketId)).thenReturn(ticket);
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, Integer ticketId, String productionStatus) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(ticketId);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setMesProductionStatus(productionStatus);
        return allocation;
    }

    private SalesOrderItem salesItem(Long id, String materialNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setMaterialNo(materialNo);
        return item;
    }

    private MesMaterialResolveResp successResponse(String bizId, String materialNo) {
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setCode(0);
        response.setMesRequestId(bizId);
        response.setMaterialNo(materialNo);
        response.setMessage("ok");
        return response;
    }
}
