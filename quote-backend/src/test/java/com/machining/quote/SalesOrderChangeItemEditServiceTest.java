package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderChangeController;
import com.machining.quote.dto.SalesOrderChangeItemUpdateReq;
import com.machining.quote.dto.SalesOrderChangeItemUpdateResp;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.dto.SalesOrderU9ItemUpdateLineReq;
import com.machining.quote.entity.SalesOrderChangeRecord;
import com.machining.quote.entity.SalesOrderChangeRecordItem;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderChangeRecordItemMapper;
import com.machining.quote.mapper.SalesOrderChangeRecordMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderChangeItemEditService;
import com.machining.quote.service.SalesOrderChangeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderChangeItemEditServiceTest {
    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void controllerExposesItemSaveWithChangeUpdatePermission() throws Exception {
        Method method = SalesOrderChangeController.class.getMethod(
                "saveItems", Long.class, SalesOrderChangeItemUpdateReq.class);

        assertThat(method.getAnnotation(PutMapping.class).value())
                .containsExactly("/{salesOrderId}/items");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("change:sales-order:update");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveItemsRequiresReasonUpdatesAllowedFieldsRecalculatesDerivedValuesAndWritesChangeAudit() {
        AuthContext.set(new AuthContext.SessionUser(7, "market01", "市场一号", "MARKET_USER"));
        Fixture fixture = fixture();
        SalesOrderHeader header = header(100L, "已同步");
        SalesOrderItem item = item(200L, header.getId());
        fixture.withOrder(header, item);

        SalesOrderChangePreviewResp preview = new SalesOrderChangePreviewResp();
        preview.setSalesOrderId(header.getId());
        preview.setSalesOrderNo(header.getSalesOrderNo());
        when(fixture.previewService.preview(header.getId())).thenReturn(preview);
        when(fixture.changeRecordMapper.insert(any(SalesOrderChangeRecord.class))).thenAnswer(invocation -> {
            SalesOrderChangeRecord record = invocation.getArgument(0);
            record.setId(9001L);
            return 1;
        });
        when(fixture.changeRecordItemMapper.insert(any(SalesOrderChangeRecordItem.class))).thenReturn(1);

        SalesOrderChangeItemUpdateReq req = request("U9 已人工调整，QPO 本地校正", item.getId());
        SalesOrderU9ItemUpdateLineReq line = req.getItems().get(0);
        line.setQty(8);
        line.setCustomerDeliveryDate(LocalDate.of(2026, 9, 30));
        line.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        line.setUntaxedUnitPrice(new BigDecimal("12.50"));
        line.setCustomerOrderNo(" PO-900 ");
        line.setCustomerProjectNo(" CP-900 ");
        line.setInternalProjectNo(" IP-900 ");
        line.setRemark(" U9 adjusted ");

        SalesOrderChangeItemUpdateResp resp = fixture.service.saveItems(header.getId(), req);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(resp.getSalesOrderNo()).isEqualTo(header.getSalesOrderNo());
        assertThat(resp.getChangeRecordId()).isEqualTo(9001L);
        assertThat(resp.getUpdatedCount()).isEqualTo(1);
        assertThat(resp.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "untaxedUnitPrice",
                "customerOrderNo",
                "customerProjectNo",
                "internalProjectNo",
                "remark");
        assertThat(resp.getPreview()).isSameAs(preview);

        assertThat(item.getQty()).isEqualTo(8);
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("120.00");
        assertThat(item.getRemainingQty()).isEqualTo(5);
        assertThat(item.getUnshippedQty()).isEqualTo(6);
        assertThat(header.getU9SyncStatus()).isEqualTo("已同步");

        verify(fixture.headerMapper).selectByIdForUpdate(header.getId());
        verify(fixture.itemMapper).selectByIdForUpdate(item.getId());
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.previewService).preview(header.getId());

        ArgumentCaptor<SalesOrderChangeRecord> recordCaptor = ArgumentCaptor.forClass(SalesOrderChangeRecord.class);
        verify(fixture.changeRecordMapper).insert(recordCaptor.capture());
        SalesOrderChangeRecord record = recordCaptor.getValue();
        assertThat(record.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(record.getSalesOrderNo()).isEqualTo(header.getSalesOrderNo());
        assertThat(record.getChangeType()).isEqualTo("ITEM_UPDATE");
        assertThat(record.getReason()).isEqualTo("U9 已人工调整，QPO 本地校正");
        assertThat(record.getUpdatedCount()).isEqualTo(1);
        assertThat(record.getChangedFieldsJson()).contains("qty", "remark");
        assertThat(record.getCreatedBy()).isEqualTo(7L);
        assertThat(record.getCreatedByName()).isEqualTo("市场一号");

        ArgumentCaptor<SalesOrderChangeRecordItem> detailCaptor = ArgumentCaptor.forClass(SalesOrderChangeRecordItem.class);
        verify(fixture.changeRecordItemMapper).insert(detailCaptor.capture());
        SalesOrderChangeRecordItem detail = detailCaptor.getValue();
        assertThat(detail.getChangeRecordId()).isEqualTo(9001L);
        assertThat(detail.getSalesOrderItemId()).isEqualTo(item.getId());
        assertThat(detail.getLineNo()).isEqualTo(item.getLineNo());
        assertThat(detail.getPartNo()).isEqualTo(item.getPartNo());
        assertThat(detail.getVersion()).isEqualTo(item.getVersion());
        assertThat(detail.getChangedFieldsJson()).contains("qty", "remark");
        assertThat(detail.getBeforeSnapshotJson()).contains("qty", "5", "untaxedUnitPrice", "10.00");
        assertThat(detail.getAfterSnapshotJson()).contains("qty", "8", "untaxedUnitPrice", "12.50");
        assertThat(detail.getAfterSnapshotJson())
                .doesNotContain("drawingUrl", "u9Request", "u9Response", "Authorization", "token");

        ArgumentCaptor<Object> auditCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_CHANGE_ITEM_UPDATE"),
                eq("sales_order_header"),
                eq(header.getId()),
                isNull(),
                auditCaptor.capture(),
                eq("CHANGE"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_UPDATE")),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) auditCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("salesOrderId", header.getId())
                .containsEntry("salesOrderNo", header.getSalesOrderNo())
                .containsEntry("changeRecordId", 9001L)
                .containsEntry("reason", "U9 已人工调整，QPO 本地校正")
                .containsEntry("updatedCount", 1)
                .containsKey("durationMs");
    }

    @Test
    void saveItemsRejectsBlankReasonNonSyncedStatusDuplicateLinesForeignLinesInvalidQtyAndInvalidPrice() {
        Fixture missingReason = fixture();
        SalesOrderHeader synced = header(101L, "已同步");
        SalesOrderItem syncedItem = item(201L, synced.getId());
        missingReason.withOrder(synced, syncedItem);
        assertThatThrownBy(() -> missingReason.service.saveItems(synced.getId(), request(" ", syncedItem.getId())))
                .hasMessageContaining("变更原因");
        verify(missingReason.itemMapper, never()).update(any(), any());

        Fixture wrongStatus = fixture();
        SalesOrderHeader pending = header(102L, "结果待确认");
        SalesOrderItem pendingItem = item(202L, pending.getId());
        wrongStatus.withOrder(pending, pendingItem);
        assertThatThrownBy(() -> wrongStatus.service.saveItems(pending.getId(), request("本地校正", pendingItem.getId())))
                .hasMessageContaining("只允许变更已同步 U9 的销售订单");
        verify(wrongStatus.itemMapper, never()).selectByIdForUpdate(any());

        Fixture duplicate = fixture();
        SalesOrderHeader dupHeader = header(103L, "已同步");
        SalesOrderItem dupItem = item(203L, dupHeader.getId());
        duplicate.withOrder(dupHeader, dupItem);
        SalesOrderChangeItemUpdateReq duplicateReq = new SalesOrderChangeItemUpdateReq();
        duplicateReq.setReason("本地校正");
        SalesOrderU9ItemUpdateLineReq first = line(dupItem.getId());
        first.setQty(6);
        SalesOrderU9ItemUpdateLineReq second = line(dupItem.getId());
        second.setRemark("重复");
        duplicateReq.setItems(List.of(first, second));
        assertThatThrownBy(() -> duplicate.service.saveItems(dupHeader.getId(), duplicateReq))
                .hasMessageContaining("重复")
                .hasMessageContaining(String.valueOf(dupItem.getId()));
        verify(duplicate.itemMapper, never()).update(any(), any());

        Fixture foreign = fixture();
        SalesOrderHeader foreignHeader = header(104L, "已同步");
        SalesOrderItem foreignItem = item(204L, 999L);
        foreign.withOrder(foreignHeader, foreignItem);
        assertThatThrownBy(() -> foreign.service.saveItems(foreignHeader.getId(), request("本地校正", foreignItem.getId())))
                .hasMessageContaining("不属于当前销售订单");
        verify(foreign.itemMapper, never()).update(any(), any());

        Fixture invalidQty = fixture();
        SalesOrderHeader qtyHeader = header(105L, "已同步");
        SalesOrderItem qtyItem = item(205L, qtyHeader.getId());
        invalidQty.withOrder(qtyHeader, qtyItem);
        SalesOrderChangeItemUpdateReq qtyReq = request("本地校正", qtyItem.getId());
        qtyReq.getItems().get(0).setQty(1);
        assertThatThrownBy(() -> invalidQty.service.saveItems(qtyHeader.getId(), qtyReq))
                .hasMessageContaining("已提数量")
                .hasMessageContaining("已出货数量");
        verify(invalidQty.itemMapper, never()).update(any(), any());

        Fixture invalidPrice = fixture();
        SalesOrderHeader priceHeader = header(106L, "已同步");
        SalesOrderItem priceItem = item(206L, priceHeader.getId());
        invalidPrice.withOrder(priceHeader, priceItem);
        SalesOrderChangeItemUpdateReq priceReq = request("本地校正", priceItem.getId());
        priceReq.getItems().get(0).setUntaxedUnitPrice(new BigDecimal("-0.01"));
        assertThatThrownBy(() -> invalidPrice.service.saveItems(priceHeader.getId(), priceReq))
                .hasMessageContaining("未税单价");
        verify(invalidPrice.itemMapper, never()).update(any(), any());
    }

    @Test
    void saveItemsRejectsU9ChangeExcludedSalesOrderItem() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(107L, "已同步");
        SalesOrderItem item = item(207L, header.getId());
        ReflectionTestUtils.setField(item, "u9ChangeExcluded", true);
        fixture.withOrder(header, item);

        assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), request("本地校正", item.getId())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已从 U9 同步和销售变更排除");

        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.changeRecordMapper, never()).insert(any());
        verify(fixture.previewService, never()).preview(any());
    }

    private Fixture fixture() {
        SalesOrderChangeItemEditService service = new SalesOrderChangeItemEditService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "changeRecordMapper", fixture.changeRecordMapper);
        ReflectionTestUtils.setField(service, "changeRecordItemMapper", fixture.changeRecordItemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "previewService", fixture.previewService);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(fixture.changeRecordMapper.insert(any(SalesOrderChangeRecord.class))).thenAnswer(invocation -> {
            SalesOrderChangeRecord record = invocation.getArgument(0);
            record.setId(7001L);
            return 1;
        });
        when(fixture.changeRecordItemMapper.insert(any(SalesOrderChangeRecordItem.class))).thenReturn(1);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SOQPO-" + id);
        header.setDocumentNo("DOC-" + id);
        header.setCustomerCode("CUST-A");
        header.setCustomerName("客户A");
        header.setCustomerProjectNo("HEADER-CP");
        header.setCustomerOrderNo("HEADER-PO");
        header.setInternalProjectNo("HEADER-IP");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 24));
        header.setStatus("已生成提单");
        header.setU9SyncStatus(u9Status);
        header.setU9OrderCode("U9-SO-" + id);
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("LK17.5.1.1-1");
        item.setVersion("A");
        item.setMaterialNo("100201000431");
        item.setMaterialNoStatus("已确认");
        item.setQty(5);
        item.setReleasedQty(3);
        item.setShippedQty(2);
        item.setRemainingQty(2);
        item.setUnshippedQty(3);
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setUntaxedTotalPrice(new BigDecimal("50.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("15.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("75.00"));
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 10));
        item.setInternalDeliveryDate(LocalDate.of(2026, 9, 8));
        item.setCustomerOrderNo("PO-OLD");
        item.setCustomerProjectNo("CP-OLD");
        item.setInternalProjectNo("IP-OLD");
        item.setRemark("old remark");
        item.setDrawingUrl("https://private.example/drawing.pdf");
        return item;
    }

    private SalesOrderChangeItemUpdateReq request(String reason, Long itemId) {
        SalesOrderChangeItemUpdateReq req = new SalesOrderChangeItemUpdateReq();
        req.setReason(reason);
        SalesOrderU9ItemUpdateLineReq line = line(itemId);
        line.setRemark("本地校正");
        req.setItems(List.of(line));
        return req;
    }

    private SalesOrderU9ItemUpdateLineReq line(Long itemId) {
        SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
        line.setSalesOrderItemId(itemId);
        return line;
    }

    private static final class Fixture {
        private final SalesOrderChangeItemEditService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final SalesOrderChangeRecordMapper changeRecordMapper = mock(SalesOrderChangeRecordMapper.class);
        private final SalesOrderChangeRecordItemMapper changeRecordItemMapper = mock(SalesOrderChangeRecordItemMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final SalesOrderChangeService previewService = mock(SalesOrderChangeService.class);
        private final AuditService auditService = mock(AuditService.class);

        private Fixture(SalesOrderChangeItemEditService service) {
            this.service = service;
        }

        private void withOrder(SalesOrderHeader header, SalesOrderItem item) {
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        }
    }
}
