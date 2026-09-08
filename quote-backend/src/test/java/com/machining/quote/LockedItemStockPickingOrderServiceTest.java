package com.machining.quote;

import com.machining.quote.dto.mes.MesProductionOrderCreateReq;
import com.machining.quote.dto.mes.MesProductionOrderCreateResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.mes.LockedItemStockPickingOrderService;
import com.machining.quote.service.mes.MesProductionOrderCreateClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LockedItemStockPickingOrderServiceTest {

    @Test
    void dispatchBatchBuildsStockPickingOrderRequestAndMarksSuccess() {
        LockedItemStockPickingOrderService service = new LockedItemStockPickingOrderService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionOrderCreateClient client = mock(MesProductionOrderCreateClient.class);
        AuditService auditService = mock(AuditService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T02:20:30Z"), ZoneId.of("Asia/Shanghai"));
        OrderDeliveryTicketAllocation allocation = allocation(1, "IN_STOCK_LOCK", null);
        MesProductionOrderCreateResp response = success("QPO-STOCK-IGNORED", item("MES-ID-1", "MO-0001", "MAT-001"));
        when(allocationMapper.selectById(1)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.create(any())).thenAnswer(invocation -> {
            MesProductionOrderCreateReq request = invocation.getArgument(0);
            return success(request.getBizId(), item("MES-ID-1", "MO-0001", "MAT-001"));
        });
        wire(service, allocationMapper, null, client, auditService, null, clock);

        Map<String, Object> result = service.dispatchBatch(List.of(1), 9901L);

        assertThat(result)
                .containsEntry("taskType", "LOCKED_ITEM_STOCK_PICKING_ORDER")
                .containsEntry("taskId", 9901L)
                .containsEntry("totalCount", 1)
                .containsEntry("successCount", 1)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 0);
        ArgumentCaptor<MesProductionOrderCreateReq> requestCaptor = ArgumentCaptor.forClass(MesProductionOrderCreateReq.class);
        verify(client).create(requestCaptor.capture());
        MesProductionOrderCreateReq request = requestCaptor.getValue();
        assertThat(request.getBizId()).startsWith("QPO-STOCK-");
        assertThat(request.getItems()).singleElement().satisfies(row -> {
            assertThat(row.getOrderType()).isEqualTo("STOCK_PICKING");
            assertThat(row.getProductCode()).isEqualTo("MAT-001");
            assertThat(row.getPlanCount()).isEqualByComparingTo(new BigDecimal("4"));
            assertThat(row.getPlanStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 9, 10, 20, 30));
            assertThat(row.getPlanEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 30, 0, 0, 0));
            assertThat(row.getRemark()).isEqualTo("工单备注");
            assertThat(row.getSaleOrderNo()).isEqualTo("SO-2026-001");
            assertThat(row.getSaleOrderItemNo()).isEqualTo("9001");
            assertThat(row.getSaleRemark()).isEqualTo("材料名称：导入材料描述\n表面处理：阳极氧化");
            assertThat(row.getCustomerPoNo()).isEqualTo("");
            assertThat(row.getCustomerProjectNo()).isEqualTo("CP-9");
            assertThat(row.getCustomerOrderNo()).isEqualTo("CO-9");
            assertThat(row.getInternalProjectNo()).isEqualTo("IP-9");
        });
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateCaptor.capture());
        OrderDeliveryTicketAllocation successUpdate = updateCaptor.getValue();
        assertThat(successUpdate.getId()).isEqualTo(1);
        assertThat(successUpdate.getStockPickingStatus()).isEqualTo("SUCCESS");
        assertThat(successUpdate.getStockPickingBizId()).isEqualTo(request.getBizId());
        assertThat(successUpdate.getStockPickingExternalId()).isEqualTo("MO-0001");
        verify(auditService).record(
                eq("LOCKED_ITEM_STOCK_PICKING_SUCCESS"),
                eq("order_delivery_ticket_allocation"),
                eq(1),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("allocationId=1 taskId=9901"),
                isNull()
        );
    }

    @Test
    void dispatchBatchReusesExistingBizIdForRetryAndKeepsResultUnknownOnTimeout() {
        LockedItemStockPickingOrderService service = new LockedItemStockPickingOrderService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionOrderCreateClient client = mock(MesProductionOrderCreateClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T02:20:30Z"), ZoneId.of("Asia/Shanghai"));
        OrderDeliveryTicketAllocation allocation = allocation(2, "IN_PRODUCTION_LOCK", "RESULT_UNKNOWN");
        allocation.setStockPickingBizId("QPO-STOCK-OLD-BIZ");
        when(allocationMapper.selectById(2)).thenReturn(allocation);
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.create(any())).thenThrow(new DataCenterCallException(
                DataCenterEndpointRegistry.MES_STOCK_PICKING_ORDER_CREATE,
                DataCenterCallException.FailureCertainty.RESULT_UNKNOWN,
                null,
                "read timed out",
                null
        ));
        wire(service, allocationMapper, null, client, null, null, clock);

        Map<String, Object> result = service.dispatchBatch(List.of(2), 9902L);

        assertThat(result)
                .containsEntry("successCount", 0)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 1);
        ArgumentCaptor<MesProductionOrderCreateReq> requestCaptor = ArgumentCaptor.forClass(MesProductionOrderCreateReq.class);
        verify(client).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getBizId()).isEqualTo("QPO-STOCK-OLD-BIZ");
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStockPickingStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(updateCaptor.getValue().getStockPickingBizId()).isEqualTo("QPO-STOCK-OLD-BIZ");
    }

    @Test
    void dispatchBatchFailsInvalidRowsWithoutCallingMes() {
        LockedItemStockPickingOrderService service = new LockedItemStockPickingOrderService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        MesProductionOrderCreateClient client = mock(MesProductionOrderCreateClient.class);
        OrderDeliveryTicketAllocation missingDate = allocation(3, "IN_STOCK_LOCK", "PENDING");
        missingDate.setDeliveryDate(null);
        when(allocationMapper.selectById(3)).thenReturn(missingDate);
        wire(service, allocationMapper, null, client, null, null, Clock.systemDefaultZone());

        Map<String, Object> result = service.dispatchBatch(List.of(3), 9903L);

        assertThat(result)
                .containsEntry("successCount", 0)
                .containsEntry("failedCount", 1)
                .containsEntry("skippedCount", 0);
        verify(client, never()).create(any());
        ArgumentCaptor<OrderDeliveryTicketAllocation> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStockPickingStatus()).isEqualTo("FAILED");
        assertThat(updateCaptor.getValue().getStockPickingMessage()).contains("内部目标交期");
    }

    @Test
    void assertCanAccessAllocationsChecksParentDeliveryTicketScope() {
        LockedItemStockPickingOrderService service = new LockedItemStockPickingOrderService();
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        OrderDeliveryTicketAllocation allocation = allocation(4, "IN_STOCK_LOCK", "PENDING");
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(104);
        when(allocationMapper.selectById(4)).thenReturn(allocation);
        when(ticketMapper.selectById(104)).thenReturn(ticket);
        wire(service, allocationMapper, ticketMapper, mock(MesProductionOrderCreateClient.class), null, dataScopeService, Clock.systemDefaultZone());

        service.assertCanAccessAllocations(List.of(4));

        verify(dataScopeService).assertCanAccessDeliveryTicket(ticket);
    }

    private void wire(
            LockedItemStockPickingOrderService service,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderDeliveryTicketMapper ticketMapper,
            MesProductionOrderCreateClient client,
            AuditService auditService,
            DataScopeService dataScopeService,
            Clock clock) {
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesProductionOrderCreateClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "clock", clock);
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String allocationType, String stockPickingStatus) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        row.setId(id);
        row.setTicketId(100 + id);
        row.setSourceType("SALES_ORDER");
        row.setSourceOrderId(3001L);
        row.setSourceItemId(9001L);
        row.setAllocationType(allocationType);
        row.setQty(4);
        row.setTicketQty(10);
        row.setMaterialNo("MAT-001");
        row.setPartNo("P-100");
        row.setVersion("A");
        row.setRfqNo("SO-2026-001");
        row.setCustomerName("ACME");
        row.setInternalProjectNo("IP-9");
        row.setCustomerOrderNo("CO-9");
        row.setCustomerProjectNo("CP-9");
        row.setDescription("导入材料描述");
        row.setSurfaceTreatment("阳极氧化");
        row.setRemark("工单备注");
        row.setDeliveryDate(LocalDate.of(2026, 9, 30));
        row.setStockPickingStatus(stockPickingStatus);
        return row;
    }

    private MesProductionOrderCreateResp success(String bizId, MesProductionOrderCreateResp.Item item) {
        MesProductionOrderCreateResp resp = new MesProductionOrderCreateResp();
        resp.setCode(0);
        MesProductionOrderCreateResp.DataEnvelope data = new MesProductionOrderCreateResp.DataEnvelope();
        data.setBizId(bizId);
        data.setItems(List.of(item));
        resp.setData(data);
        return resp;
    }

    private MesProductionOrderCreateResp.Item item(String id, String productOrder, String productCode) {
        MesProductionOrderCreateResp.Item item = new MesProductionOrderCreateResp.Item();
        item.setId(id);
        item.setProductOrder(productOrder);
        item.setOrderType("STOCK_PICKING");
        item.setProductCode(productCode);
        item.setPlanCount(new BigDecimal("4"));
        item.setStatus(0);
        return item;
    }
}
