package com.machining.quote;

import com.machining.quote.dto.wms.WmsAllocationResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SystemTaskService;
import com.machining.quote.service.wms.WmsAllocationClient;
import com.machining.quote.service.wms.WmsAllocationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class WmsAllocationTest {
    private static final Path WMS_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ALLOCATION_REMARK_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path ALLOCATION_ITEM_TYPE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path MES_PRODUCTION_DISPATCH_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path MATERIAL_SHAPE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path DELIVERY_TICKET = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicket.java");
    private static final Path DELIVERY_ALLOCATION = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");
    private static final Path ORDER_DELIVERY_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path WMS_ALLOCATION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsAllocationService.java");

    @Test
    @SuppressWarnings("unchecked")
    void submitWmsAllocationTaskCreatesSystemTaskAndDispatchesAsyncWork() {
        OrderDeliveryTicket ticket = ticket();
        ticket.setWmsAllocateStatus("PENDING");
        SystemTaskService service = new SystemTaskService();
        SystemTaskMapper taskMapper = mock(SystemTaskMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        WmsAllocationService allocationService = mock(WmsAllocationService.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AtomicReference<SystemTask> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            task.setId(701L);
            inserted.set(task);
            return 1;
        }).when(taskMapper).insert(any(SystemTask.class));
        when(taskMapper.selectById(701L)).thenAnswer(invocation -> inserted.get());
        when(ticketMapper.selectById(123)).thenReturn(ticket);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "orderDeliveryTicketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "taskExecutor", taskExecutor);
        ReflectionTestUtils.setField(service, "wmsAllocationService", allocationService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        SystemTask task = service.submitWmsAllocationTask(123);

        assertThat(task.getId()).isEqualTo(701L);
        assertThat(task.getTaskType()).isEqualTo("WMS_ALLOCATION");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        Map<String, Object> requestJson = new LinkedHashMap<>((Map<String, Object>) task.getRequestJson());
        assertThat(requestJson)
                .containsEntry("ticketId", 123)
                .containsEntry("rfqId", 88)
                .containsEntry("materialNo", "MAT-001")
                .containsEntry("demandQty", 10);
        verify(dataScopeService).assertCanAccessDeliveryTicket(ticket);
        verify(auditService).record(
                eq("DELIVERY_WMS_ALLOCATION_SUBMIT"),
                eq("system_task"),
                eq(701L),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("SUCCESS"),
                contains("taskId=701"),
                isNull()
        );
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void allocateWritesInventoryProductionAndRemainderRows() {
        WmsAllocationService service = new WmsAllocationService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        WmsAllocationClient client = mock(WmsAllocationClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        when(ticketMapper.selectOne(any())).thenReturn(ticket);
        when(orderMapper.selectById(88)).thenReturn(order());
        when(client.allocate(any())).thenReturn(successResponse(3, locks(lock("MO-001", 4), lock("MO-002", 2))));
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireAllocationService(service, ticketMapper, allocationMapper, orderMapper, client, auditService);

        Map<String, Object> result = service.allocate(123, 9001L);

        assertThat(result)
                .containsEntry("ticketId", 123)
                .containsEntry("taskId", 9001L)
                .containsEntry("materialNo", "MAT-001")
                .containsEntry("demandQty", 10)
                .containsEntry("allocationCount", 4)
                .containsEntry("inStockQty", 3)
                .containsEntry("inProductionQty", 6)
                .containsEntry("productionRequiredQty", 1)
                .containsEntry("processRouteCount", 1);
        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper, times(4)).insert(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues())
                .extracting(OrderDeliveryTicketAllocation::getAllocationType)
                .containsExactly("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK", "IN_PRODUCTION_LOCK", "PRODUCTION_REQUIRED");
        assertThat(allocationCaptor.getAllValues())
                .extracting(OrderDeliveryTicketAllocation::getQty)
                .containsExactly(3, 4, 2, 1);
        assertThat(allocationCaptor.getAllValues())
                .extracting(OrderDeliveryTicketAllocation::getWmsProductionOrderNo)
                .containsExactly(null, "MO-001", "MO-002", null);
        assertThat(allocationCaptor.getAllValues())
                .allSatisfy(row -> {
                    assertThat(row.getTicketId()).isEqualTo(123);
                    assertThat(row.getRfqId()).isEqualTo(88);
                    assertThat(row.getItemId()).isEqualTo(77);
                    assertThat(row.getMaterialNo()).isEqualTo("MAT-001");
                    assertThat(row.getPartNo()).isEqualTo("P-100");
                    assertThat(row.getVersion()).isEqualTo("A");
                    assertThat(row.getCustomerName()).isEqualTo("ACME");
                    assertThat(row.getInternalProjectNo()).isEqualTo("IP-9");
                    assertSnapshot(row, "getItemType", "组合件");
                    assertSnapshot(row, "getOrderType", "预测订单");
                    assertSnapshot(row, "getRfqNo", "RFQ-88");
                    assertSnapshot(row, "getCustomerOrderNo", "CO-9");
                    assertSnapshot(row, "getCustomerProjectNo", "CP-9");
                    assertSnapshot(row, "getMaterialName", "6061");
                    assertSnapshot(row, "getEngineeringMaterialCode", "EMC-7075");
                    assertSnapshot(row, "getEngineeringMaterialTypeCode", "RAW_MATERIAL");
                    assertSnapshot(row, "getEngineeringMaterialTypeName", "Raw Material");
                    assertSnapshot(row, "getEngineeringMaterialCategory", "ALUMINUM");
                    assertSnapshot(row, "getEngineeringMaterialName", "7075");
                    assertSnapshot(row, "getMaterialShape", "板材");
                    assertSnapshot(row, "getSurfaceTreatment", "Anodized");
                    assertSnapshot(row, "getRemark", "Rush note");
                    assertSnapshot(row, "getCustomerRequirement", "Customer needs burr-free edges");
                    assertSnapshot(row, "getDescription", "Bracket");
                    assertSnapshot(row, "getDeliveryDate", LocalDate.of(2026, 8, 12));
                    assertThat(row.getProcessRoute()).isEqualTo(ticket.getProcessRoute());
                    assertThat(row.getProcessRoute()).isNotSameAs(ticket.getProcessRoute());
                    assertStandardProcessRoute(row.getProcessRoute());
                    assertSnapshot(row, "getMaterialData", Map.of("shape", "板材", "name", "7075"));
                    assertSnapshot(row, "getMaterialGrossWeight", 12.345);
                    assertSnapshot(row, "getPartNetWeight", 10.2);
                    assertSnapshot(row, "getTicketQty", 10);
                    assertSnapshot(row, "getMesProductionStatus", "PENDING");
                    assertThat(row.getStatus()).isEqualTo("ACTIVE");
                    assertThat(row.getSourcePayload()).isNotNull();
                });
        ArgumentCaptor<OrderDeliveryTicket> processing = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).update(processing.capture(), any());
        assertThat(processing.getValue().getWmsAllocateStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<OrderDeliveryTicket> successCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(successCaptor.capture());
        OrderDeliveryTicket success = successCaptor.getValue();
        assertThat(success.getWmsAllocateStatus()).isEqualTo("SUCCESS");
        assertThat(success.getWmsAllocateMessage()).isEqualTo("allocated");
        assertThat(success.getWmsAllocatedAt()).isNotNull();
        verify(auditService).record(
                eq("DELIVERY_WMS_ALLOCATION_SUCCESS"),
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
    void allocateFailsWithoutWritingRowsWhenWmsOverAllocates() {
        WmsAllocationService service = new WmsAllocationService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        WmsAllocationClient client = mock(WmsAllocationClient.class);
        AuditService auditService = mock(AuditService.class);
        when(ticketMapper.selectOne(any())).thenReturn(ticket());
        when(orderMapper.selectById(88)).thenReturn(order());
        when(client.allocate(any())).thenReturn(successResponse(9, locks(lock("MO-001", 2))));
        when(ticketMapper.update(any(OrderDeliveryTicket.class), any())).thenReturn(1);
        wireAllocationService(service, ticketMapper, allocationMapper, orderMapper, client, auditService);

        assertThatThrownBy(() -> service.allocate(123, 9002L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("WMS locked quantity exceeds demand");

        verify(allocationMapper, never()).insert(any());
        ArgumentCaptor<OrderDeliveryTicket> processing = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).update(processing.capture(), any());
        assertThat(processing.getValue().getWmsAllocateStatus()).isEqualTo("PROCESSING");
        ArgumentCaptor<OrderDeliveryTicket> failedCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(ticketMapper).updateById(failedCaptor.capture());
        OrderDeliveryTicket failed = failedCaptor.getValue();
        assertThat(failed.getWmsAllocateStatus()).isEqualTo("FAILED");
        assertThat(failed.getWmsAllocateMessage()).contains("WMS locked quantity exceeds demand");
        verify(auditService).record(
                eq("DELIVERY_WMS_ALLOCATION_FAILED"),
                eq("order_delivery_ticket"),
                eq(123),
                isNull(),
                any(),
                eq("DELIVERY"),
                eq("FAILED"),
                contains("ticketId=123 taskId=9002"),
                contains("WMS locked quantity exceeds demand")
        );
    }

    @Test
    void allocateRejectsAlreadySuccessfulTicketWithoutChangingStatusOrRows() {
        WmsAllocationService service = new WmsAllocationService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        WmsAllocationClient client = mock(WmsAllocationClient.class);
        AuditService auditService = mock(AuditService.class);
        OrderDeliveryTicket ticket = ticket();
        ticket.setWmsAllocateStatus("SUCCESS");
        when(ticketMapper.selectOne(any())).thenReturn(ticket);
        wireAllocationService(service, ticketMapper, allocationMapper, orderMapper, client, auditService);

        assertThatThrownBy(() -> service.allocate(123, 9003L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("WMS");

        verify(ticketMapper, never()).updateById(any());
        verify(ticketMapper, never()).update(any(OrderDeliveryTicket.class), any());
        verify(allocationMapper, never()).insert(any());
        verify(client, never()).allocate(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void wmsAllocationEndpointIsPermissionProtected() throws IOException {
        String controller = read(ORDER_DELIVERY_CONTROLLER);

        assertThat(controller)
                .as("WMS allocation must be triggered from delivered-item module only")
                .contains(
                        "SystemTaskService",
                        "@PostMapping(\"/tickets/{ticketId}/wms-allocation\")",
                        "@RequirePermission(\"market:delivered-item:wms-allocate\")",
                        "submitWmsAllocationTask(ticketId)"
                );
    }

    @Test
    void wmsAllocationTaskFlowFreezesStatusAuditAndFileLogNames() throws IOException {
        String taskService = read(SYSTEM_TASK_SERVICE);
        String allocationService = read(WMS_ALLOCATION_SERVICE);

        assertThat(taskService)
                .as("System tasks need a distinct WMS allocation task type")
                .contains(
                        "WMS_ALLOCATION",
                        "submitWmsAllocationTask",
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "dataScopeService.assertCanAccessDeliveryTicket"
                );
        assertThat(allocationService)
                .as("Ticket status transitions and audit actions must be stable")
                .contains(
                        "PROCESSING",
                        "SUCCESS",
                        "FAILED",
                        "setWmsAllocateStatus",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "WMS allocation started",
                        "WMS allocation completed",
                        "WMS allocation failed",
                        "log.error(\"WMS allocation failed ticketId={} taskId={} rfqId={} materialNo={} processRouteCount={} durationMs={}\""
                );
    }

    @Test
    void wmsAllocationSuccessWritePathIsTransactionGuarded() throws IOException {
        String allocationService = read(WMS_ALLOCATION_SERVICE);

        assertThat(allocationService)
                .as("Allocation inserts and ticket SUCCESS update must be guarded by one transaction")
                .contains(
                        "TransactionTemplate",
                        "executeWithoutResult",
                        "writeSuccessRows"
                );
    }

    @Test
    void allocationAlgorithmFreezesDynamicProductionLocksAndRemainderRows() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("WMS allocation must split one delivered ticket into inventory, production-order, and remainder rows")
                .contains(
                        "demandQty = ticket.qty",
                        "inStockLockedQty",
                        "inProductionLocks",
                        "productionOrderNo",
                        "lockedQty",
                        "coveredQty",
                        "productionRequiredQty",
                        "IN_STOCK_LOCK",
                        "IN_PRODUCTION_LOCK",
                        "PRODUCTION_REQUIRED",
                        "assert sum(rows.qty) == demandQty"
                );
    }

    @Test
    void wmsTaskStatusAndSubmissionGuardsAreFrozenBeforeImplementation() throws IOException {
        String plan = read(WMS_PLAN);
        String ticket = read(DELIVERY_TICKET);

        assertThat(plan)
                .as("WMS task submission must require material number and reject duplicate processing/success rows")
                .contains(
                        "WMS_ALLOCATION",
                        "material_no",
                        "wms_allocate_status=SUCCESS",
                        "wms_allocate_status=PROCESSING",
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "WMS allocation submitted",
                        "WMS allocation started",
                        "WMS allocation completed",
                        "WMS allocation failed"
                );
        assertThat(ticket)
                .as("The immutable delivered ticket row already owns WMS task state")
                .contains(
                        "private String wmsAllocateStatus;",
                        "private String wmsAllocateMessage;",
                        "private LocalDateTime wmsAllocatedAt;"
                );
    }

    @Test
    void allocationFoundationSupportsAllRequiredSplitRowFields() throws IOException {
        String migration = read(FOUNDATION_MIGRATION);
        String remarkMigration = read(ALLOCATION_REMARK_MIGRATION);
        String entity = read(DELIVERY_ALLOCATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "ticket_id integer NOT NULL",
                        "FOREIGN KEY (ticket_id) REFERENCES order_delivery_ticket(id)",
                        "allocation_type character varying(40) NOT NULL",
                        "qty integer NOT NULL",
                        "material_no character varying(100)",
                        "wms_production_order_no character varying(100)",
                        "idx_delivery_allocation_wms_order"
                );
        assertThat(remarkMigration)
                .contains(
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "remark text"
                );
        assertThat(read(ALLOCATION_ITEM_TYPE_MIGRATION))
                .as("Locked item rows must snapshot the delivered ticket item type for display, filters, and future push routing")
                .contains(
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "item_type character varying(20)",
                        "idx_delivery_allocation_item_type"
                );
        assertThat(entity)
                .contains(
                        "private Integer ticketId;",
                        "private Integer rfqId;",
                        "private Integer itemId;",
                        "private String itemType;",
                        "private String allocationType;",
                        "private Integer qty;",
                        "private String materialNo;",
                        "private String orderType;",
                        "private String partNo;",
                        "private String version;",
                        "private String customerName;",
                        "private String internalProjectNo;",
                        "private String wmsProductionOrderNo;",
                        "private String rfqNo;",
                        "private String customerOrderNo;",
                        "private String customerProjectNo;",
                        "private String materialName;",
                        "private String engineeringMaterialCode;",
                        "private String engineeringMaterialTypeCode;",
                        "private String engineeringMaterialTypeName;",
                        "private String engineeringMaterialCategory;",
                        "private String engineeringMaterialName;",
                        "private String materialShape;",
                        "private String surfaceTreatment;",
                        "private String remark;",
                        "private String customerRequirement;",
                        "private String description;",
                        "private LocalDate deliveryDate;",
                        "private Object processRoute;",
                        "private Object materialData;",
                        "private Double materialGrossWeight;",
                        "private Double partNetWeight;",
                        "private Object sourcePayload;"
                );
    }

    @Test
    void wmsAllocationRowsMustCopyTicketSnapshotFields() throws IOException {
        String allocationService = read(WMS_ALLOCATION_SERVICE);

        assertThat(allocationService)
                .as("Each WMS allocation split row must own its immutable display snapshot")
                .contains(
                        "row.setCustomerOrderNo(ticket.getCustomerOrderNo())",
                        "row.setOrderType(ticket.getOrderType())",
                        "row.setItemType(ticket.getItemType())",
                        "row.setRfqNo(resolveRfqNo(ticket, order))",
                        "row.setCustomerProjectNo(ticket.getCustomerProjectNo())",
                        "row.setMaterialName(ticket.getMaterialName())",
                        "row.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode())",
                        "row.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode())",
                        "row.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName())",
                        "row.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory())",
                        "row.setEngineeringMaterialName(ticket.getEngineeringMaterialName())",
                        "row.setMaterialShape(ticket.getMaterialShape())",
                        "row.setSurfaceTreatment(ticket.getSurfaceTreatment())",
                        "row.setRemark(ticket.getRemark())",
                        "row.setCustomerRequirement(ticket.getCustomerRequirement())",
                        "row.setDescription(ticket.getDescription())",
                        "row.setDeliveryDate(ticket.getDeliveryDate())",
                        "row.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()))",
                        "row.setMaterialData(ticket.getMaterialData())",
                        "row.setMaterialGrossWeight(ticket.getMaterialGrossWeight())",
                        "row.setPartNetWeight(ticket.getPartNetWeight())",
                        "row.setTicketQty(ticket.getQty())",
                        "row.setMesProductionStatus(\"PENDING\")"
                );
    }

    @Test
    void materialShapeMigrationAddsExplicitSnapshotsAndBackfillsFromMaterialData() throws IOException {
        String migration = read(MATERIAL_SHAPE_MIGRATION);

        assertThat(migration)
                .contains(
                        "CREATE TABLE item_quote_version",
                        "CREATE TABLE sales_order_item",
                        "CREATE TABLE order_delivery_ticket",
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "material_shape character varying(30)"
                );
    }

    @Test
    void mesProductionDispatchMigrationAddsTicketQuantityAndStatusSkeleton() throws IOException {
        String migration = read(MES_PRODUCTION_DISPATCH_MIGRATION);
        String permissionSeed = read(Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql"));

        if (!migration.isBlank()) {
            assertThat(migration)
                    .contains(
                            "CREATE TABLE order_delivery_ticket_allocation",
                            "ticket_qty integer",
                            "mes_production_status character varying(30) DEFAULT 'PENDING'",
                            "mes_production_request_id character varying(80)",
                            "mes_production_external_id character varying(120)",
                            "mes_production_synced_at timestamp without time zone",
                            "mes_production_message text",
                            "idx_delivery_allocation_mes_production_status",
                            "uk_delivery_allocation_mes_production_request"
                    );
            assertThat(permissionSeed)
                    .contains(
                            "market:production-item:mes-production-submit",
                            "market:production-item:view",
                            "market:locked-item:mes-production-submit",
                            "MARKET_USER",
                            "MARKET_MANAGER"
                    )
                    .contains("'market:locked-item:mes-production-submit'")
                    .contains("false, false");
            return;
        }

        assertThat(migration)
                .contains(
                        "ALTER TABLE order_delivery_ticket_allocation",
                        "ticket_qty integer",
                        "mes_production_status character varying(30) DEFAULT 'PENDING'",
                        "mes_production_request_id character varying(80)",
                        "mes_production_external_id character varying(120)",
                        "mes_production_synced_at timestamp without time zone",
                        "mes_production_message text",
                        "SET ticket_qty = ticket.qty",
                        "FROM order_delivery_ticket ticket",
                        "alloc.ticket_id = ticket.id",
                        "idx_delivery_allocation_mes_production_status",
                        "uk_delivery_allocation_mes_production_request",
                        "market:locked-item:mes-production-submit",
                        "锁库零件下发MES生产",
                        "market:locked-item:view",
                        "MARKET_USER",
                        "MARKET_MANAGER"
                )
                .doesNotContain("market:locked-item:mes-production-submit' AND r.role_code = 'READONLY'");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void wireAllocationService(
            WmsAllocationService service,
            OrderDeliveryTicketMapper ticketMapper,
            OrderDeliveryTicketAllocationMapper allocationMapper,
            OrderRfqMapper orderMapper,
            WmsAllocationClient client,
            AuditService auditService) {
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "wmsAllocationClient", client);
        ReflectionTestUtils.setField(service, "auditService", auditService);
    }

    private OrderDeliveryTicket ticket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(123);
        ticket.setRfqId(88);
        ticket.setItemId(77);
        ticket.setItemType("组合件");
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setQty(10);
        ticket.setMaterialNo("MAT-001");
        ticket.setOrderType("预测订单");
        ticket.setInternalProjectNo("IP-9");
        ticket.setRfqNo("RFQ-88");
        ticket.setCustomerName("ACME");
        ticket.setCustomerOrderNo("CO-9");
        ticket.setCustomerProjectNo("CP-9");
        ticket.setMaterialName("6061");
        ticket.setEngineeringMaterialName("7075");
        setSnapshot(ticket, "setEngineeringMaterialCode", "EMC-7075");
        setSnapshot(ticket, "setEngineeringMaterialTypeCode", "RAW_MATERIAL");
        setSnapshot(ticket, "setEngineeringMaterialTypeName", "Raw Material");
        setSnapshot(ticket, "setEngineeringMaterialCategory", "ALUMINUM");
        setSnapshot(ticket, "setMaterialShape", "板材");
        ticket.setSurfaceTreatment("Anodized");
        ticket.setRemark("Rush note");
        ticket.setCustomerRequirement("Customer needs burr-free edges");
        ticket.setDescription("Bracket");
        ticket.setDeliveryDate(LocalDate.of(2026, 8, 12));
        ticket.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("sortNo", 10),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("lineCost", 15.0))));
        ticket.setMaterialData(Map.of("shape", "板材", "name", "7075"));
        ticket.setMaterialGrossWeight(12.345);
        ticket.setPartNetWeight(10.2);
        ticket.setWmsAllocateStatus("PENDING");
        return ticket;
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(88);
        order.setRfqNo("RFQ-88");
        order.setCustomerName("ACME");
        return order;
    }

    private WmsAllocationResp successResponse(Integer inStockQty, List<WmsAllocationResp.InProductionLock> locks) {
        WmsAllocationResp resp = new WmsAllocationResp();
        resp.setSuccess(true);
        resp.setMaterialNo("MAT-001");
        resp.setInStockLockedQty(inStockQty);
        resp.setInProductionLocks(locks);
        resp.setMessage("allocated");
        resp.setExternalTraceId("WMS-T-1");
        return resp;
    }

    private WmsAllocationResp.InProductionLock lock(String productionOrderNo, Integer lockedQty) {
        WmsAllocationResp.InProductionLock lock = new WmsAllocationResp.InProductionLock();
        lock.setProductionOrderNo(productionOrderNo);
        lock.setLockedQty(lockedQty);
        return lock;
    }

    @SafeVarargs
    private final List<WmsAllocationResp.InProductionLock> locks(WmsAllocationResp.InProductionLock... locks) {
        return List.of(locks);
    }

    private void assertSnapshot(Object row, String getterName, Object expectedValue) {
        try {
            Object actual = row.getClass().getMethod(getterName).invoke(row);
            assertThat(actual).isEqualTo(expectedValue);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Allocation snapshot getter missing or inaccessible: " + getterName, error);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertStandardProcessRoute(Object processRoute) {
        assertThat(processRoute).isInstanceOf(List.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) processRoute;
        assertThat(rows).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("processCode", "PACKAGING");
            assertThat(route).containsEntry("categoryName", "包装");
            assertThat(route).containsEntry("detailName", "精细包装");
            assertThat(((Number) route.get("hourlyRate")).doubleValue()).isEqualTo(10.0);
            assertThat(((Number) route.get("unitWorkHours")).doubleValue()).isEqualTo(1.5);
            assertThat(((Number) route.get("lineCost")).doubleValue()).isEqualTo(15.0);
        });
    }

    private void setSnapshot(Object row, String setterName, Object value) {
        try {
            row.getClass().getMethod(setterName, value.getClass()).invoke(row, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Snapshot setter missing or inaccessible: " + setterName, error);
        }
    }
}
