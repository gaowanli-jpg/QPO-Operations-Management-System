package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketMesRequest;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.SystemTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialSyncTest {
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path MES_SYNC_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesMaterialSyncService.java");
    private static final Path MES_REQ = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesMaterialResolveReq.java");
    private static final Path MES_RESP = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesMaterialResolveResp.java");

    @Test
    void submitMesMaterialSyncTaskCreatesSystemTaskAndDispatchesAsyncWork() {
        OrderDeliveryTicket ticket = ticket();
        SystemTaskService service = new SystemTaskService();
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        MesMaterialSyncService syncService = mock(MesMaterialSyncService.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AtomicReference<SystemTask> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(501L);
            inserted.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(501L)).thenAnswer(invocation -> inserted.get());
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "orderDeliveryTicketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "mesMaterialSyncService", syncService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        SystemTask task = service.submitMesMaterialSyncTask(123);

        assertThat(task.getId()).isEqualTo(501L);
        assertThat(task.getTaskType()).isEqualTo("MES_MATERIAL_SYNC");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        Map<String, Object> requestJson = new LinkedHashMap<>((Map<String, Object>) task.getRequestJson());
        assertThat(requestJson)
                .containsEntry("ticketId", 123)
                .containsEntry("rfqId", 88)
                .containsEntry("partNo", "P-100");
        verify(dataScopeService).assertCanAccessDeliveryTicket(ticket);
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_SUBMIT"),
                eq("system_task"),
                eq(501L),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("taskId=501"),
                isNull()
        );
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void syncMaterialNoWritesMaterialNumberAndSuccessStatus() {
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
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setMaterialNo("  MES-7788  ");
        response.setMessage("created");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
            response.setExternalTraceId("MES-TASK-7788");
            return response;
        });
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);
        ReflectionTestUtils.setField(service, "mesRequestMapper", mesRequestMapper);

        Map<String, Object> result = service.syncMaterialNo(123, 9001L);

        assertThat(result)
                .containsEntry("ticketId", 123)
                .containsEntry("taskId", 9001L)
                .containsEntry("status", "SUCCESS")
                .containsEntry("materialNo", "MES-7788");
        ArgumentCaptor<MesMaterialResolveReq> reqCaptor = ArgumentCaptor.forClass(MesMaterialResolveReq.class);
        verify(client).resolveMaterial(reqCaptor.capture());
        MesMaterialResolveReq req = reqCaptor.getValue();
        assertThat(req.getMesRequestId()).isNotBlank();
        assertThat(ReflectionTestUtils.getField(req, "customerCode")).isEqualTo("C-ACME");
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
        assertThat(success.getMesExternalTaskId()).isEqualTo("MES-TASK-7788");
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
                .containsEntry("externalTraceId", "MES-TASK-7788");
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
        MesMaterialResolveResp response = new MesMaterialResolveResp();
        response.setSuccess(true);
        response.setCallbackRequired(true);
        response.setStatus("CALLBACK_PENDING");
        response.setExternalTraceId("MES-TASK-PENDING");
        response.setMessage("MES已受理，后续回调");
        when(client.resolveMaterial(any())).thenAnswer(invocation -> {
            MesMaterialResolveReq req = invocation.getArgument(0);
            response.setMesRequestId(req.getMesRequestId());
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
                .containsEntry("externalTraceId", "MES-TASK-PENDING");
        assertThat(result).doesNotContainKey("materialNo");
        ArgumentCaptor<OrderDeliveryTicket> updateCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(updateCaptor.capture());
        OrderDeliveryTicket update = updateCaptor.getValue();
        assertThat(update.getMesSyncStatus()).isEqualTo("CALLBACK_PENDING");
        assertThat(update.getMesSyncMessage()).isEqualTo("MES已受理，后续回调");
        assertThat(update.getMesExternalTaskId()).isEqualTo("MES-TASK-PENDING");
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
                .containsEntry("externalTraceId", "MES-TASK-PENDING");
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

        Map<String, Object> result = service.syncMaterialNo(123, 9005L);

        assertThat(result)
                .containsEntry("sourceType", "SALES_ORDER")
                .containsEntry("sourceItemId", 5001L)
                .containsEntry("materialNo", "MES-SO-7788");
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
    void syncMaterialNoSendsExternalReadableDrawingUrlToMesWithoutRenamingField() {
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
        assertThat(reqCaptor.getValue().getDrawingUrl())
                .isEqualTo("http://minio-public/qpo-drawings/drawings/p100.pdf");
        verify(businessFileService).externalReadableUrl("/uploads/drawings/p100.pdf");
        assertThat(readQuietly(MES_REQ)).contains("private String drawingUrl;");
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
    void deliveredItemMesSyncEndpointIsPermissionProtected() throws IOException {
        String controller = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(controller)
                .as("MES material sync must be triggered from delivered-item module only")
                .contains(
                        "SystemTaskService",
                        "@PostMapping(\"/tickets/{ticketId}/mes-material\")",
                        "@RequirePermission(\"market:delivered-item:mes-sync\")",
                        "submitMesMaterialSyncTask(ticketId)"
                );
    }

    @Test
    void mesMaterialSyncTaskFlowFreezesStatusAndAuditNames() throws IOException {
        String taskService = read(SYSTEM_TASK_SERVICE);
        String syncService = read(MES_SYNC_SERVICE);

        assertThat(taskService)
                .as("System tasks need a distinct MES material sync task type")
                .contains(
                        "MES_MATERIAL_SYNC",
                        "submitMesMaterialSyncTask",
                        "DELIVERY_MES_MATERIAL_SYNC_SUBMIT",
                        "dataScopeService.assertCanAccessDeliveryTicket"
                );
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
    void mesMaterialDtosCarryTheExternalContractFields() throws IOException {
        assertThat(read(MES_REQ))
                .contains(
                        "class MesMaterialResolveReq",
                        "private String mesRequestId;",
                        "private String customerCode;",
                        "private String customerName;",
                        "private String partNo;",
                        "private String version;",
                        "private String drawingUrl;",
                        "private String internalProjectNo;"
                )
                .doesNotContain(
                        "private Integer ticketId;",
                        "private String sourceType;",
                        "private Integer rfqId;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;",
                        "private String orderNo;",
                        "private String rfqNo;"
                );
        assertThat(read(MES_RESP))
                .contains(
                        "class MesMaterialResolveResp",
                        "private Boolean success;",
                        "private String mesRequestId;",
                        "private String status;",
                        "private Boolean callbackRequired;",
                        "private String materialNo;",
                        "private String message;",
                        "private String externalTraceId;"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
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

    private String readQuietly(Path path) {
        try {
            return read(path);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private OrderDeliveryTicket ticket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setInternalProjectNo("IP-9");
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
}
