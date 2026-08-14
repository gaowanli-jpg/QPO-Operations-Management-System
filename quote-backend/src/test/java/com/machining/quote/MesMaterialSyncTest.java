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
import com.machining.quote.service.BusinessFileService;
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

class MesMaterialSyncTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path MES_SYNC_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesMaterialSyncService.java");

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
        when(ticketMapper.selectById(123)).thenReturn(ticket());
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
        assertThat(req.getItems().get(0).getDrawingUrls()).containsExactly("http://qpo.local/uploads/drawings/p100.pdf");
        assertThat(req.getItems().get(0).getRemark()).isEqualTo("release remark");
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
    void syncMaterialNoSendsExternalReadableDrawingUrlToMesItemDrawingUrls() {
        MesMaterialSyncService service = syncService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        MesMaterialClient client = mock(MesMaterialClient.class);
        AuditService auditService = mock(AuditService.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        when(ticketMapper.selectById(123)).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(itemMapper.selectById(77)).thenReturn(item("/uploads/drawings/p100.pdf"));
        when(businessFileService.externalReadableUrl("/uploads/drawings/p100.pdf"))
                .thenReturn("http://minio-public/qpo-drawings/drawings/p100.pdf");
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("MES-7788");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService, businessFileService);

        service.syncMaterialNo(123, 9004L);

        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getItems()).hasSize(1);
        assertThat(reqCaptor.getValue().getItems().get(0).getDrawingUrls())
                .containsExactly("http://minio-public/qpo-drawings/drawings/p100.pdf");
        verify(businessFileService).externalReadableUrl("/uploads/drawings/p100.pdf");
        JsonNode payload = serialize(reqCaptor.getValue());
        assertThat(payload.path("items").get(0).path("drawingUrls").get(0).asText())
                .isEqualTo("http://minio-public/qpo-drawings/drawings/p100.pdf");
        assertThat(payload.path("items").get(0).has("drawingUrl")).isFalse();
    }

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

    @Test
    void deliveredItemMesSyncOnlyExposesBatchEndpoint() {
        List<String> controllerMethods = Arrays.stream(OrderDeliveryController.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        List<String> postMappings = Arrays.stream(OrderDeliveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .toList();
        List<String> taskMethods = Arrays.stream(SystemTaskService.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(controllerMethods)
                .contains("syncMesMaterialBatch")
                .doesNotContain("syncMesMaterial");
        assertThat(postMappings)
                .contains("/tickets/mes-material/batch")
                .doesNotContain("/tickets/{ticketId}/mes-material");
        assertThat(taskMethods)
                .contains("submitMesMaterialBatchSyncTask")
                .doesNotContain("submitMesMaterialSyncTask");
    }

    @Test
    void mesMaterialSyncTaskFlowFreezesStatusAndAuditNames() throws IOException {
        String taskService = read(SYSTEM_TASK_SERVICE);
        String syncService = read(MES_SYNC_SERVICE);

        assertThat(taskService)
                .as("System tasks must submit MES material requests through the batch entry")
                .contains(
                        "MES_MATERIAL_BATCH_SYNC",
                        "submitMesMaterialBatchSyncTask",
                        "DELIVERY_MES_MATERIAL_SYNC_SUBMIT",
                        "deliveryIntegrationBatchService.syncMesMaterialBatch"
                )
                .doesNotContain("submitMesMaterialSyncTask");
        assertThat(syncService)
                .as("Ticket status transitions and audit actions must be stable before implementation continues")
                .contains(
                        "PROCESSING",
                        "SUCCESS",
                        "FAILED",
                        "setMesSyncStatus",
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        "DELIVERY_MES_MATERIAL_SYNC_ACCEPTED",
                        "DELIVERY_MES_MATERIAL_SYNC_FAILED",
                        "MES material sync started",
                        "MES material sync completed",
                        "MES material sync accepted",
                        "MES material sync failed",
                        "log.error(\"MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}\""
                );
    }

    @Test
    void mesMaterialDtosCarryTheExternalContractFields() {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId("QPO-MAT-1");
        req.setCustomerCode("GSS1");
        req.setCustomerName("测试客户");
        req.setInternalProjectNo("IP-1");
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setDrawingUrls(List.of("http://qpo.local/p100.pdf"));
        item.setRemark("Rush");
        item.setCustomerRequirements("物料描述：Bracket\n表面处理：Anodized");
        req.setItems(List.of(item));

        JsonNode payload = serialize(req);
        assertThat(payload.path("bizId").asText()).isEqualTo("QPO-MAT-1");
        assertThat(payload.path("customerCode").asText()).isEqualTo("GSS1");
        assertThat(payload.path("items")).hasSize(1);
        assertThat(payload.path("items").get(0).path("drawingNo").asText()).isEqualTo("P-100");
        assertThat(payload.path("items").get(0).path("drawingVersion").asText()).isEqualTo("A");
        assertThat(payload.path("items").get(0).path("drawingUrls").get(0).asText())
                .isEqualTo("http://qpo.local/p100.pdf");
        assertThat(payload.path("items").get(0).path("remark").asText()).isEqualTo("Rush");
        assertThat(payload.path("items").get(0).path("customerRequirements").asText())
                .isEqualTo("物料描述：Bracket\n表面处理：Anodized");
        assertThat(payload.has("mesRequestId")).isFalse();
        assertThat(payload.path("items").get(0).has("partNo")).isFalse();
        assertThat(payload.path("items").get(0).has("drawingUrl")).isFalse();

        MesMaterialResolveResp numericCode = new MesMaterialResolveResp();
        numericCode.setCode(0);
        assertThat(numericCode.getSuccess()).isTrue();
        MesMaterialResolveResp stringCode = new MesMaterialResolveResp();
        stringCode.setCode("0");
        assertThat(stringCode.getSuccess()).isTrue();
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private JsonNode serialize(Object value) {
        try {
            return OBJECT_MAPPER.valueToTree(value);
        } catch (IllegalArgumentException e) {
            throw new AssertionError(e);
        }
    }

    private MesMaterialSyncService syncService() {
        return new MesMaterialSyncService();
    }

    private void wireSyncService(
            MesMaterialSyncService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            MesMaterialClient client,
            AuditService auditService) {
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        when(businessFileService.externalReadableUrl(anyString())).thenAnswer(invocation -> {
            String drawingUrl = invocation.getArgument(0);
            if (drawingUrl.startsWith("http://") || drawingUrl.startsWith("https://")) {
                return drawingUrl;
            }
            return "http://qpo.local" + (drawingUrl.startsWith("/") ? drawingUrl : "/" + drawingUrl);
        });
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService, businessFileService);
    }

    private void wireSyncService(
            MesMaterialSyncService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            MesMaterialClient client,
            AuditService auditService,
            BusinessFileService businessFileService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "mesMaterialClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
    }

    private OrderDeliveryTicket ticket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
        ticket.setRemark("release remark");
        ticket.setMesSyncStatus("PENDING");
        return ticket;
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerCode("C-ACME");
        order.setCustomerName("ACME");
        return order;
    }

    private ItemQuoteVersion item(String drawingUrl) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(77);
        item.setDrawingUrl(drawingUrl);
        return item;
    }

    private MesMaterialResolveResp mesProductResponse(String productCode, String status, String taskNo, String message) {
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setCode(0);
        response.setMessage(message);
        MesMaterialResolveResp.Data data = new MesMaterialResolveResp.Data();
        MesMaterialResolveResp.Item item = new MesMaterialResolveResp.Item();
        item.setTaskId(2L);
        item.setTaskNo(taskNo);
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setStatus(status);
        item.setProductId(productCode == null ? null : 20L);
        item.setProductCode(productCode);
        item.setProductName(productCode == null ? null : "业务演示成品");
        data.setItems(java.util.List.of(item));
        response.setData(data);
        return response;
    }
}
