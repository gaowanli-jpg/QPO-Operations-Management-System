package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderManageService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SalesOrderManagementGovernanceTest {
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderManageController.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderManageService.java");

    @Test
    void controllerExposesSecuredGovernanceAndDrawingEndpointsWithoutDelete() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@PostMapping(\"/orders/{salesOrderId}/discard\")",
                        "@RequirePermission(\"market:sales-order:discard\")",
                        "@PostMapping(\"/orders/{salesOrderId}/restore\")",
                        "@RequirePermission(\"market:sales-order:restore\")",
                        "@PostMapping(value = \"/orders/{salesOrderId}/supplement-drawings\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)",
                        "@PostMapping(value = \"/orders/{salesOrderId}/drawings/replace\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)",
                        "@RequirePermission(\"market:sales-order:supplement-drawing\")",
                        "replaceOrderDrawings"
                )
                .doesNotContain("/items/{itemId}/drawings")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("market:order:delete")
                .doesNotContain("market:history-order:");
    }

    @Test
    void controllerExposesItemLevelU9ChangeExclusionEndpointsWithUpdatePermission() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/discard\")",
                        "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/restore\")",
                        "@RequirePermission(\"market:sales-order:update\")",
                        "discardItemForU9Change",
                        "restoreItemForU9Change")
                .doesNotContain(
                        "@RequirePermission(\"market:sales-order:discard\")\n    public OrderStatusChangeResult discardItemForU9Change",
                "@RequirePermission(\"market:sales-order:restore\")\n    public OrderStatusChangeResult restoreItemForU9Change");
    }

    @Test
    void controllerExposesLinkedDiscardAndRestoreWithTheSalesOrderUpdatePermission() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/linked-discard\")",
                        "@PostMapping(\"/orders/{salesOrderId}/items/{itemId}/linked-restore\")",
                        "@RequirePermission(\"market:sales-order:update\")",
                        "salesOrderLinkedDiscardService.discard",
                        "salesOrderLinkedDiscardService.restore");
    }

    @Test
    void salesOrderItemU9ChangeExclusionMigrationAndEntityUseDedicatedFields() throws IOException {
        Path migration = Path.of("src", "main", "resources", "db", "migration", "V48__sales_order_item_u9_change_exclusion.sql");
        assertThat(migration).exists();
        String migrationSource = read(migration);
        assertThat(migrationSource)
                .contains(
                        "ALTER TABLE sales_order_item",
                        "ADD COLUMN IF NOT EXISTS u9_change_excluded boolean DEFAULT false NOT NULL",
                        "ADD COLUMN IF NOT EXISTS u9_change_excluded_at timestamp",
                        "ADD COLUMN IF NOT EXISTS u9_change_excluded_by bigint",
                        "ADD COLUMN IF NOT EXISTS u9_change_excluded_reason character varying(500)",
                        "ADD COLUMN IF NOT EXISTS u9_change_restored_at timestamp",
                        "ADD COLUMN IF NOT EXISTS u9_change_restored_by bigint",
                        "ADD COLUMN IF NOT EXISTS u9_change_restore_reason character varying(500)",
                        "idx_sales_order_item_u9_change_excluded_order")
                .doesNotContain("order_delivery_ticket", "order_delivery_ticket_allocation", "u9_sync_status");

        String entity = read(Path.of("src", "main", "java", "com", "machining", "quote", "entity", "SalesOrderItem.java"));
        assertThat(entity)
                .contains(
                        "private Boolean u9ChangeExcluded;",
                        "private LocalDateTime u9ChangeExcludedAt;",
                        "private Long u9ChangeExcludedBy;",
                        "private String u9ChangeExcludedReason;",
                        "private LocalDateTime u9ChangeRestoredAt;",
                        "private Long u9ChangeRestoredBy;",
                        "private String u9ChangeRestoreReason;");
    }

    @Test
    void itemLevelExclusionIsNotConsumedByMesWmsDeliveredOrProductionFlows() throws IOException {
        for (Path source : List.of(
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "DeliveryService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "DeliveredItemService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "DeliveryAutoMesMaterialSyncService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "SourceItemMaterialNoSyncService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "ProductionItemService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "LockedItemService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "mes", "MesMaterialSyncService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsReservationPlanService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "wms", "WmsReservationStateService.java"))) {
            assertThat(read(source))
                    .as(source.toString())
                    .doesNotContain("u9_change_excluded", "u9ChangeExcluded");
        }
    }

    @Test
    void serviceDefinesSalesOrderOnlyStatusAndDrawingWrites() throws IOException {
        String source = read(SERVICE);

        assertThat(source)
                .contains(
                        "selectByIdForUpdate",
                        "SALES_ORDER_DISCARD",
                        "SALES_ORDER_RESTORE",
                        "SALES_ORDER_DRAWING_SUPPLEMENT",
                        "SALES_ORDER_DRAWING_REPLACE",
                        "Sales order status change started",
                        "Sales order status change completed",
                        "Sales order drawing supplement completed",
                        "Sales order drawing replace completed",
                        "saveSalesOrderImportDrawing",
                        "DrawingPartNoResolver",
                        "sales_order_header"
                )
                .doesNotContain(
                        "ItemQuoteVersionMapper",
                        "OrderRfqMapper",
                        "\"item_quote_version\"",
                        "\"order_rfq\""
                );
    }

    @Test
    void releasedUnsyncedSalesOrderCanBeDiscardedAndRestoredToReleased() throws Exception {
        SalesOrderItem releasedItem = item(501L, 100L, QuoteStatusWorkflow.RELEASED);
        Fixture discardFixture = fixture(header(100L, QuoteStatusWorkflow.RELEASED), List.of(releasedItem), 1L);

        Object discardResult = invokeStatusAction(discardFixture.service, "discard", 100L, "客户取消");

        assertThat(String.valueOf(discardResult)).contains(QuoteStatusWorkflow.DISCARDED);
        verify(discardFixture.dataScopeService).assertCanAccessSalesOrder(discardFixture.header);
        ArgumentCaptor<SalesOrderHeader> discardedHeader = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(discardFixture.headerMapper).updateById(discardedHeader.capture());
        assertThat(discardedHeader.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        ArgumentCaptor<SalesOrderItem> discardedItem = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(discardFixture.itemMapper).updateById(discardedItem.capture());
        assertThat(discardedItem.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        verify(discardFixture.auditService).record(eq("SALES_ORDER_DISCARD"), eq("sales_order_header"), eq(100L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());

        SalesOrderItem discardedLine = item(601L, 200L, QuoteStatusWorkflow.DISCARDED);
        Fixture restoreFixture = fixture(header(200L, QuoteStatusWorkflow.DISCARDED), List.of(discardedLine), 0L);

        Object restoreResult = invokeStatusAction(restoreFixture.service, "restore", 200L, "恢复订单");

        assertThat(String.valueOf(restoreResult)).contains(QuoteStatusWorkflow.RELEASED);
        ArgumentCaptor<SalesOrderHeader> restoredHeader = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(restoreFixture.headerMapper).updateById(restoredHeader.capture());
        assertThat(restoredHeader.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        ArgumentCaptor<SalesOrderItem> restoredItem = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(restoreFixture.itemMapper).updateById(restoredItem.capture());
        assertThat(restoredItem.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        verify(restoreFixture.auditService).record(eq("SALES_ORDER_RESTORE"), eq("sales_order_header"), eq(200L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void nonReleasedSalesOrdersCannotBeDiscardedButSyncedReleasedOrdersCan() throws Exception {
        Fixture partiallyReleased = fixture(header(300L, QuoteStatusWorkflow.PARTIALLY_RELEASED), List.of(), 0L);
        assertThatThrownBy(() -> invokeStatusAction(partiallyReleased.service, "discard", 300L, "不能废弃"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已生成提单的销售订单可以废弃");
        verify(partiallyReleased.headerMapper, never()).updateById(any());
        verify(partiallyReleased.itemMapper, never()).updateById(any());

        SalesOrderHeader syncedHeader = header(301L, QuoteStatusWorkflow.RELEASED);
        syncedHeader.setU9SyncStatus("已同步");
        Fixture synced = fixture(syncedHeader, List.of(), 0L);
        Object syncedDiscardResult = invokeStatusAction(synced.service, "discard", 301L, "同步后业务废弃");
        assertThat(String.valueOf(syncedDiscardResult)).contains(QuoteStatusWorkflow.DISCARDED);
        verify(synced.headerMapper).updateById(any(SalesOrderHeader.class));

        Fixture ticketed = fixture(header(302L, QuoteStatusWorkflow.QUOTED), List.of(), 1L);
        assertThatThrownBy(() -> invokeStatusAction(ticketed.service, "discard", 302L, "已有提单"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已生成提单的销售订单可以废弃");
        verify(ticketed.headerMapper, never()).updateById(any());
        verify(ticketed.itemMapper, never()).updateById(any());
    }

    @Test
    void itemLevelExclusionOnlyMarksSalesOrderItemForU9AndSalesChangeWithoutChangingBusinessStatuses() throws Exception {
        SalesOrderHeader header = header(320L, QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus("已同步");
        SalesOrderItem item = item(820L, header.getId(), QuoteStatusWorkflow.RELEASED);
        Fixture fixture = fixture(header, List.of(item), 1L);
        when(fixture.itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        Object discardResult = invokeItemStatusAction(fixture.service, "discardItemForU9Change", header.getId(), item.getId(), "U9 不再处理该行");

        assertThat(String.valueOf(discardResult)).contains("已从 U9 同步和销售变更排除");
        ArgumentCaptor<UpdateWrapper<SalesOrderItem>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.itemMapper).update(isNull(), updateCaptor.capture());
        String sqlSet = updateCaptor.getValue().getSqlSet();
        assertThat(sqlSet)
                .contains(
                        "u9_change_excluded=",
                        "u9_change_excluded_at=",
                        "u9_change_excluded_by=",
                        "u9_change_excluded_reason=",
                        "updated_at=")
                .doesNotContain("status=", "u9_sync_status=");
        assertThat(item.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThat(header.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThat(header.getU9SyncStatus()).isEqualTo("已同步");
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.auditService).record(eq("SALES_ORDER_ITEM_U9_CHANGE_EXCLUDE"), eq("sales_order_item"), eq(item.getId()), isNull(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());

        ReflectionTestUtils.setField(item, "u9ChangeExcluded", true);
        Object restoreResult = invokeItemStatusAction(fixture.service, "restoreItemForU9Change", header.getId(), item.getId(), "恢复本地校正");

        assertThat(String.valueOf(restoreResult)).contains("已恢复到 U9 同步和销售变更");
        verify(fixture.auditService).record(eq("SALES_ORDER_ITEM_U9_CHANGE_RESTORE"), eq("sales_order_item"), eq(item.getId()), isNull(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void itemLevelExclusionRequiresReasonRejectsForeignItemAndRunningU9Statuses() throws Exception {
        SalesOrderHeader header = header(321L, QuoteStatusWorkflow.RELEASED);
        SalesOrderItem item = item(821L, header.getId(), QuoteStatusWorkflow.RELEASED);
        Fixture missingReason = fixture(header, List.of(item), 0L);
        when(missingReason.itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);

        assertThatThrownBy(() -> invokeItemStatusAction(missingReason.service, "discardItemForU9Change", header.getId(), item.getId(), " "))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("原因不能为空");
        verify(missingReason.itemMapper, never()).update(isNull(), any(UpdateWrapper.class));

        SalesOrderHeader running = header(322L, QuoteStatusWorkflow.RELEASED);
        running.setU9SyncStatus("同步中");
        SalesOrderItem runningItem = item(822L, running.getId(), QuoteStatusWorkflow.RELEASED);
        Fixture runningFixture = fixture(running, List.of(runningItem), 0L);
        when(runningFixture.itemMapper.selectByIdForUpdate(runningItem.getId())).thenReturn(runningItem);
        assertThatThrownBy(() -> invokeItemStatusAction(runningFixture.service, "discardItemForU9Change", running.getId(), runningItem.getId(), "暂停 U9 行"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("U9 状态为同步中")
                .hasMessageContaining("不能废弃或恢复销售订单零件");
        verify(runningFixture.itemMapper, never()).update(isNull(), any(UpdateWrapper.class));

        SalesOrderHeader pending = header(323L, QuoteStatusWorkflow.RELEASED);
        pending.setU9SyncStatus("结果待确认");
        SalesOrderItem pendingItem = item(823L, pending.getId(), QuoteStatusWorkflow.RELEASED);
        Fixture pendingFixture = fixture(pending, List.of(pendingItem), 0L);
        when(pendingFixture.itemMapper.selectByIdForUpdate(pendingItem.getId())).thenReturn(pendingItem);
        assertThatThrownBy(() -> invokeItemStatusAction(pendingFixture.service, "restoreItemForU9Change", pending.getId(), pendingItem.getId(), "恢复 U9 行"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("U9 状态为结果待确认")
                .hasMessageContaining("不能废弃或恢复销售订单零件");
        verify(pendingFixture.itemMapper, never()).update(isNull(), any(UpdateWrapper.class));

        SalesOrderHeader foreignHeader = header(324L, QuoteStatusWorkflow.RELEASED);
        SalesOrderItem foreignItem = item(824L, 999L, QuoteStatusWorkflow.RELEASED);
        Fixture foreignFixture = fixture(foreignHeader, List.of(foreignItem), 0L);
        when(foreignFixture.itemMapper.selectByIdForUpdate(foreignItem.getId())).thenReturn(foreignItem);
        assertThatThrownBy(() -> invokeItemStatusAction(foreignFixture.service, "discardItemForU9Change", foreignHeader.getId(), foreignItem.getId(), "暂停 U9 行"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不属于当前销售订单");
        verify(foreignFixture.itemMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void supplementDrawingBindsMissingItemsAcrossSalesOrderByPartNo() throws Exception {
        SalesOrderHeader header = header(400L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem itemA = item(700L, 400L, QuoteStatusWorkflow.QUOTED);
        itemA.setPartNo("P-700");
        SalesOrderItem itemB = item(701L, 400L, QuoteStatusWorkflow.QUOTED);
        itemB.setPartNo("P-700");
        SalesOrderItem itemC = item(702L, 400L, QuoteStatusWorkflow.QUOTED);
        itemC.setPartNo("P-702");
        itemC.setDrawingUrl("/existing/P-702.pdf");
        itemC.setHasDrawing(true);
        Fixture fixture = fixture(header, List.of(itemA, itemB, itemC), 0L);
        when(fixture.headerMapper.selectByIdForUpdate(400L)).thenReturn(header);
        when(fixture.businessFileService.saveSalesOrderImportDrawing(eq("客户A"), eq("SO-400"), any(), eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("400")))
                .thenReturn(new BusinessFileService.StoredBusinessFile(
                        "/uploads/客户A/SO-400/P-700.pdf",
                        StorageType.LOCAL,
                        1L,
                        "客户A/SO-400/P-700.pdf",
                        "P-700.pdf",
                        "application/pdf",
                        5L,
                        null
                ));
        MockMultipartFile matchingDrawing = new MockMultipartFile("drawings", "P-700.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile alreadyBoundDrawing = new MockMultipartFile("drawings", "P-702.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile unmatchedDrawing = new MockMultipartFile("drawings", "NO-MATCH.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile skippedFile = new MockMultipartFile("drawings", "note.txt", "text/plain", "dummy".getBytes(StandardCharsets.UTF_8));

        Object result = invokeSupplement(fixture.service, 400L, List.of(matchingDrawing, alreadyBoundDrawing, unmatchedDrawing, skippedFile));

        assertThat(String.valueOf(result)).contains("成功绑定 2 份");
        assertThat((Map<String, Object>) result)
                .containsEntry("boundCount", 2)
                .containsEntry("alreadyBoundCount", 1)
                .containsEntry("unmatchedCount", 1)
                .containsEntry("skippedCount", 1);
        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper, times(2)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(SalesOrderItem::getId)
                .containsExactlyInAnyOrder(700L, 701L);
        assertThat(itemCaptor.getAllValues())
                .allSatisfy(updated -> {
                    assertThat(updated.getDrawingUrl()).isEqualTo("/uploads/客户A/SO-400/P-700.pdf");
                    assertThat(updated.getHasDrawing()).isTrue();
                });
        verify(fixture.auditService).record(eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("sales_order_header"), eq(400L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void supplementVersionDrawingBindsOnlyMatchingSalesOrderPartVersion() throws Exception {
        SalesOrderHeader header = header(410L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem itemVersionA = item(710L, 410L, QuoteStatusWorkflow.QUOTED);
        itemVersionA.setPartNo("P-200");
        itemVersionA.setVersion("A");
        SalesOrderItem itemVersionB = item(711L, 410L, QuoteStatusWorkflow.QUOTED);
        itemVersionB.setPartNo("P-200");
        itemVersionB.setVersion("B");
        Fixture fixture = fixture(header, List.of(itemVersionA, itemVersionB), 0L);
        when(fixture.businessFileService.saveSalesOrderImportDrawing(eq("客户A"), eq("SO-410"), any(), eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("410")))
                .thenReturn(new BusinessFileService.StoredBusinessFile(
                        "/uploads/客户A/SO-410/P-200_B.pdf",
                        StorageType.LOCAL,
                        1L,
                        "客户A/SO-410/P-200_B.pdf",
                        "P-200_B.pdf",
                        "application/pdf",
                        5L,
                        null
                ));
        MockMultipartFile versionDrawing = new MockMultipartFile(
                "versionDrawings",
                "P-200_B.pdf",
                "application/pdf",
                "dummy".getBytes(StandardCharsets.UTF_8)
        );

        Object result = invokeSupplement(fixture.service, 410L, List.of(), List.of(versionDrawing));

        assertThat((Map<String, Object>) result)
                .containsEntry("boundCount", 1)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getId()).isEqualTo(711L);
        assertThat(itemCaptor.getValue().getDrawingUrl()).isEqualTo("/uploads/客户A/SO-410/P-200_B.pdf");
        assertThat(itemCaptor.getValue().getHasDrawing()).isTrue();
        assertThat(itemVersionA.getDrawingUrl()).isNull();
        assertThat(itemVersionA.getHasDrawing()).isNull();

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_DRAWING_SUPPLEMENT"),
                eq("sales_order_header"),
                eq(410L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("uploadCount", 1)
                .containsEntry("uploadedDrawingCount", 0)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("boundCount", 1)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        assertThat((Map<String, Object>) afterData.get("drawingMatchModes"))
                .containsEntry("partNo", 0)
                .containsEntry("partNoVersion", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("dummy")
                .doesNotContain("MockMultipartFile")
                .doesNotContain("multipart");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderDetailSummaryIncludesStoredItemPriceTotalsForReadOnlyHeaderBoxes() {
        SalesOrderHeader header = header(420L, QuoteStatusWorkflow.RELEASED);
        Fixture fixture = fixture(header, List.of(), 0L);
        Map<String, Object> itemStats = new LinkedHashMap<>();
        itemStats.put("sales_order_id", 420L);
        itemStats.put("item_count", 2L);
        itemStats.put("total_quantity", 7L);
        itemStats.put("released_quantity", 3L);
        itemStats.put("drawing_missing_count", 0L);
        itemStats.put("order_untaxed_total_price", new BigDecimal("1234.56"));
        itemStats.put("order_tax_included_total_price", new BigDecimal("1395.05"));
        when(fixture.itemMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(itemStats));
        when(fixture.itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> detail = fixture.service.getOrderDetail(header.getId());

        Map<String, Object> summary = (Map<String, Object>) detail.get("summary");
        assertThat(summary)
                .containsEntry("orderUntaxedTotalPrice", new BigDecimal("1234.56"))
                .containsEntry("orderTaxIncludedTotalPrice", new BigDecimal("1395.05"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void supplementDrawingFolderBindsAllSalesOrderItemsAndStoresHeaderMetadata() throws Exception {
        SalesOrderHeader header = header(420L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem itemA = item(720L, 420L, QuoteStatusWorkflow.QUOTED);
        itemA.setPartNo("P-720");
        SalesOrderItem itemB = item(721L, 420L, QuoteStatusWorkflow.QUOTED);
        itemB.setPartNo("P-721");
        itemB.setDrawingUrl("/old/P-721.pdf");
        itemB.setHasDrawing(true);
        Fixture fixture = fixture(header, List.of(itemA, itemB), 0L);
        MockMultipartFile folderDrawing = new MockMultipartFile(
                "drawingFolderFiles",
                "folder/sales-order-pack.pdf",
                "application/pdf",
                "folder-pdf".getBytes(StandardCharsets.UTF_8)
        );
        String folderUrl = "/uploads/drawing-folders/SALES_ORDER/420/folder-420/";
        when(fixture.businessFileService.saveDrawingFolderFiles(
                eq("SALES_ORDER"), eq("420"), eq("销售订单图纸"), eq(List.of(folderDrawing)), eq("SALES_ORDER_DRAWING_SUPPLEMENT_FOLDER"), eq("420")))
                .thenReturn(drawingFolderResult(folderUrl, "销售订单图纸", folderDrawing));
        when(fixture.headerMapper.updateById(any(SalesOrderHeader.class))).thenReturn(1);
        when(fixture.itemMapper.updateById(any(SalesOrderItem.class))).thenReturn(1);

        Object result = invokeSupplement(
                fixture.service,
                420L,
                List.of(),
                List.of(),
                List.of(folderDrawing),
                "销售订单图纸");

        assertThat((Map<String, Object>) result)
                .containsEntry("boundCount", 2)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0)
                .containsEntry("drawingFolderFileCount", 1);
        assertThat(header.getDrawingFolderUrl()).isEqualTo(folderUrl);
        assertThat(header.getDrawingFolderName()).isEqualTo("销售订单图纸");
        assertThat(header.getDrawingFolderFileCount()).isEqualTo(1);
        assertThat(itemA.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(itemA.getHasDrawing()).isTrue();
        assertThat(itemB.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(itemB.getHasDrawing()).isTrue();

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_DRAWING_SUPPLEMENT"),
                eq("sales_order_header"),
                eq(420L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1)
                .containsEntry("boundCount", 2);
        assertThat(String.valueOf(afterDataCaptor.getValue())).doesNotContain(folderUrl).doesNotContain("folder-pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceDrawingFolderOverwritesAllSalesOrderItemsAndStoresHeaderMetadata() throws Exception {
        SalesOrderHeader header = header(421L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem itemA = item(722L, 421L, QuoteStatusWorkflow.QUOTED);
        itemA.setPartNo("P-722");
        itemA.setDrawingUrl("/old/P-722.pdf");
        itemA.setHasDrawing(true);
        SalesOrderItem itemB = item(723L, 421L, QuoteStatusWorkflow.QUOTED);
        itemB.setPartNo("P-723");
        Fixture fixture = fixture(header, List.of(itemA, itemB), 0L);
        MockMultipartFile folderDrawing = new MockMultipartFile(
                "drawingFolderFiles",
                "new-folder/sales-order-pack.pdf",
                "application/pdf",
                "folder-pdf".getBytes(StandardCharsets.UTF_8)
        );
        String folderUrl = "/uploads/drawing-folders/SALES_ORDER/421/folder-421/";
        when(fixture.businessFileService.saveDrawingFolderFiles(
                eq("SALES_ORDER"), eq("421"), eq("新销售订单图纸"), eq(List.of(folderDrawing)), eq("SALES_ORDER_DRAWING_REPLACE_FOLDER"), eq("421")))
                .thenReturn(drawingFolderResult(folderUrl, "新销售订单图纸", folderDrawing));
        when(fixture.headerMapper.updateById(any(SalesOrderHeader.class))).thenReturn(1);
        when(fixture.itemMapper.updateById(any(SalesOrderItem.class))).thenReturn(1);

        Object result = invokeReplace(
                fixture.service,
                421L,
                List.of(),
                List.of(),
                List.of(folderDrawing),
                "新销售订单图纸");

        assertThat((Map<String, Object>) result)
                .containsEntry("replacedCount", 2)
                .containsEntry("boundCount", 2)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1);
        assertThat(header.getDrawingFolderUrl()).isEqualTo(folderUrl);
        assertThat(header.getDrawingFolderName()).isEqualTo("新销售订单图纸");
        assertThat(header.getDrawingFolderFileCount()).isEqualTo(1);
        assertThat(itemA.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(itemB.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(itemA.getHasDrawing()).isTrue();
        assertThat(itemB.getHasDrawing()).isTrue();

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_DRAWING_REPLACE"),
                eq("sales_order_header"),
                eq(421L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1)
                .containsEntry("replacedCount", 2);
        assertThat(String.valueOf(afterDataCaptor.getValue())).doesNotContain(folderUrl).doesNotContain("folder-pdf");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceDrawingsOverwritesAlreadyBoundSalesOrderItemsByPartNoAndVersion() throws Exception {
        SalesOrderHeader header = header(430L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem itemA = item(730L, 430L, QuoteStatusWorkflow.QUOTED);
        itemA.setPartNo("P-730");
        itemA.setVersion("A");
        itemA.setDrawingUrl("/old/P-730-A.pdf");
        itemA.setHasDrawing(true);
        SalesOrderItem itemB = item(731L, 430L, QuoteStatusWorkflow.QUOTED);
        itemB.setPartNo("P-731");
        itemB.setVersion("B");
        itemB.setDrawingUrl("/old/P-731-B.pdf");
        itemB.setHasDrawing(true);
        Fixture fixture = fixture(header, List.of(itemA, itemB), 0L);
        when(fixture.businessFileService.saveSalesOrderImportDrawing(eq("客户A"), eq("SO-430"), any(), eq("SALES_ORDER_DRAWING_REPLACE"), eq("430")))
                .thenReturn(new BusinessFileService.StoredBusinessFile(
                        "/uploads/客户A/SO-430/replaced.pdf",
                        StorageType.LOCAL,
                        1L,
                        "客户A/SO-430/replaced.pdf",
                        "replaced.pdf",
                        "application/pdf",
                        5L,
                        null
                ));
        when(fixture.itemMapper.updateById(any(SalesOrderItem.class))).thenReturn(1);
        MockMultipartFile versionDrawing = new MockMultipartFile("versionDrawings", "P-730_A.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile partDrawing = new MockMultipartFile("drawings", "P-731.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        Object result = invokeReplace(fixture.service, 430L, List.of(partDrawing), List.of(versionDrawing), List.of(), "");

        assertThat((Map<String, Object>) result)
                .containsEntry("replacedCount", 2)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper, times(2)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(SalesOrderItem::getId)
                .containsExactlyInAnyOrder(730L, 731L);
        assertThat(itemCaptor.getAllValues()).allSatisfy(updated -> {
            assertThat(updated.getDrawingUrl()).isEqualTo("/uploads/客户A/SO-430/replaced.pdf");
            assertThat(updated.getHasDrawing()).isTrue();
        });
        verify(fixture.auditService).record(eq("SALES_ORDER_DRAWING_REPLACE"), eq("sales_order_header"), eq(430L), isNull(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    private Object invokeStatusAction(SalesOrderManageService service, String action, Long salesOrderId, String reason) throws Exception {
        Method method = SalesOrderManageService.class.getMethod(action, Long.class, OrderStatusChangeReq.class);
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(reason);
        try {
            return method.invoke(service, salesOrderId, req);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeItemStatusAction(SalesOrderManageService service, String action, Long salesOrderId, Long itemId, String reason) throws Exception {
        Method method = SalesOrderManageService.class.getMethod(action, Long.class, Long.class, OrderStatusChangeReq.class);
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(reason);
        try {
            return method.invoke(service, salesOrderId, itemId, req);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeSupplement(SalesOrderManageService service, Long salesOrderId, List<MultipartFile> drawings) throws Exception {
        Method method = SalesOrderManageService.class.getMethod("supplementOrderDrawings", Long.class, List.class);
        try {
            return method.invoke(service, salesOrderId, drawings);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeSupplement(
            SalesOrderManageService service,
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) throws Exception {
        Method method = SalesOrderManageService.class.getMethod("supplementOrderDrawings", Long.class, List.class, List.class);
        try {
            return method.invoke(service, salesOrderId, drawings, versionDrawings);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeReplace(
            SalesOrderManageService service,
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) throws Exception {
        Method method = SalesOrderManageService.class.getMethod("replaceOrderDrawings", Long.class, List.class, List.class, List.class, String.class);
        try {
            return method.invoke(service, salesOrderId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeSupplement(
            SalesOrderManageService service,
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) throws Exception {
        Method method = SalesOrderManageService.class.getMethod("supplementOrderDrawings", Long.class, List.class, List.class, List.class, String.class);
        try {
            return method.invoke(service, salesOrderId, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Fixture fixture(SalesOrderHeader header, List<SalesOrderItem> items, Long ticketCount) {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        when(headerMapper.selectById(header.getId())).thenReturn(header);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
        when(itemMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(ticketMapper.selectCount(any(QueryWrapper.class))).thenReturn(ticketCount);
        when(ticketMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        SalesOrderManageService service = new SalesOrderManageService();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        return new Fixture(service, headerMapper, itemMapper, ticketMapper, dataScopeService, auditService, businessFileService, header);
    }

    private SalesOrderHeader header(Long id, String status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SO-" + id);
        header.setCustomerId(88L);
        header.setCustomerName("客户A");
        header.setStatus(status);
        header.setU9SyncStatus("未同步");
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setQty(10);
        item.setReleasedQty(0);
        item.setStatus(status);
        return item;
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private BusinessFileService.DrawingFolderSaveResult drawingFolderResult(
            String folderUrl,
            String folderName,
            MockMultipartFile drawing) {
        BusinessFileService.DrawingFolderFile file = new BusinessFileService.DrawingFolderFile(
                drawing.getOriginalFilename(),
                folderUrl + "file-1.pdf",
                drawing.getContentType(),
                drawing.getSize(),
                java.time.LocalDateTime.now());
        return new BusinessFileService.DrawingFolderSaveResult(folderUrl, folderName, 1, List.of(file));
    }

    private record Fixture(
            SalesOrderManageService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            BusinessFileService businessFileService,
            SalesOrderHeader header) {
    }
}
