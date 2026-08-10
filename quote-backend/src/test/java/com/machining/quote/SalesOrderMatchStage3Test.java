package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderMatchApplyReq;
import com.machining.quote.dto.SalesOrderMatchApplyResp;
import com.machining.quote.dto.SalesOrderMatchPreviewReq;
import com.machining.quote.dto.SalesOrderMatchPreviewResp;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderMatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderMatchStage3Test {
    @Test
    void controllerExposesApplyEndpointWithApplyPermission() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/machining/quote/controller/SalesOrderMatchController.java"), StandardCharsets.UTF_8);

        assertThat(controller)
                .contains("@PostMapping(\"/apply\")")
                .contains("@PostMapping(\"/orders/{salesOrderId}/apply\")")
                .contains("@RequirePermission(\"market:sales-match:apply\")")
                .contains("SalesOrderMatchApplyReq")
                .contains("salesOrderMatchService.apply");
    }

    @Test
    void applyCopiesProductionSnapshotWithoutOverwritingSalesOrderFactsAndRecordsAudit() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialName("模板材料");
        item.setSurfaceTreatment("模板表处");
        item.setDrawingUrl("/sales-order/uploaded/P-001.pdf");
        item.setHasDrawing(true);
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.apply(applyReq(List.of(item.getId())));

        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getUnmatchedItemCount()).isZero();
        assertThat(resp.getUpdatedItemCount()).isEqualTo(1);
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSalesOrderItemId()).isEqualTo(item.getId());
                    assertThat(row.getMatched()).isTrue();
                    assertThat(row.getAssemblyRelationStatus()).isEqualTo("不适用");
                });

        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialName()).isEqualTo("模板材料");
        assertThat(updated.getSurfaceTreatment()).isEqualTo("模板表处");
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-历史材料");
        assertThat(updated.getMaterialGrossWeight()).isEqualTo(1.25);
        assertThat(updated.getPartNetWeight()).isEqualTo(0.80);
        assertThat(updated.getQuoteMode()).isEqualTo("NORMAL");
        assertThat(updated.getItemType()).isEqualTo("普通件");
        assertThat(updated.getMaterialData()).isEqualTo(source.getMaterialData());
        assertThat(updated.getProcessRoute()).isEqualTo(source.getProcessRoute());
        assertThat(updated.getProcessRoute()).isNotSameAs(source.getProcessRoute());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copiedRoute = (List<Map<String, Object>>) updated.getProcessRoute();
        assertThat(copiedRoute).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("processCode", "PACKAGING");
            assertThat(route).containsEntry("categoryName", "包装");
            assertThat(route).containsEntry("detailName", "精细包装");
            assertThat(route).containsEntry("hourlyRate", 10.0);
            assertThat(route).containsEntry("lineCost", 15.0);
        });
        assertThat(updated.getFeeData()).isEqualTo(source.getFeeData());
        assertThat(updated.getDrawingUrl()).isEqualTo("/sales-order/uploaded/P-001.pdf");
        assertThat(updated.getHasDrawing()).isTrue();
        assertThat(updated.getMatchedRfqId()).isEqualTo(21);
        assertThat(updated.getMatchedItemId()).isEqualTo(901);
        assertThat(updated.getMatchStatus()).isEqualTo("已匹配");

        assertThat(updated.getQty()).isEqualTo(10);
        assertThat(updated.getMaterialNo()).isEqualTo("MES-MAT-501");
        assertThat(updated.getTaxIncludedUnitPrice()).isEqualByComparingTo("11.30");
        assertThat(updated.getUntaxedUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(updated.getTaxIncludedTotalPrice()).isEqualByComparingTo("113.00");
        assertThat(updated.getUntaxedTotalPrice()).isEqualByComparingTo("100.00");
        assertThat(updated.getCustomerOrderNo()).isEqualTo("PO-101");
        assertThat(updated.getCustomerProjectNo()).isEqualTo("CP-101");
        assertThat(updated.getInternalProjectNo()).isEqualTo("IP-101");
        assertThat(updated.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(updated.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 5));

        SalesOrderHeader summary = fixture.lastHeaderUpdate();
        assertThat(summary.getMatchedItemCount()).isEqualTo(1);
        assertThat(summary.getUnmatchedItemCount()).isZero();
        assertThat(summary.getWarningMessage()).isEmpty();
        verify(fixture.auditService).record(eq("SALES_ORDER_MATCH_APPLY"), eq("sales_order_header"), eq(header.getId()), isNull(), any());
    }

    @Test
    void applyOrderWrapsSingleQuotedSalesOrder() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        fixture.withLockedItems(header, List.of(item));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.applyOrder(header.getId());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getTotalItemCount()).isEqualTo(1);
        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(fixture.lastItemUpdate().getMatchStatus()).isEqualTo("已匹配");
    }

    @Test
    void applyImportedOrderUsesImportAutoTriggerAndKeepsImportedFactsProtected() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialName("IMPORTED-MATERIAL");
        item.setSurfaceTreatment("IMPORTED-SURFACE");
        item.setDrawingUrl("/sales-order/uploaded/P-001.pdf");
        item.setHasDrawing(true);
        fixture.withLockedItems(header, List.of(item));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL", "NORMAL");
        source.setMaterialData(Map.of("name", "HISTORY-MATERIAL", "shape", "BAR", "cost", 50));
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.applyImportedOrder(header.getId());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getUnmatchedItemCount()).isZero();
        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-HISTORY-MATERIAL");
        assertThat(updated.getMaterialShape()).isNotBlank();
        assertThat(updated.getMaterialData()).isEqualTo(source.getMaterialData());
        assertThat(updated.getProcessRoute()).isEqualTo(source.getProcessRoute());
        assertThat(updated.getFeeData()).isEqualTo(source.getFeeData());
        assertThat(updated.getMaterialGrossWeight()).isEqualTo(1.25);
        assertThat(updated.getPartNetWeight()).isEqualTo(0.80);
        assertThat(updated.getMaterialName()).isEqualTo("IMPORTED-MATERIAL");
        assertThat(updated.getSurfaceTreatment()).isEqualTo("IMPORTED-SURFACE");
        assertThat(updated.getDrawingUrl()).isEqualTo("/sales-order/uploaded/P-001.pdf");
        assertThat(updated.getQty()).isEqualTo(10);
        assertThat(updated.getMaterialNo()).isEqualTo("MES-MAT-501");

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_MATCH_APPLY"),
                eq("sales_order_header"),
                eq(header.getId()),
                isNull(),
                afterDataCaptor.capture());
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("trigger", "IMPORT_AUTO")
                .containsEntry("matchedItemCount", 1)
                .containsEntry("unmatchedItemCount", 0);
    }

    @Test
    void previewAndApplyTreatImportedEmptyJsonSnapshotsAsMissingAndDeepCopyHistorySnapshots() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialData(Map.of());
        item.setProcessRoute(List.of());
        item.setFeeData(List.of());
        fixture.withLockedItems(header, List.of(item));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL", "NORMAL");
        Map<String, Object> sourceMaterial = mutableMaterialSnapshot("HISTORY-MATERIAL");
        List<Map<String, Object>> sourceRoute = mutableRouteSnapshot("CUT");
        List<Map<String, Object>> sourceFee = mutableFeeSnapshot("management");
        source.setMaterialData(sourceMaterial);
        source.setProcessRoute(sourceRoute);
        source.setFeeData(sourceFee);
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchPreviewReq previewReq = new SalesOrderMatchPreviewReq();
        previewReq.setSalesOrderIds(List.of(header.getId()));
        SalesOrderMatchPreviewResp preview = fixture.service.preview(previewReq);

        assertThat(preview.getRows()).singleElement().satisfies(row -> assertThat(row.getChangedFields())
                .containsEntry("materialSnapshotAvailable", true)
                .containsEntry("processSnapshotAvailable", true)
                .containsEntry("feeSnapshotAvailable", true));

        fixture.service.apply(applyReq(List.of(item.getId())));

        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialData()).isEqualTo(sourceMaterial).isNotSameAs(sourceMaterial);
        assertThat(updated.getProcessRoute()).isEqualTo(sourceRoute).isNotSameAs(sourceRoute);
        assertThat(updated.getFeeData()).isEqualTo(sourceFee).isNotSameAs(sourceFee);

        @SuppressWarnings("unchecked")
        Map<String, Object> copiedMaterial = (Map<String, Object>) updated.getMaterialData();
        @SuppressWarnings("unchecked")
        List<String> copiedTags = (List<String>) copiedMaterial.get("tags");
        copiedTags.add("target-only");
        @SuppressWarnings("unchecked")
        List<String> sourceTags = (List<String>) sourceMaterial.get("tags");
        assertThat(sourceTags).containsExactly("source-tag");
    }

    @Test
    void applyCopiesConfirmedMaterialNoToSalesOrderAndReportsMaterialNoCompletion() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialNo(null);
        item.setMaterialNoStatus("未确认");
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL");
        source.setMaterialNo("MES-HISTORY-001");
        source.setMaterialNoStatus("已确认");
        source.setMaterialNoSource("MES_TICKET");
        source.setMaterialNoSourceTicketId(3001L);
        source.setMaterialNoMessage("历史 MES 确认");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.apply(applyReq(List.of(item.getId())));

        assertThat(resp.getMaterialNoCopiedCount()).isEqualTo(1);
        assertThat(resp.getMaterialNoSkippedCount()).isZero();
        assertThat(resp.getMaterialNoConflictCount()).isZero();
        assertThat(resp.getMessage()).contains("物料号补全 1 个", "物料号跳过 0 个", "物料号冲突 0 个");
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getMaterialNoReuseStatus()).isEqualTo("COPIED");
                    assertThat(row.getMaterialNoReuseMessage()).isEqualTo("已补全 MES 已确认物料号");
                });
        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialNo()).isEqualTo("MES-HISTORY-001");
        assertThat(updated.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(updated.getMaterialNoSource()).isEqualTo("MES_TICKET");
        assertThat(updated.getMaterialNoSourceTicketId()).isEqualTo(3001L);
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-历史材料");
    }

    @Test
    void applySkipsUnconfirmedSourceMaterialNoAndKeepsSnapshotCompletion() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialNo(null);
        item.setMaterialData(Map.of());
        item.setProcessRoute(List.of());
        item.setFeeData(List.of());
        item.setMaterialNoStatus("未确认");
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL");
        source.setMaterialNo("MES-UNCONFIRMED-001");
        source.setMaterialNoStatus("未确认");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.apply(applyReq(List.of(item.getId())));

        assertThat(resp.getMaterialNoCopiedCount()).isZero();
        assertThat(resp.getMaterialNoSkippedCount()).isEqualTo(1);
        assertThat(resp.getMaterialNoConflictCount()).isZero();
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getMaterialNoReuseStatus()).isEqualTo("NOT_APPLICABLE"));
        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialNo()).isNull();
        assertThat(updated.getMaterialNoStatus()).isEqualTo("未确认");
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-历史材料");
        assertThat(updated.getMaterialData()).isEqualTo(source.getMaterialData());
        assertThat(updated.getProcessRoute()).isEqualTo(source.getProcessRoute());
        assertThat(updated.getFeeData()).isEqualTo(source.getFeeData());
    }

    @Test
    void previewShowsAllPricesAsProtectedAndApplyDoesNotBackfillBlankPrices() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setTaxIncludedUnitPrice(null);
        item.setTaxIncludedTotalPrice(null);
        item.setUntaxedUnitPrice(null);
        item.setUntaxedTotalPrice(null);
        fixture.withLockedItems(header, List.of(item));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL", "NORMAL");
        source.setTaxIncludedUnitPrice(999.0);
        source.setTaxIncludedTotalPrice(9990.0);
        source.setUntaxedUnitPrice(900.0);
        source.setUntaxedTotalPrice(9000.0);
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchPreviewReq previewReq = new SalesOrderMatchPreviewReq();
        previewReq.setSalesOrderIds(List.of(header.getId()));
        SalesOrderMatchPreviewResp preview = fixture.service.preview(previewReq);

        assertThat(preview.getRows()).singleElement().satisfies(row -> {
            assertBlankProtectedPrice(row.getProtectedFields(), "taxIncludedUnitPrice");
            assertBlankProtectedPrice(row.getProtectedFields(), "taxIncludedTotalPrice");
            assertBlankProtectedPrice(row.getProtectedFields(), "untaxedUnitPrice");
            assertBlankProtectedPrice(row.getProtectedFields(), "untaxedTotalPrice");
        });

        fixture.service.apply(applyReq(List.of(item.getId())));

        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getTaxIncludedUnitPrice()).isNull();
        assertThat(updated.getTaxIncludedTotalPrice()).isNull();
        assertThat(updated.getUntaxedUnitPrice()).isNull();
        assertThat(updated.getUntaxedTotalPrice()).isNull();
    }

    @Test
    void applyReportsMaterialNoConflictWithoutBlockingOtherSnapshotFields() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setMaterialNo("MES-CURRENT-001");
        item.setMaterialNoStatus("已确认");
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL");
        source.setMaterialNo("MES-HISTORY-001");
        source.setMaterialNoStatus("已确认");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyResp resp = fixture.service.apply(applyReq(List.of(item.getId())));

        assertThat(resp.getMaterialNoCopiedCount()).isZero();
        assertThat(resp.getMaterialNoSkippedCount()).isZero();
        assertThat(resp.getMaterialNoConflictCount()).isEqualTo(1);
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getMaterialNoReuseStatus()).isEqualTo("CONFLICT");
                    assertThat(row.getMaterialNoReuseMessage()).contains("已有不同物料号");
                });
        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialNo()).isEqualTo("MES-CURRENT-001");
        assertThat(updated.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-历史材料");
        assertThat(updated.getMatchStatus()).isEqualTo("已匹配");
    }

    @Test
    void applyOrderRejectsNonQuotedSalesOrdersAndLegacyPayloadCannotBypassStatusGuard() {
        Fixture fixture = fixture();
        SalesOrderHeader partial = header(101L, "SOQPO-20260731-001");
        partial.setStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        SalesOrderItem item = salesOrderItem(501L, partial.getId(), "P-001", "A");
        fixture.withLockedItems(partial, List.of(item));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        assertThatThrownBy(() -> fixture.service.applyOrder(partial.getId()))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);

        SalesOrderMatchApplyReq orderReq = new SalesOrderMatchApplyReq();
        orderReq.setSalesOrderIds(List.of(partial.getId()));
        assertThatThrownBy(() -> fixture.service.apply(orderReq))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);

        assertThatThrownBy(() -> fixture.service.apply(applyReq(List.of(item.getId()))))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);
        verify(fixture.sourceMapper, never()).selectLatestReleasedRepeatSources(isNull(), any());
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
        verify(fixture.headerMapper, never()).updateById(any());
    }

    @Test
    void applyDoesNotWriteProtectedSalesOrderFactsEvenWhenBlankAndOverwriteRequested() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyReq req = applyReq(List.of(item.getId()));
        req.setOverwriteTemplateMaterial(true);
        req.setOverwriteExistingProductionSnapshot(true);
        fixture.service.apply(req);

        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getMaterialName()).isNull();
        assertThat(updated.getSurfaceTreatment()).isNull();
        assertThat(updated.getDrawingUrl()).isNull();
        assertThat(updated.getHasDrawing()).isNull();
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("ENG-HISTORY-MATERIAL");
    }

    @Test
    void applyFillsOnlyEmptyProductionSnapshotsAndIgnoresOverwriteSnapshotFlag() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        item.setEngineeringMaterialName(null);
        item.setMaterialShape(null);
        item.setMaterialData(Map.of("name", "EXISTING-REAL", "cost", 10));
        item.setProcessRoute(List.of(Map.of("process", "OLD")));
        item.setFeeData(List.of(Map.of("fee", "OLD")));
        item.setQuoteMode("EXISTING-MODE");
        item.setItemType("EXISTING-TYPE");
        item.setAssemblyFee(new BigDecimal("3.00"));
        item.setBaseCost(new BigDecimal("4.00"));
        fixture.withLockedItems(header, List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL", "NORMAL");
        source.setEngineeringMaterialName(null);
        source.setMaterialData(Map.of("name", "SOURCE-REAL", "shape", "SOURCE-SHAPE", "cost", 50));
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchApplyReq req = applyReq(List.of(item.getId()));
        req.setOverwriteExistingProductionSnapshot(true);
        fixture.service.apply(req);

        SalesOrderItem updated = fixture.lastItemUpdate();
        assertThat(updated.getEngineeringMaterialName()).isEqualTo("SOURCE-REAL");
        assertThat(updated.getMaterialShape()).isEqualTo("SOURCE-SHAPE");
        assertThat(updated.getMaterialData()).isEqualTo(Map.of("name", "EXISTING-REAL", "cost", 10));
        assertThat(updated.getProcessRoute()).isEqualTo(List.of(Map.of("process", "OLD")));
        assertThat(updated.getFeeData()).isEqualTo(List.of(Map.of("fee", "OLD")));
        assertThat(updated.getQuoteMode()).isEqualTo("EXISTING-MODE");
        assertThat(updated.getItemType()).isEqualTo("EXISTING-TYPE");
        assertThat(updated.getAssemblyFee()).isEqualByComparingTo("3.00");
        assertThat(updated.getBaseCost()).isEqualByComparingTo("4.00");
    }

    @Test
    void applyMarksUnmatchedRowsWithoutBlockingMatchedRows() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem matched = salesOrderItem(501L, header.getId(), "P-001", "A");
        SalesOrderItem unmatched = salesOrderItem(502L, header.getId(), "P-404", "A");
        fixture.withLockedItems(header, List.of(matched, unmatched));
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any()))
                .thenReturn(List.of(releasedSource(901, 21, "P-001", "A", "历史材料", "NORMAL")));

        SalesOrderMatchApplyResp resp = fixture.service.apply(applyReq(List.of(matched.getId(), unmatched.getId())));

        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getUnmatchedItemCount()).isEqualTo(1);
        assertThat(fixture.itemUpdates()).anySatisfy(item -> {
            assertThat(item.getId()).isEqualTo(unmatched.getId());
            assertThat(item.getMatchStatus()).isEqualTo("未匹配");
            assertThat(item.getMatchMessage()).contains("未命中历史已提单生产快照");
        });
        SalesOrderHeader summary = fixture.lastHeaderUpdate();
        assertThat(summary.getMatchedItemCount()).isEqualTo(1);
        assertThat(summary.getUnmatchedItemCount()).isEqualTo(1);
        assertThat(summary.getWarningMessage()).contains("1");
    }

    @Test
    void completeAssemblyMatchRebuildsSalesOrderAssemblyRelationsOnly() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem parent = salesOrderItem(501L, header.getId(), "ASM-001", "A");
        SalesOrderItem child = salesOrderItem(502L, header.getId(), "CH-001", "A");
        fixture.withLockedItems(header, List.of(parent));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(child));
        ItemQuoteVersion parentSource = releasedSource(901, 21, "ASM-001", "A", "历史组合材料", "ASSEMBLY");
        ItemQuoteVersion childSource = releasedSource(902, 21, "CH-001", "A", "历史子件材料", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(parentSource));
        when(fixture.sourceMapper.selectBatchIds(any())).thenReturn(List.of(childSource));
        when(fixture.historyRelationMapper.selectList(any())).thenReturn(List.of(historyRelation(901, 902, 2.5, 7)));

        fixture.service.apply(applyReq(List.of(parent.getId())));

        SalesOrderItem updatedParent = fixture.lastItemUpdate();
        assertThat(updatedParent.getMatchStatus()).isEqualTo("已匹配");
        assertThat(updatedParent.getAssemblyRelationStatus()).isEqualTo("已指定父子关系");
        verify(fixture.salesOrderAssemblyRelationMapper).delete(any(QueryWrapper.class));
        ArgumentCaptor<SalesOrderItemAssemblyRelation> relationCaptor =
                ArgumentCaptor.forClass(SalesOrderItemAssemblyRelation.class);
        verify(fixture.salesOrderAssemblyRelationMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue()).satisfies(relation -> {
            assertThat(relation.getSalesOrderId()).isEqualTo(header.getId());
            assertThat(relation.getParentItemId()).isEqualTo(parent.getId());
            assertThat(relation.getChildItemId()).isEqualTo(child.getId());
            assertThat(relation.getQtyPerParent()).isEqualByComparingTo("2.5");
            assertThat(relation.getSortNo()).isEqualTo(7);
            assertThat(relation.getSourceParentItemId()).isEqualTo(901);
            assertThat(relation.getSourceChildItemId()).isEqualTo(902);
        });
        verify(fixture.historyRelationMapper, never()).insert(any());
        verify(fixture.sourceMapper, never()).insert(any());
    }

    @Test
    void applyReplacesImportedDefaultNormalQuoteModeWhenSourceIsAssemblyAndRebuildsRelations() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem parent = salesOrderItem(501L, header.getId(), "ASM-001", "A");
        parent.setQuoteMode("NORMAL");
        SalesOrderItem child = salesOrderItem(502L, header.getId(), "CH-001", "A");
        fixture.withLockedItems(header, List.of(parent));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(child));
        ItemQuoteVersion parentSource = releasedSource(901, 21, "ASM-001", "A", "HISTORY-ASM", "ASSEMBLY");
        ItemQuoteVersion childSource = releasedSource(902, 21, "CH-001", "A", "HISTORY-CHILD", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(parentSource));
        when(fixture.sourceMapper.selectBatchIds(any())).thenReturn(List.of(childSource));
        when(fixture.historyRelationMapper.selectList(any())).thenReturn(List.of(historyRelation(901, 902, 2.5, 7)));

        fixture.service.apply(applyReq(List.of(parent.getId())));

        SalesOrderItem updatedParent = fixture.lastItemUpdate();
        assertThat(updatedParent.getQuoteMode()).isEqualTo("ASSEMBLY");
        verify(fixture.salesOrderAssemblyRelationMapper).insert(any(SalesOrderItemAssemblyRelation.class));
    }

    @Test
    void incompleteAssemblyMatchDoesNotWritePartialSalesOrderRelations() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001");
        SalesOrderItem parent = salesOrderItem(501L, header.getId(), "ASM-001", "A");
        fixture.withLockedItems(header, List.of(parent));
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of());
        ItemQuoteVersion parentSource = releasedSource(901, 21, "ASM-001", "A", "历史组合材料", "ASSEMBLY");
        ItemQuoteVersion missingChildSource = releasedSource(902, 21, "CH-MISSING", "A", "历史子件材料", "NORMAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(parentSource));
        when(fixture.sourceMapper.selectBatchIds(any())).thenReturn(List.of(missingChildSource));
        when(fixture.historyRelationMapper.selectList(any())).thenReturn(List.of(historyRelation(901, 902, 1.0, 1)));

        fixture.service.apply(applyReq(List.of(parent.getId())));

        SalesOrderItem updatedParent = fixture.lastItemUpdate();
        assertThat(updatedParent.getMatchStatus()).isEqualTo("已匹配");
        assertThat(updatedParent.getAssemblyRelationStatus()).isEqualTo("未指定父子关系");
        verify(fixture.salesOrderAssemblyRelationMapper).delete(any(QueryWrapper.class));
        verify(fixture.salesOrderAssemblyRelationMapper, never()).insert(any());
    }

    @Test
    void auditActionIsMarketActionWithReadableLabel() {
        assertThat(AuditService.inferModule("SALES_ORDER_MATCH_APPLY")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_MATCH_APPLY")).contains("异常治理").contains("补全");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        ReflectionTestUtils.setField(fixture.service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.service, "itemQuoteVersionMapper", fixture.sourceMapper);
        ReflectionTestUtils.setField(fixture.service, "historyAssemblyRelationMapper", fixture.historyRelationMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderAssemblyRelationMapper", fixture.salesOrderAssemblyRelationMapper);
        ReflectionTestUtils.setField(fixture.service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(fixture.service, "auditService", fixture.auditService);
        return fixture;
    }

    private SalesOrderMatchApplyReq applyReq(List<Long> itemIds) {
        SalesOrderMatchApplyReq req = new SalesOrderMatchApplyReq();
        req.setSalesOrderItemIds(itemIds);
        return req;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setCustomerName("客户A");
        header.setCustomerOrderNo("PO-" + id);
        header.setCustomerProjectNo("CP-" + id);
        header.setInternalProjectNo("IP-" + id);
        header.setDocumentTypeCode("SO01");
        header.setStatus(QuoteStatusWorkflow.QUOTED);
        return header;
    }

    private SalesOrderItem salesOrderItem(Long id, Long salesOrderId, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(id.intValue());
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("零件 " + partNo);
        item.setQty(10);
        item.setCustomerOrderNo("PO-" + salesOrderId);
        item.setCustomerProjectNo("CP-" + salesOrderId);
        item.setInternalProjectNo("IP-" + salesOrderId);
        item.setSalesMaterialNo("SALES-MAT-" + id);
        item.setMaterialNo("MES-MAT-" + id);
        item.setCustomerDeliveryDate(LocalDate.of(2026, 8, 1));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 5));
        item.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        item.setUntaxedTotalPrice(new BigDecimal("100.00"));
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setMatchStatus("待匹配");
        item.setAssemblyRelationStatus("不适用");
        return item;
    }

    private ItemQuoteVersion releasedSource(Integer id, Integer rfqId, String partNo, String version, String materialName, String quoteMode) {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(id);
        source.setRfqId(rfqId);
        source.setPartNo(partNo);
        source.setVersion(version);
        source.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        source.setMaterialName(materialName);
        source.setEngineeringMaterialName("ENG-" + materialName);
        source.setSurfaceTreatment("历史表处-" + materialName);
        source.setMaterialData(Map.of("name", materialName, "cost", 50));
        source.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("sortNo", 10),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("processName", "精细包装"),
                Map.entry("calcType", "工时"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("inputVal", 1.5),
                Map.entry("unitRate", 10.0),
                Map.entry("lineCost", 15.0))));
        source.setFeeData(List.of(Map.of("feeName", "profit", "ratePercent", 10)));
        source.setDrawingUrl("history/" + partNo + ".pdf");
        source.setMaterialGrossWeight(1.25);
        source.setPartNetWeight(0.80);
        source.setQuoteMode(quoteMode);
        source.setAssemblyFee(12.5);
        source.setBaseCost(99.75);
        source.setTaxIncludedUnitPrice(999.0);
        return source;
    }

    private ItemAssemblyRelation historyRelation(Integer parentItemId, Integer childItemId, double qtyPerParent, int sortNo) {
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setRfqId(21);
        relation.setParentItemId(parentItemId);
        relation.setChildItemId(childItemId);
        relation.setChildQtyPerParent(qtyPerParent);
        relation.setSortNo(sortNo);
        return relation;
    }

    @SuppressWarnings("unchecked")
    private void assertBlankProtectedPrice(Map<String, Object> fields, String key) {
        assertThat(fields).containsKey(key);
        assertThat((Map<String, Object>) fields.get(key))
                .containsEntry("current", "")
                .containsEntry("source", "");
    }

    private Map<String, Object> mutableMaterialSnapshot(String name) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("name", name);
        material.put("cost", 50);
        material.put("tags", new ArrayList<>(List.of("source-tag")));
        return material;
    }

    private List<Map<String, Object>> mutableRouteSnapshot(String processCode) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("routeType", "PROCESS");
        route.put("processCode", processCode);
        route.put("categoryName", "cutting");
        route.put("detailName", "laser");
        route.put("lineCost", 15.0);
        return new ArrayList<>(List.of(route));
    }

    private List<Map<String, Object>> mutableFeeSnapshot(String feeName) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", feeName);
        fee.put("ratePercent", 10);
        return new ArrayList<>(List.of(fee));
    }

    private static final class Fixture {
        private final SalesOrderMatchService service = new SalesOrderMatchService();
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        private final ItemQuoteVersionMapper sourceMapper = mock(ItemQuoteVersionMapper.class);
        private final ItemAssemblyRelationMapper historyRelationMapper = mock(ItemAssemblyRelationMapper.class);
        private final SalesOrderItemAssemblyRelationMapper salesOrderAssemblyRelationMapper =
                mock(SalesOrderItemAssemblyRelationMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final AuditService auditService = mock(AuditService.class);

        private void withLockedItems(SalesOrderHeader header, List<SalesOrderItem> items) {
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            when(headerMapper.selectById(header.getId())).thenReturn(header);
            for (SalesOrderItem item : items) {
                when(salesOrderItemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
            }
        }

        private SalesOrderItem lastItemUpdate() {
            List<SalesOrderItem> updates = itemUpdates();
            return updates.get(updates.size() - 1);
        }

        private List<SalesOrderItem> itemUpdates() {
            ArgumentCaptor<SalesOrderItem> captor = ArgumentCaptor.forClass(SalesOrderItem.class);
            verify(salesOrderItemMapper, atLeastOnce()).updateById(captor.capture());
            return captor.getAllValues();
        }

        private SalesOrderHeader lastHeaderUpdate() {
            ArgumentCaptor<SalesOrderHeader> captor = ArgumentCaptor.forClass(SalesOrderHeader.class);
            verify(headerMapper, atLeastOnce()).updateById(captor.capture());
            return captor.getAllValues().get(captor.getAllValues().size() - 1);
        }
    }
}
