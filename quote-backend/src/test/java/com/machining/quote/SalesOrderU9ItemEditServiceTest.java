package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.dto.SalesOrderU9ItemUpdateLineReq;
import com.machining.quote.dto.SalesOrderU9ItemUpdateReq;
import com.machining.quote.dto.SalesOrderU9ItemUpdateResp;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9ItemEditService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
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

class SalesOrderU9ItemEditServiceTest {
    @Test
    void controllerExposesItemSaveWithSubmitPermission() throws Exception {
        Method method = SalesOrderU9SyncController.class.getMethod(
                "saveItems", Long.class, SalesOrderU9ItemUpdateReq.class);

        assertThat(method.getAnnotation(PutMapping.class).value())
                .containsExactly("/orders/{salesOrderId}/items");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveItemsLocksHeaderAndLineUpdatesAllowedFieldsAndRecalculatesDerivedValuesForU9Preview() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(100L, "未同步");
        SalesOrderItem item = item(200L, header.getId());
        fixture.withOrder(header, item);
        SalesOrderU9SyncPreviewResp preview = preview(header);
        when(fixture.previewService.previewSalesOrder(header.getId())).thenReturn(preview);

        SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
        SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setQty(8);
        line.setCustomerDeliveryDate(LocalDate.of(2026, 9, 30));
        line.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        line.setUntaxedUnitPrice(new BigDecimal("12.50"));
        line.setTaxIncludedUnitPrice(new BigDecimal("16.00"));
        line.setCustomerOrderNo(" PO-900 ");
        line.setCustomerProjectNo(" CP-900 ");
        line.setInternalProjectNo(" IP-900 ");
        line.setRemark(" U9 ready ");
        req.setItems(List.of(line));

        SalesOrderU9ItemUpdateResp resp = fixture.service.saveItems(header.getId(), req);

        assertThat(resp.getSuccess()).isTrue();
        assertThat(resp.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(resp.getSalesOrderNo()).isEqualTo(header.getSalesOrderNo());
        assertThat(resp.getUpdatedCount()).isEqualTo(1);
        assertThat(resp.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "untaxedUnitPrice",
                "taxIncludedUnitPrice",
                "customerOrderNo",
                "customerProjectNo",
                "internalProjectNo",
                "remark");
        assertThat(resp.getPreview()).isSameAs(preview);

        assertThat(item.getQty()).isEqualTo(8);
        assertThat(item.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(item.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getTaxIncludedUnitPrice()).isEqualByComparingTo("16.00");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("128.00");
        assertThat(item.getRemainingQty()).isEqualTo(5);
        assertThat(item.getUnshippedQty()).isEqualTo(6);
        assertThat(item.getCustomerOrderNo()).isEqualTo("PO-900");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-900");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-900");
        assertThat(item.getRemark()).isEqualTo("U9 ready");

        U9SalesOrderHeadDTO u9Request = new U9SalesOrderRequestAssembler().assemble(header, List.of(item));
        assertThat(u9Request.getIsPriceIncludeTax()).isTrue();
        assertThat(u9Request.getSOLineDTOList()).singleElement().satisfies(u9Line -> {
            assertThat(u9Line.getOrderByQtyTU()).isEqualByComparingTo("8");
            assertThat(u9Line.getFinallyPriceTC()).isEqualByComparingTo("16.00");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg1()).isEqualTo("CP-900");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg2()).isEqualTo("PO-900");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg3()).isEqualTo("IP-900");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg4()).isEqualTo("2026-09-30");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg5()).isEqualTo("2026-09-25");
            assertThat(u9Line.getDescFlexField().getPrivateDescSeg9()).isEqualTo("16");
            assertThat(u9Line.getDescFlexField().getPubDescSeg2()).isEqualTo("U9 ready");
            assertThat(u9Line.getSOShiplineDTOList()).singleElement().satisfies(shipLine -> {
                assertThat(shipLine.getShipPlanQtyTU()).isEqualByComparingTo("8");
                assertThat(shipLine.getRequireDate()).isEqualTo("2026-09-30");
                assertThat(shipLine.getDeliveryDate()).isEqualTo("2026-09-25");
            });
        });

        verify(fixture.headerMapper).selectByIdForUpdate(header.getId());
        verify(fixture.itemMapper).selectByIdForUpdate(item.getId());
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.previewService).previewSalesOrder(header.getId());

        ArgumentCaptor<Object> auditCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_U9_ITEM_UPDATE"),
                eq("sales_order_header"),
                eq(header.getId()),
                isNull(),
                auditCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_U9_ITEM_UPDATE")),
                isNull()
        );
        Map<String, Object> auditSummary = (Map<String, Object>) auditCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("salesOrderId", header.getId())
                .containsEntry("salesOrderNo", header.getSalesOrderNo())
                .containsEntry("updatedCount", 1)
                .containsKey("durationMs");
        assertThat(auditSummary.get("durationMs")).isInstanceOf(Number.class);
    }

    @Test
    void saveItemsRejectsDuplicateLineIdsBeforeUpdating() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "未同步");
        SalesOrderItem item = item(201L, header.getId());
        fixture.withOrder(header, item);

        SalesOrderU9ItemUpdateLineReq first = new SalesOrderU9ItemUpdateLineReq();
        first.setSalesOrderItemId(item.getId());
        first.setQty(3);
        SalesOrderU9ItemUpdateLineReq duplicate = new SalesOrderU9ItemUpdateLineReq();
        duplicate.setSalesOrderItemId(item.getId());
        duplicate.setRemark("dup");
        SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
        req.setItems(List.of(first, duplicate));

        assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("重复")
                .hasMessageContaining(String.valueOf(item.getId()));

        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.previewService, never()).previewSalesOrder(any());
    }

    @Test
    void saveItemsRejectsLineThatDoesNotBelongToCurrentSalesOrder() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(102L, "未同步");
        SalesOrderItem item = item(202L, 999L);
        fixture.withOrder(header, item);

        SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setQty(3);
        SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
        req.setItems(List.of(line));

        assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不属于当前销售订单");

        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.previewService, never()).previewSalesOrder(any());
    }

    @Test
    void saveItemsRejectsU9ChangeExcludedSalesOrderItem() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(107L, "未同步");
        SalesOrderItem item = item(207L, header.getId());
        ReflectionTestUtils.setField(item, "u9ChangeExcluded", true);
        fixture.withOrder(header, item);

        SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setRemark("excluded");
        SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
        req.setItems(List.of(line));

        assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已从 U9 同步和销售变更排除");

        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.previewService, never()).previewSalesOrder(any());
    }

    @Test
    void saveItemsRejectsInvalidQuantityAndPrice() {
        Fixture invalidQty = fixture();
        SalesOrderHeader qtyHeader = header(103L, "未同步");
        SalesOrderItem qtyItem = item(203L, qtyHeader.getId());
        invalidQty.withOrder(qtyHeader, qtyItem);
        SalesOrderU9ItemUpdateLineReq qtyLine = new SalesOrderU9ItemUpdateLineReq();
        qtyLine.setSalesOrderItemId(qtyItem.getId());
        qtyLine.setQty(0);
        SalesOrderU9ItemUpdateReq qtyReq = new SalesOrderU9ItemUpdateReq();
        qtyReq.setItems(List.of(qtyLine));

        assertThatThrownBy(() -> invalidQty.service.saveItems(qtyHeader.getId(), qtyReq))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("数量");
        verify(invalidQty.itemMapper, never()).update(any(), any());

        Fixture invalidPrice = fixture();
        SalesOrderHeader priceHeader = header(104L, "未同步");
        SalesOrderItem priceItem = item(204L, priceHeader.getId());
        invalidPrice.withOrder(priceHeader, priceItem);
        SalesOrderU9ItemUpdateLineReq priceLine = new SalesOrderU9ItemUpdateLineReq();
        priceLine.setSalesOrderItemId(priceItem.getId());
        priceLine.setUntaxedUnitPrice(new BigDecimal("-0.01"));
        SalesOrderU9ItemUpdateReq priceReq = new SalesOrderU9ItemUpdateReq();
        priceReq.setItems(List.of(priceLine));

        assertThatThrownBy(() -> invalidPrice.service.saveItems(priceHeader.getId(), priceReq))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("未税单价");
        verify(invalidPrice.itemMapper, never()).update(any(), any());
    }

    @Test
    void saveItemsRejectsQuantityBelowReleasedOrShippedQuantity() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(105L, "未同步");
        SalesOrderItem item = item(205L, header.getId());
        item.setReleasedQty(4);
        item.setShippedQty(3);
        fixture.withOrder(header, item);
        SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
        line.setSalesOrderItemId(item.getId());
        line.setQty(2);
        SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
        req.setItems(List.of(line));

        assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已提数量")
                .hasMessageContaining("已出货数量");

        verify(fixture.itemMapper, never()).update(any(), any());
    }

    @Test
    void saveItemsRejectsLockedU9StatusesBeforeLineUpdate() {
        for (String status : List.of("已同步", "同步中", "结果待确认")) {
            Fixture fixture = fixture();
            SalesOrderHeader header = header(106L, status);
            SalesOrderItem item = item(206L, header.getId());
            fixture.withOrder(header, item);
            SalesOrderU9ItemUpdateLineReq line = new SalesOrderU9ItemUpdateLineReq();
            line.setSalesOrderItemId(item.getId());
            line.setRemark("locked");
            SalesOrderU9ItemUpdateReq req = new SalesOrderU9ItemUpdateReq();
            req.setItems(List.of(line));

            assertThatThrownBy(() -> fixture.service.saveItems(header.getId(), req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(status)
                    .hasMessageContaining("不能修改");

            verify(fixture.itemMapper, never()).selectByIdForUpdate(any());
            verify(fixture.itemMapper, never()).update(any(), any());
            verify(fixture.previewService, never()).previewSalesOrder(any());
        }
    }

    private Fixture fixture() {
        SalesOrderU9ItemEditService service = new SalesOrderU9ItemEditService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "previewService", fixture.previewService);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SOQPO-" + id);
        header.setDocumentNo("DOC-" + id);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-A");
        header.setCustomerName("客户A");
        header.setCustomerProjectNo("HEADER-CP");
        header.setCustomerOrderNo("HEADER-PO");
        header.setInternalProjectNo("HEADER-IP");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 17));
        header.setStatus("已生成提单");
        header.setU9SyncStatus(u9Status);
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
        return item;
    }

    private SalesOrderU9SyncPreviewResp preview(SalesOrderHeader header) {
        SalesOrderU9SyncPreviewResp preview = new SalesOrderU9SyncPreviewResp();
        preview.setSalesOrderId(header.getId());
        preview.setSalesOrderNo(header.getSalesOrderNo());
        return preview;
    }

    private static final class Fixture {
        private final SalesOrderU9ItemEditService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final SalesOrderU9SyncService previewService = mock(SalesOrderU9SyncService.class);
        private final AuditService auditService = mock(AuditService.class);

        private Fixture(SalesOrderU9ItemEditService service) {
            this.service = service;
        }

        private void withOrder(SalesOrderHeader header, SalesOrderItem item) {
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        }
    }
}
