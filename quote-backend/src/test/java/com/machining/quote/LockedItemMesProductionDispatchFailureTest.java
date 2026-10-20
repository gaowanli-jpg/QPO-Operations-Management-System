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

class LockedItemMesProductionDispatchFailureTest extends LockedItemMesProductionDispatchTestSupport {

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
        when(allocationMapper.selectBatchIds(List.of(3))).thenReturn(List.of(failed));
        when(ticketMapper.selectBatchIds(List.of(103))).thenReturn(List.of(ticket));
        when(allocationMapper.update(any(OrderDeliveryTicketAllocation.class), any())).thenReturn(1);
        when(client.dispatch(any())).thenReturn(success("MES-PROD-3", "accepted"));
        wireDispatchService(service, allocationMapper, ticketMapper, client, null, dataScopeService);

        service.assertCanAccessAllocations(List.of(3));
        Map<String, Object> result = service.dispatchBatch(List.of(3), 9004L);

        assertThat(result).containsEntry("successCount", 1);
        verify(allocationMapper).selectBatchIds(List.of(3));
        verify(ticketMapper).selectBatchIds(List.of(103));
        verify(dataScopeService).assertCanAccessDeliveryTickets(List.of(ticket));
        verify(dataScopeService, never()).assertCanAccessDeliveryTicket(ticket);
        verify(client).dispatch(any());
    }
}
