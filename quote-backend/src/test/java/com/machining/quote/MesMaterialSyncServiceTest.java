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
import com.machining.quote.service.mes.MesMaterialRequestValidator;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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

class MesMaterialSyncServiceTest extends MesMaterialSyncTestSupport {

    @Test
    void syncMaterialNoWritesMaterialNumberAndSuccessStatus() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        SourceItemMaterialNoSyncService sourceSyncService = mock(SourceItemMaterialNoSyncService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setDescription("Part description");
        ticket.setMaterialName("Material note");
        ticket.setSurfaceTreatment("Anodized");
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("/uploads/drawings/p100.pdf"));
        MesMaterialResolveResp response = mesProductResponse("MES-7788", "EXISTING_PRODUCT", "MP202608050001", "created");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.getData().setBizId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        when(sourceSyncService.syncConfirmedMaterialNo(any(), eq("MES-7788"), eq("created")))
                .thenReturn(new SourceItemMaterialNoSyncService.MaterialNoSyncResult(1, 1, 1, 1, 0));
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);
        ReflectionTestUtils.setField(service, "mesRequestMapper", mesRequestMapper);
        ReflectionTestUtils.setField(service, "sourceItemMaterialNoSyncService", sourceSyncService);

        Map<String, Object> result = service.syncMaterialNo(123, 9001L);

        assertThat(result)
                .containsEntry("ticketId", 123)
                .containsEntry("taskId", 9001L)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-7788")
                .containsEntry("sourceSyncTargetCount", 2)
                .containsEntry("sourceSyncUpdatedCount", 2)
                .containsEntry("sourceSyncIdempotentCount", 0)
                .containsEntry("rfqSourceUpdatedCount", 1)
                .containsEntry("salesOrderSourceUpdatedCount", 1);
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq req = reqCaptor.getValue();
        assertThat(req.getMesRequestId()).isNotBlank();
        assertThat(ReflectionTestUtils.getField(req, "customerCode")).isEqualTo("C-ACME");
        assertThat(req.getItems()).hasSize(1);
        assertThat(req.getItems().get(0).getDrawingNo()).isEqualTo("P-100");
        assertThat(req.getItems().get(0).getDrawingVersion()).isEqualTo("A");
        assertThat(serialize(req).path("items").get(0).has("drawingUrls")).isFalse();
        assertThat(req.getItems().get(0).getRemark()).isEqualTo("release remark");
        assertThat(req.getItems().get(0).getCustomerRequirements())
                .isEqualTo("材料名称：Material note\n表面处理：Anodized");
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(mesRequestMapper).insert(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMesRequestId()).isEqualTo(req.getMesRequestId());
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<OrderDeliveryTicket> processing = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).update(processing.capture(), any());
        assertThat(processing.getValue().getMesSyncStatus()).isEqualTo("PROCESSING");
        assertThat(processing.getValue().getMesRequestId()).isEqualTo(req.getMesRequestId());
        ArgumentCaptor<OrderDeliveryTicket> successCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(successCaptor.capture());
        OrderDeliveryTicket success = successCaptor.getValue();
        assertThat(success.getMaterialNo()).isEqualTo("MES-7788");
        assertThat(success.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(success.getMesSyncMessage()).isEqualTo("created");
        assertThat(success.getMesRequestId()).isEqualTo(req.getMesRequestId());
        assertThat(success.getMesExternalTaskId()).isEqualTo("MP202608050001");
        assertThat(success.getMesSyncedAt()).isNotNull();
        ArgumentCaptor<Object> auditSummaryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_SUCCESS"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                auditSummaryCaptor.capture(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("ticketId=123 taskId=9001"),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) auditSummaryCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("status", "SUCCESS")
                .containsEntry("mesRequestId", req.getMesRequestId())
                .containsEntry("externalTraceId", "MP202608050001")
                .containsEntry("sourceSyncTargetCount", 2)
                .containsEntry("sourceSyncUpdatedCount", 2)
                .containsEntry("rfqSourceUpdatedCount", 1)
                .containsEntry("salesOrderSourceUpdatedCount", 1);
    }

    @Test
    void syncMaterialNoMarksCallbackPendingWhenMesAcceptsWithoutMaterialNumber() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper mesRequestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        when(ticketMapper.selectById(123)).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("/uploads/drawings/p100.pdf"));
        MesMaterialResolveResp response = mesProductResponse(null, "PENDING_CREATE", "MP202608040002", "MES已受理，后续回调");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.getData().setBizId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);
        ReflectionTestUtils.setField(service, "mesRequestMapper", mesRequestMapper);

        Map<String, Object> result = service.syncMaterialNo(123, 9006L);

        assertThat(result)
                .containsEntry("ticketId", 123)
                .containsEntry("taskId", 9006L)
                .containsEntry("status", "CALLBACK_PENDING")
                .containsEntry("externalTraceId", "MP202608040002");
        assertThat(result).doesNotContainKey("materialNo");
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(updateCaptor.capture());
        OrderDeliveryTicket update = updateCaptor.getValue();
        assertThat(update.getMesSyncStatus()).isEqualTo("CALLBACK_PENDING");
        assertThat(update.getMesSyncMessage()).isEqualTo("MES已受理，后续回调");
        assertThat(update.getMesExternalTaskId()).isEqualTo("MP202608040002");
        assertThat(update.getMesRequestId()).isNotBlank();
        ArgumentCaptor<Object> auditSummaryCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_ACCEPTED"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                auditSummaryCaptor.capture(),
                eq("DELIVERY"),
                eq("CALLBACK_PENDING"),
                contains("ticketId=123 taskId=9006"),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) auditSummaryCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("status", "CALLBACK_PENDING")
                .containsEntry("mesRequestId", update.getMesRequestId())
                .containsEntry("externalTraceId", "MP202608040002");
        verify(auditService, never()).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_FAILED"),
                anyString(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void syncMaterialNoWritesConfirmedMaterialBackToSalesOrderItemForSalesOrderTicket() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setRfqId(null);
        ticket.setItemId(null);
        ticket.setRfqNo("SOQPO-001");
        ticket.setCustomerName("客户A");
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9001L);
        ticket.setSourceItemId(5001L);
        SalesOrderItem salesItem = new SalesOrderItem();
        salesItem.setId(5001L);
        salesItem.setDrawingUrl("/uploads/sales-order/p100.pdf");
        SalesOrderHeader salesOrder = new SalesOrderHeader();
        salesOrder.setId(9001L);
        salesOrder.setCustomerCode("C-SO");
        salesOrder.setCustomerName("客户A");
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        when(salesOrderItemMapper.selectById(5001L)).thenReturn(salesItem);
        when(salesOrderItemMapper.selectByIdForUpdate(5001L)).thenReturn(salesItem);
        when(salesOrderHeaderMapper.selectById(9001L)).thenReturn(salesOrder);
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("MES-SO-7788");
        response.setMessage("created");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
        SourceItemMaterialNoSyncService sourceSyncService = new SourceItemMaterialNoSyncService();
        ReflectionTestUtils.setField(sourceSyncService, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "sourceItemMaterialNoSyncService", sourceSyncService);

        Map<String, Object> result = service.syncMaterialNo(123, 9005L);

        assertThat(result)
                .containsEntry("sourceType", "SALES_ORDER")
                .containsEntry("sourceItemId", 5001L)
                .containsEntry("materialNo", "MES-SO-7788")
                .containsEntry("sourceSyncTargetCount", 1)
                .containsEntry("sourceSyncUpdatedCount", 1)
                .containsEntry("rfqSourceUpdatedCount", 0)
                .containsEntry("salesOrderSourceUpdatedCount", 1);
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        assertThat(ReflectionTestUtils.getField(reqCaptor.getValue(), "customerCode")).isEqualTo("C-SO");
        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(salesOrderItemMapper).updateById(itemCaptor.capture());
        SalesOrderItem update = itemCaptor.getValue();
        assertThat(update.getId()).isEqualTo(5001L);
        assertThat(update.getMaterialNo()).isEqualTo("MES-SO-7788");
        assertThat(update.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(update.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(update.getMaterialNoSourceTicketId()).isEqualTo(123L);
        assertThat(update.getMaterialNoSyncedAt()).isNotNull();
        assertThat(update.getMaterialNoMessage()).isEqualTo("created");
    }

    @Test
    void syncMaterialNoDoesNotSendDrawingUrlsToMesEvenWhenTicketHasDrawing() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        when(ticketMapper.selectById(123)).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("/uploads/drawings/p100.pdf"));
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("MES-7788");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        service.syncMaterialNo(123, 9004L);

        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getItems()).hasSize(1);
        assertThat(serialize(reqCaptor.getValue()).path("items").get(0).has("drawingUrls")).isFalse();
        JsonNode payload = serialize(reqCaptor.getValue());
        assertThat(payload.path("items").get(0).has("drawingUrls")).isFalse();
        assertThat(payload.path("items").get(0).has("drawingUrl")).isFalse();
    }

    @Test
    void mesMaterialRequestValidatorAllowsMissingDrawingUrls() {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setBizId("QPO-MAT-NO-DRAWING");
        req.setInternalProjectNo("IP-9");
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        req.setItems(List.of(item));

        assertThatCode(() -> new MesMaterialRequestValidator().validate(req))
                .doesNotThrowAnyException();
    }

    @Test
    void syncMaterialNoCallsMesWithoutDrawingUrlWhenTicketAndSourceItemHaveNoDrawing() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setDrawingUrl(null);
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item(null));
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("MES-NO-DRAWING");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        Map<String, Object> result = service.syncMaterialNo(123, 9010L);

        assertThat(result)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-NO-DRAWING");
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq.Item sentItem = reqCaptor.getValue().getItems().get(0);
        assertThat(sentItem.getDrawingNo()).isEqualTo("P-100");
        assertThat(sentItem.getDrawingVersion()).isEqualTo("A");
        assertThat(serialize(reqCaptor.getValue()).path("items").get(0).has("drawingUrls")).isFalse();
    }
}
