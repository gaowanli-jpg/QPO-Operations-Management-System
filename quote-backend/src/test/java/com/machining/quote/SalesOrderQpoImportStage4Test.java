package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.service.SalesOrderRepeatEnrichmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage4Test {
    @Test
    void enrichmentCopiesProductionSnapshotWithoutOverwritingImportedPrices() {
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderRepeatEnrichmentService service = new SalesOrderRepeatEnrichmentService();
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);

        ItemQuoteVersion matched = currentItem(1, "P-001", "A", 11.30, 10.00);
        ItemQuoteVersion unmatched = currentItem(2, "P-002", "B", 20.00, 18.00);
        ItemQuoteVersion source = releasedSource("P-001", "A");
        when(itemMapper.selectLatestReleasedRepeatSources(eq(100), any())).thenReturn(List.of(source));

        com.machining.quote.dto.SalesOrderRepeatEnrichmentResult result = service.enrich(100, List.of(matched, unmatched));

        assertThat(result.getMatchedItemCount()).isEqualTo(1);
        assertThat(result.getUnmatchedItemCount()).isEqualTo(1);
        assertThat(result.getWarningMessage()).contains("1");

        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        ItemQuoteVersion enriched = itemCaptor.getValue();
        assertThat(enriched.getId()).isEqualTo(1);
        assertThat(enriched.getMaterialData()).isEqualTo(source.getMaterialData());
        assertThat(enriched.getProcessRoute()).isEqualTo(source.getProcessRoute());
        assertThat(enriched.getFeeData()).isEqualTo(source.getFeeData());
        assertThat(enriched.getDrawingUrl()).isEqualTo("drawings/P-001.pdf");
        assertThat(enriched.getMaterialName()).isEqualTo("6061-T6");
        assertThat(enriched.getEngineeringMaterialName()).isEqualTo("6061-T6");
        assertThat(enriched.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(enriched.getMaterialGrossWeight()).isEqualTo(1.25);
        assertThat(enriched.getPartNetWeight()).isEqualTo(0.80);
        assertThat(enriched.getQuoteMode()).isEqualTo("NORMAL");
        assertThat(enriched.getFinalUnitPrice()).isCloseTo(11.30, within(0.000001));
        assertThat(enriched.getUntaxedUnitPrice()).isCloseTo(10.00, within(0.000001));
        assertThat(enriched.getTaxIncludedUnitPrice()).isCloseTo(11.30, within(0.000001));
        assertThat(enriched.getFinalTotalPrice()).isCloseTo(113.00, within(0.000001));
        assertThat(enriched.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    private ItemQuoteVersion currentItem(Integer id, String partNo, String version, double taxPrice, double untaxedPrice) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(100);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(10);
        item.setMaterialName("imported-material");
        item.setSurfaceTreatment("imported-surface");
        item.setFinalUnitPrice(taxPrice);
        item.setFinalTotalPrice(taxPrice * 10);
        item.setTaxIncludedUnitPrice(taxPrice);
        item.setTaxIncludedTotalPrice(taxPrice * 10);
        item.setUntaxedUnitPrice(untaxedPrice);
        item.setUntaxedTotalPrice(untaxedPrice * 10);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private ItemQuoteVersion releasedSource(String partNo, String version) {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(900);
        source.setRfqId(50);
        source.setPartNo(partNo);
        source.setVersion(version);
        source.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        source.setMaterialName("6061-T6");
        source.setEngineeringMaterialName("6061-T6");
        source.setSurfaceTreatment("Anodized");
        source.setMaterialData(Map.of("name", "6061-T6", "cost", 50));
        source.setProcessRoute(List.of(Map.of("sortNo", 10, "processName", "CNC", "inputVal", 1.5)));
        source.setFeeData(List.of(Map.of("feeName", "profit", "ratePercent", 10)));
        source.setDrawingUrl("drawings/P-001.pdf");
        source.setMaterialGrossWeight(1.25);
        source.setPartNetWeight(0.80);
        source.setQuoteMode("NORMAL");
        source.setAssemblyFee(0.0);
        source.setBaseCost(99.0);
        source.setFinalUnitPrice(999.0);
        source.setFinalTotalPrice(9990.0);
        source.setTaxIncludedUnitPrice(999.0);
        source.setUntaxedUnitPrice(888.0);
        source.setTaxIncludedTotalPrice(9990.0);
        source.setUntaxedTotalPrice(8880.0);
        return source;
    }
}
