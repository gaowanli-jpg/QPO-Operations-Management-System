package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderRepeatEnrichmentResult;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.RepeatQuoteSourceSqlProvider;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.SalesOrderRepeatEnrichmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage4Test {
    @Test
    void enrichmentCopiesLatestReleasedProductionSnapshotIntoSalesOrderItemsWithoutOverwritingSalesFields() {
        ItemQuoteVersionMapper sourceMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderRepeatEnrichmentService service = new SalesOrderRepeatEnrichmentService();
        ReflectionTestUtils.setField(service, "itemMapper", sourceMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);

        SalesOrderItem matched = currentSalesOrderItem(501L, "P-001", "A");
        SalesOrderItem unmatched = currentSalesOrderItem(502L, "P-002", "B");
        ItemQuoteVersion newerSource = releasedSource(900, 50, "P-001", "A", "new-material");
        ItemQuoteVersion olderSource = releasedSource(800, 40, "P-001", "A", "old-material");
        when(sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(newerSource, olderSource));

        SalesOrderRepeatEnrichmentResult result = service.enrich(List.of(matched, unmatched));

        assertThat(result.getMatchedItemCount()).isEqualTo(1);
        assertThat(result.getUnmatchedItemCount()).isEqualTo(1);
        assertThat(result.getWarningMessage()).contains("1");

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(salesOrderItemMapper, times(2)).updateById(itemCaptor.capture());
        SalesOrderItem enriched = itemCaptor.getAllValues().stream()
                .filter(item -> Long.valueOf(501L).equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(enriched.getMaterialData()).isEqualTo(newerSource.getMaterialData());
        assertThat(enriched.getProcessRoute()).isEqualTo(newerSource.getProcessRoute());
        assertThat(enriched.getFeeData()).isEqualTo(newerSource.getFeeData());
        assertThat(enriched.getDrawingUrl()).isEqualTo("drawings/P-001.pdf");
        assertThat(enriched.getHasDrawing()).isTrue();
        assertThat(enriched.getMaterialName()).isEqualTo("new-material");
        assertThat(enriched.getEngineeringMaterialName()).isEqualTo("ENG-new-material");
        assertThat(enriched.getSurfaceTreatment()).isEqualTo("Anodized-new-material");
        assertThat(enriched.getMaterialGrossWeight()).isEqualTo(1.25);
        assertThat(enriched.getPartNetWeight()).isEqualTo(0.80);
        assertThat(enriched.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(enriched.getAssemblyFee()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(enriched.getBaseCost()).isEqualByComparingTo(new BigDecimal("99.75"));
        assertThat(enriched.getItemType()).isEqualTo("组合件");
        assertThat(enriched.getMatchedRfqId()).isEqualTo(50);
        assertThat(enriched.getMatchedItemId()).isEqualTo(900);
        assertThat(enriched.getMatchStatus()).isEqualTo("已匹配");
        assertThat(enriched.getMatchMessage()).contains("50").contains("900");

        assertThat(enriched.getQty()).isEqualTo(10);
        assertThat(enriched.getTaxIncludedUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
        assertThat(enriched.getUntaxedUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(enriched.getTaxIncludedTotalPrice()).isEqualByComparingTo(new BigDecimal("113.00"));
        assertThat(enriched.getUntaxedTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(enriched.getFinalUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
        assertThat(enriched.getFinalTotalPrice()).isEqualByComparingTo(new BigDecimal("113.00"));
        assertThat(enriched.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(enriched.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(enriched.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(enriched.getSalesMaterialNo()).isEqualTo("MAT-001");
        assertThat(enriched.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(enriched.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(enriched.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 5));

        SalesOrderItem updatedUnmatched = itemCaptor.getAllValues().stream()
                .filter(item -> Long.valueOf(502L).equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(updatedUnmatched.getMatchStatus()).isEqualTo("未匹配");
        assertThat(updatedUnmatched.getMatchMessage()).contains("未补齐历史生产快照");
        assertThat(updatedUnmatched.getTaxIncludedUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
    }

    @Test
    void repeatSourceSqlUsesOnlyReleasedRfqItemQuoteVersionsAndAllowsSalesOrderLookupWithoutCurrentRfq() {
        RepeatQuoteSourceSqlProvider provider = new RepeatQuoteSourceSqlProvider();

        Map<String, Object> salesOrderLookupParams = new HashMap<>();
        salesOrderLookupParams.put("currentRfqId", null);
        salesOrderLookupParams.put("keys", List.of(new PartVersionKey("P-001", "A")));
        String salesOrderLookupSql = provider.selectLatestReleasedRepeatSources(salesOrderLookupParams);
        assertThat(salesOrderLookupSql)
                .contains("FROM item_quote_version h")
                .contains("h.status = '\u5df2\u63d0\u5355'")
                .contains("ORDER BY h.id DESC")
                .contains("LIMIT 1")
                .doesNotContain("sales_order_item")
                .doesNotContain("h.rfq_id <> #{currentRfqId}");

        String rfqLookupSql = provider.selectLatestReleasedRepeatSources(Map.of(
                "currentRfqId", 100,
                "keys", List.of(new PartVersionKey("P-001", "A"))
        ));
        assertThat(rfqLookupSql).contains("h.rfq_id <> #{currentRfqId}");
    }

    private SalesOrderItem currentSalesOrderItem(Long id, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(301L);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(10);
        item.setCustomerOrderNo("PO-001");
        item.setCustomerProjectNo("CP-001");
        item.setInternalProjectNo("IP-001");
        item.setSalesMaterialNo("MAT-001");
        item.setMaterialNo("MAT-001");
        item.setCustomerDeliveryDate(LocalDate.of(2026, 8, 1));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 5));
        item.setMaterialName("imported-material");
        item.setSurfaceTreatment("imported-surface");
        item.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        item.setUntaxedTotalPrice(new BigDecimal("100.00"));
        item.setFinalUnitPrice(new BigDecimal("11.30"));
        item.setFinalTotalPrice(new BigDecimal("113.00"));
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private ItemQuoteVersion releasedSource(Integer id, Integer rfqId, String partNo, String version, String materialName) {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(id);
        source.setRfqId(rfqId);
        source.setPartNo(partNo);
        source.setVersion(version);
        source.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        source.setMaterialName(materialName);
        source.setEngineeringMaterialName("ENG-" + materialName);
        source.setSurfaceTreatment("Anodized-" + materialName);
        source.setMaterialData(Map.of("name", materialName, "cost", 50));
        source.setProcessRoute(List.of(Map.of("sortNo", 10, "processName", "CNC", "inputVal", 1.5)));
        source.setFeeData(List.of(Map.of("feeName", "profit", "ratePercent", 10)));
        source.setDrawingUrl("drawings/P-001.pdf");
        source.setMaterialGrossWeight(1.25);
        source.setPartNetWeight(0.80);
        source.setQuoteMode("ASSEMBLY");
        source.setAssemblyFee(12.5);
        source.setBaseCost(99.75);
        source.setFinalUnitPrice(999.0);
        source.setFinalTotalPrice(9990.0);
        source.setTaxIncludedUnitPrice(999.0);
        source.setUntaxedUnitPrice(888.0);
        source.setTaxIncludedTotalPrice(9990.0);
        source.setUntaxedTotalPrice(8880.0);
        return source;
    }
}
