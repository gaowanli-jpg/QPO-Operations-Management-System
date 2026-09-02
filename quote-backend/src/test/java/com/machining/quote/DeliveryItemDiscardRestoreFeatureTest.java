package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryItemDiscardRestoreFeatureTest {
    private static final Path MAIN = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V33__delivery_item_discard_restore.sql");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md");

    @Test
    void schemaDtosAndQueriesExposeIndependentDiscardMetadata() throws IOException {
        String migration = read(MIGRATION);
        String ticketEntity = read(MAIN.resolve(Path.of("entity", "OrderDeliveryTicket.java")));
        String allocationEntity = read(MAIN.resolve(Path.of("entity", "OrderDeliveryTicketAllocation.java")));
        String deliveredDto = read(MAIN.resolve(Path.of("dto", "DeliveredItemDTO.java")));
        String lockedDto = read(MAIN.resolve(Path.of("dto", "LockedItemDTO.java")));
        String deliveredReq = read(MAIN.resolve(Path.of("dto", "DeliveredItemQueryReq.java")));
        String productionReq = read(MAIN.resolve(Path.of("dto", "ProductionItemQueryReq.java")));
        String ticketMapper = read(MAIN.resolve(Path.of("mapper", "OrderDeliveryTicketMapper.java")));
        String allocationMapper = read(MAIN.resolve(Path.of("mapper", "OrderDeliveryTicketAllocationMapper.java")));

        assertThat(migration)
                .contains(
                        "ALTER TABLE order_delivery_ticket",
                        "ADD COLUMN IF NOT EXISTS discarded boolean DEFAULT false NOT NULL",
                        "discarded_at timestamp",
                        "discarded_by bigint",
                        "discard_reason character varying(500)",
                        "ALTER TABLE order_delivery_ticket_allocation",
                        "idx_order_delivery_ticket_discarded_id",
                        "idx_delivery_allocation_discarded_id"
                );
        for (String source : new String[]{ticketEntity, allocationEntity, deliveredDto, lockedDto}) {
            assertThat(source)
                    .contains(
                            "private Boolean discarded;",
                            "private LocalDateTime discardedAt;",
                            "private Long discardedBy;",
                            "private String discardReason;",
                            "private LocalDateTime restoredAt;",
                            "private Long restoredBy;",
                            "private String restoreReason;"
                    );
        }
        assertThat(deliveredReq).contains("private Boolean discarded;");
        assertThat(productionReq).contains("private Boolean discarded;");
        assertThat(ticketMapper).contains("ticket.discarded", "ticket.discarded_at", "ticket.discard_reason");
        assertThat(allocationMapper).contains("alloc.discarded", "alloc.discarded_at", "alloc.discard_reason", "selectByIdForUpdate");
    }

    @Test
    void controllersServicesPermissionsAndTasksWireDiscardRestoreSafely() throws IOException {
        String controller = read(MAIN.resolve(Path.of("controller", "OrderDeliveryController.java")));
        String deliveredService = read(MAIN.resolve(Path.of("service", "DeliveredItemService.java")));
        String productionService = read(MAIN.resolve(Path.of("service", "ProductionItemService.java")));
        String systemTaskService = read(MAIN.resolve(Path.of("service", "SystemTaskService.java")));
        String mesProductionService = read(MAIN.resolve(Path.of("service", "mes", "MesProductionDispatchService.java")));
        String wmsPlanService = read(MAIN.resolve(Path.of("service", "wms", "WmsReservationPlanService.java")));
        String auditService = read(MAIN.resolve(Path.of("service", "AuditService.java")));
        String permissionService = read(MAIN.resolve(Path.of("service", "PermissionService.java")));
        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);

        assertThat(controller)
                .contains(
                        "@PostMapping(\"/tickets/{ticketId}/discard\")",
                        "@RequirePermission(\"market:delivered-item:discard\")",
                        "return deliveredItemService.discardTicket(ticketId, req)",
                        "@PostMapping(\"/tickets/{ticketId}/restore\")",
                        "@RequirePermission(\"market:delivered-item:restore\")",
                        "@PostMapping(\"/production-items/{allocationId}/discard\")",
                        "@RequirePermission(\"market:production-item:discard\")",
                        "@PostMapping(\"/production-items/{allocationId}/restore\")",
                        "@RequirePermission(\"market:production-item:restore\")"
                );
        assertThat(deliveredService)
                .contains(
                        "applyDiscardedScope(query, req.getDiscarded())",
                        "discardTicket(Integer ticketId, DeliveryItemDiscardReq req)",
                        "restoreTicket(Integer ticketId, DeliveryItemRestoreReq req)",
                        "CALLBACK_PENDING",
                        "DELIVERED_ITEM_DISCARD",
                        "DELIVERED_ITEM_RESTORE",
                        "dataScopeService.assertCanAccessDeliveryTicket(ticket)"
                );
        assertThat(deliveredService).contains(
                "result.put(\"rfqNo\", ticket.getRfqNo())",
                "result.put(\"partNo\", ticket.getPartNo())",
                "result.put(\"version\", ticket.getVersion())",
                "result.put(\"discardedBy\", ticket.getDiscardedBy())"
        );
        assertThat(productionService)
                .contains(
                        "applyDiscardedScope(query, req.getDiscarded())",
                        "discardAllocation(Integer allocationId, DeliveryItemDiscardReq req)",
                        "restoreAllocation(Integer allocationId, DeliveryItemRestoreReq req)",
                        "CALLBACK_PENDING",
                        "PRODUCTION_ITEM_DISCARD",
                        "PRODUCTION_ITEM_RESTORE"
                );
        assertThat(productionService).contains(
                "result.put(\"rfqNo\", allocation.getRfqNo())",
                "result.put(\"partNo\", allocation.getPartNo())",
                "result.put(\"version\", allocation.getVersion())",
                "result.put(\"discardedBy\", allocation.getDiscardedBy())"
        );
        assertThat(systemTaskService)
                .contains("filterDiscardedTicketIds", "filterDiscardedAllocationIds")
                .doesNotContain("discardReason");
        assertThat(wmsPlanService).contains("Boolean.TRUE.equals(ticket.getDiscarded())");
        assertThat(mesProductionService).contains("Boolean.TRUE.equals(allocation.getDiscarded())");
        assertThat(auditService).contains(
                "DELIVERED_ITEM_DISCARD",
                "DELIVERED_ITEM_RESTORE",
                "PRODUCTION_ITEM_DISCARD",
                "PRODUCTION_ITEM_RESTORE"
        );
        for (String source : new String[]{permissionService, permissionDoc}) {
            assertThat(source)
                    .contains(
                            "market:delivered-item:discard",
                            "market:delivered-item:restore",
                            "market:production-item:discard",
                            "market:production-item:restore"
                    )
                    .doesNotContain("common:discard", "common:restore");
        }
        assertThat(logDoc).contains(
                "DELIVERED_ITEM_DISCARD",
                "DELIVERED_ITEM_RESTORE",
                "PRODUCTION_ITEM_DISCARD",
                "PRODUCTION_ITEM_RESTORE"
        );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
