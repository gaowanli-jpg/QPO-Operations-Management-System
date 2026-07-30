package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
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
        verify(dataScopeService).assertCanAccessOrder(88, null);
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
        when(client.resolveMaterial(any())).thenReturn(response);
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireSyncService(service, ticketMapper, orderMapper, itemMapper, client, auditService);

        Map<String, Object> result = service.syncMaterialNo(123, 9001L);

        assertThat(result)
                .containsEntry("ticketId", 123)
                .containsEntry("taskId", 9001L)
                .containsEntry("materialNo", "MES-7788");
        ArgumentCaptor<OrderDeliveryTicket> processing = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).update(processing.capture(), any());
        assertThat(processing.getValue().getMesSyncStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<OrderDeliveryTicket> successCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(successCaptor.capture());
        OrderDeliveryTicket success = successCaptor.getValue();
        assertThat(success.getMaterialNo()).isEqualTo("MES-7788");
        assertThat(success.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(success.getMesSyncMessage()).isEqualTo("created");
        assertThat(success.getMesSyncedAt()).isNotNull();
        verify(auditService).record(
                eq("DELIVERY_MES_MATERIAL_SYNC_SUCCESS"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("ticketId=123 taskId=9001"),
                isNull()
        );
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
        when(client.resolveMaterial(any())).thenReturn(response);
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
                        "dataScopeService.assertCanAccessOrder"
                );
        assertThat(syncService)
                .as("Ticket status transitions and audit actions must be stable before implementation continues")
                .contains(
                        "PROCESSING",
                        "SUCCESS",
                        "FAILED",
                        "setMesSyncStatus",
                        "DELIVERY_MES_MATERIAL_SYNC_SUCCESS",
                        "DELIVERY_MES_MATERIAL_SYNC_FAILED",
                        "MES material sync started",
                        "MES material sync completed",
                        "MES material sync failed",
                        "log.error(\"MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}\""
                );
    }

    @Test
    void mesMaterialDtosCarryTheExternalContractFields() throws IOException {
        assertThat(read(MES_REQ))
                .contains(
                        "class MesMaterialResolveReq",
                        "private Integer ticketId;",
                        "private Integer rfqId;",
                        "private String rfqNo;",
                        "private String customerName;",
                        "private String partNo;",
                        "private String version;",
                        "private String drawingUrl;",
                        "private String internalProjectNo;"
                );
        assertThat(read(MES_RESP))
                .contains(
                        "class MesMaterialResolveResp",
                        "private Boolean success;",
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
