package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.OrderDeliveryController;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialSyncFailureGuardTest extends MesMaterialSyncTestSupport {

    @Test
    void syncMaterialNoMarksTicketFailedAndRethrowsWhenMesRejects() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        when(ticketMapper.selectById(123)).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("http://qpo.local/p100.pdf"));
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(false);
        response.setMessage("MES part missing");
        when(client.resolveMaterial(any())).thenReturn(response);
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        assertThatThrownBy(() -> service.syncMaterialNo(123, 9002L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MES part missing");

        ArgumentCaptor<OrderDeliveryTicket> processing = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).update(processing.capture(), any());
        assertThat(processing.getValue().getMesSyncStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<OrderDeliveryTicket> failedCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(failedCaptor.capture());
        OrderDeliveryTicket failed = failedCaptor.getValue();
        assertThat(failed.getMesSyncStatus()).isEqualTo("FAILED");
        assertThat(failed.getMesSyncMessage()).isEqualTo("MES part missing");
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_FAILED"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("ticketId=123 taskId=9002"),
                eq("MES part missing")
        );
    }

    @Test
    void syncMaterialNoRejectsSuccessfulMesResponseWithoutRequestId() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        when(ticketMapper.selectById(123)).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("http://qpo.local/p100.pdf"));
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("MES-7788");
        when(client.resolveMaterial(any())).thenReturn(response);
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        assertThatThrownBy(() -> service.syncMaterialNo(123, 9008L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("请求ID");

        ArgumentCaptor<OrderDeliveryTicket> failedCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(failedCaptor.capture());
        assertThat(failedCaptor.getValue().getMesSyncStatus()).isEqualTo("FAILED");
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_FAILED"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("ticketId=123 taskId=9008"),
                contains("请求ID")
        );
    }

    @Test
    void syncMaterialNoRejectsAlreadySuccessfulTicketWithoutOverwritingMaterialNumber() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setMesSyncStatus("SUCCESS");
        ticket.setMaterialNo("MAT-LOCKED");
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        assertThatThrownBy(() -> service.syncMaterialNo(123, 9003L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MES");

        verify(ticketMapper, never()).updateById(any());
        verify(ticketMapper, never()).update(any(OrderDeliveryTicket.class), any());
        verify(client, never()).resolveMaterial(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void syncMaterialNoRejectsCallbackPendingTicketWithoutCallingMesAgain() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setMesSyncStatus("CALLBACK_PENDING");
        ticket.setMesRequestId("MES-OLD-123");
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        assertThatThrownBy(() -> service.syncMaterialNo(123, 9007L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("callback");

        verify(ticketMapper, never()).updateById(any());
        verify(ticketMapper, never()).update(any(OrderDeliveryTicket.class), any());
        verify(client, never()).resolveMaterial(any());
    }
}
