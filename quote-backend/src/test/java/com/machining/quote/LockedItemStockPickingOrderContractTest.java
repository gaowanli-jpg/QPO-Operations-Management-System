package com.machining.quote;

import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterEndpointRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LockedItemStockPickingOrderContractTest {
    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V50__locked_item_stock_picking_order.sql");
    private static final Path CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "OrderDeliveryController.java");
    private static final Path SYSTEM_TASK_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "SystemTaskService.java");
    private static final Path LOCKED_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "LockedItemService.java");
    private static final Path ENTITY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "entity", "OrderDeliveryTicketAllocation.java");
    private static final Path DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "LockedItemDTO.java");
    private static final Path QUERY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "LockedItemQueryReq.java");
    private static final Path MAPPER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "mapper", "OrderDeliveryTicketAllocationMapper.java");
    private static final Path PERMISSION_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path APPLICATION_YAML = Path.of(
            "src", "main", "resources", "application.yaml");
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");

    @Test
    void dataCenterRegistryDefinesStockPickingOrderCreateEndpoint() {
        DataCenterEndpointRegistry registry = new DataCenterEndpointRegistry();

        assertThat(DataCenterEndpointRegistry.MES_STOCK_PICKING_ORDER_CREATE)
                .isEqualTo("MES_STOCK_PICKING_ORDER_CREATE");
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.MES_STOCK_PICKING_ORDER_CREATE))
                .satisfies(definition -> {
                    assertThat(definition.displayName()).isEqualTo("MES提库存工单创建");
                    assertThat(definition.targetSystemCode()).isEqualTo("MES");
                    assertThat(definition.defaultMethod()).isEqualTo("POST");
                    assertThat(definition.requestAdapter()).isEqualTo("MES_PRODUCTION_ORDER_CREATE_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("MES_PRODUCTION_ORDER_CREATE_RESPONSE_V1");
                    assertThat(definition.minimumTimeoutMs()).isEqualTo(60000);
                });
    }

    @Test
    void migrationAddsStockPickingStateAndEndpointWithoutChangingBaselineFiles() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains(
                        "stock_picking_status",
                        "stock_picking_biz_id",
                        "stock_picking_external_id",
                        "stock_picking_synced_at",
                        "stock_picking_message",
                        "idx_delivery_allocation_stock_picking_status_id",
                        "idx_delivery_allocation_locked_stock_active_id",
                        "MES_STOCK_PICKING_ORDER_CREATE",
                        "/api/v1/mes/openapi/production-order/create",
                        "market:locked-item:stock-picking-submit",
                        "market:locked-item:discard",
                        "market:locked-item:restore")
                .doesNotContain("common:");
    }

    @Test
    void backendSurfacesLockedStockPickingStatusAndProtectedActions() throws IOException {
        String entity = read(ENTITY);
        String dto = read(DTO);
        String query = read(QUERY);
        String mapper = read(MAPPER);
        String service = read(LOCKED_SERVICE);
        String controller = read(CONTROLLER);
        String taskService = read(SYSTEM_TASK_SERVICE);

        assertThat(entity).contains(
                "private String stockPickingStatus;",
                "private String stockPickingBizId;",
                "private String stockPickingExternalId;",
                "private LocalDateTime stockPickingSyncedAt;",
                "private String stockPickingMessage;");
        assertThat(dto).contains(
                "private String stockPickingStatus;",
                "private String stockPickingBizId;",
                "private String stockPickingExternalId;",
                "private LocalDateTime stockPickingSyncedAt;",
                "private String stockPickingMessage;");
        assertThat(query).contains("private String stockPickingStatus;", "private Boolean discarded;");
        assertThat(mapper).contains(
                "alloc.stock_picking_status",
                "alloc.stock_picking_biz_id",
                "alloc.stock_picking_external_id",
                "alloc.stock_picking_synced_at",
                "alloc.stock_picking_message");
        assertThat(service).contains(
                "applyStockPickingStatusScope",
                "applyDiscardedScope",
                "batchDiscardAllocations(DeliveryItemBatchDiscardReq req)",
                "batchRestoreAllocations(DeliveryItemBatchRestoreReq req)",
                "batchResult(\"锁库零件批量废弃完成。\"",
                "LOCKED_ITEM_DISCARD",
                "LOCKED_ITEM_RESTORE");
        assertThat(controller).contains(
                "@PostMapping(\"/locked-items/{allocationId}/discard\")",
                "@RequirePermission(\"market:locked-item:discard\")",
                "@PostMapping(\"/locked-items/batch-discard\")",
                "return lockedItemService.batchDiscardAllocations(req)",
                "@PostMapping(\"/locked-items/{allocationId}/restore\")",
                "@RequirePermission(\"market:locked-item:restore\")",
                "@PostMapping(\"/locked-items/batch-restore\")",
                "return lockedItemService.batchRestoreAllocations(req)",
                "@PostMapping(\"/locked-items/stock-picking-orders\")",
                "@RequirePermission(\"market:locked-item:stock-picking-submit\")");
        assertThat(taskService).contains(
                "submitLockedStockPickingOrderBatchTask",
                "LOCKED_ITEM_STOCK_PICKING_ORDER",
                "LOCKED_ITEM_STOCK_PICKING_SUBMIT");
    }

    @Test
    void permissionAndAuditCatalogsExposeStockPickingLabels() throws IOException {
        String permissionService = read(PERMISSION_SERVICE);

        assertThat(permissionService)
                .contains(
                        "market:locked-item:stock-picking-submit",
                        "锁库零件下发提库存工单",
                        "market:locked-item:discard",
                        "market:locked-item:restore",
                        "Map.entry(\"market:locked-item:stock-picking-submit\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))")
                .doesNotContain("Map.entry(\"market:locked-item:mes-production-submit\"");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_STOCK_PICKING_SUBMIT")).isEqualTo("提交提库存工单");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_STOCK_PICKING_SUCCESS")).isEqualTo("提库存工单下发成功");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_STOCK_PICKING_FAILED")).isEqualTo("提库存工单下发失败");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_STOCK_PICKING_RESULT_UNKNOWN")).isEqualTo("提库存工单结果待核对");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_DISCARD")).isEqualTo("废弃锁库零件");
        assertThat(AuditService.labelForAction("LOCKED_ITEM_RESTORE")).isEqualTo("恢复锁库零件");
    }

    @Test
    void environmentFallbackDocumentsStockPickingEndpointPath() throws IOException {
        assertThat(read(APPLICATION_YAML))
                .contains("mes-stock-picking-order-create-path: ${ERP_DATA_CENTER_MES_STOCK_PICKING_ORDER_CREATE_PATH:/api/v1/mes/openapi/production-order/create}");
        assertThat(read(ENV_EXAMPLE))
                .contains("ERP_DATA_CENTER_MES_STOCK_PICKING_ORDER_CREATE_PATH=/api/v1/mes/openapi/production-order/create")
                .doesNotContain("client_secret=");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
