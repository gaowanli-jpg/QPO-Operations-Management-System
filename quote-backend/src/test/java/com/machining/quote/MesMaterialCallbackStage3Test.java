package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.MesIntegrationCallbackController;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MesMaterialCallbackStage3Test {
    private static final Path CALLBACK_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesMaterialCallbackReq.java");
    private static final Path CALLBACK_RESP = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesMaterialCallbackResp.java");

    @Test
    void materialCallbackConfirmsPendingTicketAndSalesOrderItem() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = salesOrderTicket("CALLBACK_PENDING", null);
        OrderDeliveryTicketMesRequest request = requestLedger("CALLBACK_PENDING", 123);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(5001L);
        when(requestMapper.selectOne(any(Wrapper.class))).thenReturn(request);
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        when(salesOrderItemMapper.selectById(5001L)).thenReturn(item);
        wire(service, ticketMapper, requestMapper, salesOrderItemMapper, auditService);

        MesMaterialCallbackResp response = service.handleMaterialCallback(callback("MAT-NEW"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMesRequestId()).isEqualTo("MES-REQ-123");
        assertThat(response.getMaterialNo()).isEqualTo("MAT-NEW");
        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(ticketCaptor.capture());
        OrderDeliveryTicket ticketUpdate = ticketCaptor.getValue();
        assertThat(ticketUpdate.getId()).isEqualTo(123);
        assertThat(ticketUpdate.getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(ticketUpdate.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(ticketUpdate.getMesRequestId()).isEqualTo("MES-REQ-123");
        assertThat(ticketUpdate.getMesExternalTaskId()).isEqualTo("MES-TASK-1");
        assertThat(ticketUpdate.getMesCallbackReceivedAt()).isNotNull();
        assertThat(ticketUpdate.getMesCallbackMessage()).isEqualTo("MES completed");
        assertThat(ticketUpdate.getMesSyncedAt()).isNotNull();
        ArgumentCaptor<SalesOrderItem> salesCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(salesOrderItemMapper).updateById(salesCaptor.capture());
        SalesOrderItem salesUpdate = salesCaptor.getValue();
        assertThat(salesUpdate.getId()).isEqualTo(5001L);
        assertThat(salesUpdate.getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(salesUpdate.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(salesUpdate.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(salesUpdate.getMaterialNoSourceTicketId()).isEqualTo(123L);
        assertThat(salesUpdate.getMaterialNoSyncedAt()).isNotNull();
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(requestMapper).update(requestCaptor.capture(), any(Wrapper.class));
        OrderDeliveryTicketMesRequest requestUpdate = requestCaptor.getValue();
        assertThat(requestUpdate.getRequestStatus()).isEqualTo("SUCCESS");
        assertThat(requestUpdate.getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(requestUpdate.getMesExternalTaskId()).isEqualTo("MES-TASK-1");
        assertThat(requestUpdate.getCallbackMessage()).isEqualTo("MES completed");
        assertThat(requestUpdate.getCallbackReceivedAt()).isNotNull();
        assertThat(requestUpdate.getCompletedAt()).isNotNull();
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(Map.class),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("mesRequestId=MES-REQ-123"),
                isNull()
        );
    }

    @Test
    void duplicateSameCallbackReturnsSuccessWithoutRewritingTicketOrSalesOrderItem() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicket ticket = salesOrderTicket("SUCCESS", "MAT-NEW");
        ticket.setMesSyncedAt(LocalDateTime.now().minusMinutes(5));
        when(requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger("SUCCESS", 123));
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        wire(service, ticketMapper, requestMapper, salesOrderItemMapper, mock(AuditService.class));

        MesMaterialCallbackResp response = service.handleMaterialCallback(callback("MAT-NEW"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("已处理");
        verify(ticketMapper, never()).updateById(any());
        verify(salesOrderItemMapper, never()).updateById(any());
    }

    @Test
    void conflictingCallbackMaterialNumberDoesNotOverwriteConfirmedTicket() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger("SUCCESS", 123));
        when(ticketMapper.selectById(123)).thenReturn(salesOrderTicket("SUCCESS", "MAT-OLD"));
        wire(service, ticketMapper, requestMapper, salesOrderItemMapper, auditService);

        assertThatThrownBy(() -> service.handleMaterialCallback(callback("MAT-NEW")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("冲突");

        verify(ticketMapper, never()).updateById(any());
        verify(salesOrderItemMapper, never()).updateById(any());
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(Map.class),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("mesRequestId=MES-REQ-123"),
                contains("冲突")
        );
    }

    @Test
    void callbackLocatesTicketFromRequestLedgerWithoutExternalTicketId() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicket ticket = salesOrderTicket("CALLBACK_PENDING", null);
        when(requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger("CALLBACK_PENDING", 123));
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        wire(service, ticketMapper, requestMapper, salesOrderItemMapper, mock(AuditService.class));

        MesMaterialCallbackResp response = service.handleMaterialCallback(callback("MAT-NEW"));

        assertThat(response.getSuccess()).isTrue();
        verify(ticketMapper).selectById(123);
        verify(ticketMapper).updateById(any(OrderDeliveryTicket.class));
    }

    @Test
    void staleMesRequestCallbackDoesNotOverwriteNewerTicketRequest() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = salesOrderTicket("CALLBACK_PENDING", null);
        ticket.setMesRequestId("MES-REQ-LATEST");
        when(requestMapper.selectOne(any(Wrapper.class))).thenReturn(requestLedger("CALLBACK_PENDING", 123));
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        wire(service, ticketMapper, requestMapper, salesOrderItemMapper, auditService);

        assertThatThrownBy(() -> service.handleMaterialCallback(callback("MAT-OLD")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("旧 MES 回调");

        verify(ticketMapper, never()).updateById(any());
        verify(salesOrderItemMapper, never()).updateById(any());
        ArgumentCaptor<OrderDeliveryTicketMesRequest> requestCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketMesRequest.class);
        verify(requestMapper).update(requestCaptor.capture(), any(Wrapper.class));
        assertThat(requestCaptor.getValue().getRequestStatus()).isEqualTo("STALE");
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(Map.class),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("mesRequestId=MES-REQ-123"),
                contains("旧 MES 回调")
        );
    }

    @Test
    void callbackControllerRejectsMissingOrWrongTokenBeforeCallingService() {
        MesMaterialSyncService service = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = controller(service, configService);
        when(configService.hasValidBearerToken(any(), isNull())).thenReturn(false);
        when(configService.hasValidBearerToken(any(), eq("Bearer wrong"))).thenReturn(false);

        ResponseEntity<MesMaterialCallbackResp> missing = controller.materialCallback(null, callback("MAT-NEW"));
        ResponseEntity<MesMaterialCallbackResp> wrong = controller.materialCallback("Bearer wrong", callback("MAT-NEW"));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().getSuccess()).isFalse();
        verifyNoInteractions(service);
    }

    @Test
    void callbackControllerAcceptsBearerTokenAndDelegates() {
        MesMaterialSyncService service = mock(MesMaterialSyncService.class);
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        MesMaterialCallbackResp expected = new MesMaterialCallbackResp();
        expected.setSuccess(true);
        expected.setMesRequestId("MES-REQ-123");
        when(service.handleMaterialCallback(any(MesMaterialCallbackReq.class))).thenReturn(expected);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        when(configService.hasValidBearerToken(any(), eq("Bearer callback-token"))).thenReturn(true);
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", service);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback("Bearer callback-token", callback("MAT-NEW"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(service).handleMaterialCallback(any(MesMaterialCallbackReq.class));
    }

    @Test
    void callbackDtosDoNotExposeInternalTicketId() throws IOException {
        assertThat(read(CALLBACK_REQ)).doesNotContain("private Integer ticketId;");
        assertThat(read(CALLBACK_RESP)).doesNotContain("private Integer ticketId;");
    }

    @Test
    void callbackRequestAcceptsMesFieldAliases() throws IOException {
        MesMaterialCallbackReq req = new ObjectMapper().readValue("""
                {
                  "bizId": "MES-REQ-123",
                  "status": "SUCCESS",
                  "productCode": "MAT-NEW",
                  "externalTraceId": "MP202608050001",
                  "msg": "MES completed"
                }
                """, MesMaterialCallbackReq.class);

        assertThat(req.getMesRequestId()).isEqualTo("MES-REQ-123");
        assertThat(req.getMaterialNo()).isEqualTo("MAT-NEW");
        assertThat(req.getMessage()).isEqualTo("MES completed");
        assertThat(req.getExternalTraceId()).isEqualTo("MP202608050001");
    }

    @Test
    void callbackControllerReturnsBadRequestWhenRequiredIdentityIsMissing() {
        MesMaterialSyncService service = syncService();
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = controller(service, configService);
        MesMaterialCallbackReq req = callback("MAT-NEW");
        req.setMesRequestId(" ");
        when(configService.hasValidBearerToken(any(), eq("Bearer callback-token"))).thenReturn(true);

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback("Bearer callback-token", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("MES 请求ID");
    }

    private MesMaterialSyncService syncService() {
        return new MesMaterialSyncService();
    }

    private MesIntegrationCallbackController controller(
            MesMaterialSyncService syncService,
            SystemInboundCallbackConfigService configService) {
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", syncService);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);
        return controller;
    }

    private void wire(
            MesMaterialSyncService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderDeliveryTicketMesRequestMapper requestMapper,
            SalesOrderItemMapper salesOrderItemMapper,
            AuditService auditService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesRequestMapper", requestMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
    }

    private MesMaterialCallbackReq callback(String materialNo) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("MES-REQ-123");
        req.setExternalTraceId("MES-TASK-1");
        req.setStatus("SUCCESS");
        req.setMaterialNo(materialNo);
        req.setMessage("MES completed");
        return req;
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private OrderDeliveryTicketMesRequest requestLedger(String status, Integer ticketId) {
        OrderDeliveryTicketMesRequest request = new OrderDeliveryTicketMesRequest();
        request.setId(9001L);
        request.setTicketId(ticketId);
        request.setMesRequestId("MES-REQ-123");
        request.setRequestStatus(status);
        return request;
    }

    private OrderDeliveryTicket salesOrderTicket(String status, String materialNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(9001L);
        ticket.setSourceItemId(5001L);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setMesRequestId("MES-REQ-123");
        ticket.setMesSyncStatus(status);
        ticket.setMaterialNo(materialNo);
        return ticket;
    }
}
