package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderRepeatEnrichmentResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderRepeatEnrichmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage4Test {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void enrichmentCopiesProductionSnapshotWithoutOverwritingImportedPrices() {
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderRepeatEnrichmentService service = new SalesOrderRepeatEnrichmentService();
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);

        ItemQuoteVersion matched = currentItem(1, "P-001", "A", 11.30, 10.00);
        ItemQuoteVersion unmatched = currentItem(2, "P-002", "B", 20.00, 18.00);
        ItemQuoteVersion source = releasedSource("P-001", "A");
        when(itemMapper.selectLatestReleasedRepeatSources(eq(100), any())).thenReturn(List.of(source));

        SalesOrderRepeatEnrichmentResult result = service.enrich(100, List.of(matched, unmatched));

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

    @Test
    void importServicePersistsEnrichmentResultInRecordAndResponse() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        RfqNumberService rfqNumberService = mock(RfqNumberService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderRepeatEnrichmentService enrichmentService = mock(SalesOrderRepeatEnrichmentService.class);
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "excelParser", parser);
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "rfqNumberService", rfqNumberService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "repeatEnrichmentService", enrichmentService);
        AuthContext.set(new AuthContext.SessionUser(5, "market01", "Market One", "MARKET_USER"));
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(rfqNumberService.nextRfqNo()).thenReturn("RFQ-SO-0001");
        when(enrichmentService.enrich(eq(101), any())).thenReturn(enrichmentResult(1, 1));
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            OrderRfq order = invocation.getArgument(0);
            order.setId(101);
            return 1;
        }).when(orderMapper).insert(any(OrderRfq.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(301L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            ItemQuoteVersion item = invocation.getArgument(0);
            item.setId(item.getPartNo().equals("P-001") ? 401 : 402);
            return 1;
        }).when(itemMapper).insert(any(ItemQuoteVersion.class));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile("excelFile", "sales-order.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                7L,
                "SO01"
        );

        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getUnmatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getWarningMessage()).contains("1");

        verify(enrichmentService).enrich(eq(101), org.mockito.ArgumentMatchers.argThat(items ->
                items.size() == 2 && items.stream().allMatch(item -> item.getId() != null)));
        verify(recordMapper).updateById(org.mockito.ArgumentMatchers.argThat(record ->
                record.getId().equals(301L)
                        && record.getMatchedItemCount().equals(1)
                        && record.getUnmatchedItemCount().equals(1)
                        && record.getWarningMessage().contains("1")));
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.argThat(summary -> summary.toString().contains("unmatchedItemCount=1")),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_IMPORT_QPO")),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    private SalesOrderRepeatEnrichmentResult enrichmentResult(int matched, int unmatched) {
        SalesOrderRepeatEnrichmentResult result = new SalesOrderRepeatEnrichmentResult();
        result.setMatchedItemCount(matched);
        result.setUnmatchedItemCount(unmatched);
        result.setWarningMessage(unmatched + " item unmatched");
        return result;
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

    private CustomerMaster customer() {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(7L);
        customer.setCustomerCode("C001");
        customer.setCustomerShortName("ACME");
        customer.setCustomerName("ACME Manufacturing");
        customer.setEnabled(true);
        return customer;
    }

    private SalesOrderQpoExcelParseResult parseResult() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setCustomerOrderNo("PO-001");
        group.setInternalProjectNo("IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("sample");
        group.setLines(List.of(line("P-001", "A", "MAT-001", 10), line("P-002", "B", "MAT-002", 4)));
        result.setOrders(List.of(group));
        result.setSourceRowCount(2);
        result.setOrderCount(1);
        result.setLineCount(2);
        return result;
    }

    private SalesOrderQpoExcelLineDTO line(String partNo, String version, String salesMaterialNo, int qty) {
        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(2);
        line.setSalesOrderLineNo(1);
        line.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        line.setCustomerOrderNo("PO-001");
        line.setCustomerProjectNo("CP-001");
        line.setCustomerOrderTitle("GSQ1");
        line.setOrderType("sample");
        line.setSalesMaterialNo(salesMaterialNo);
        line.setInternalProjectNo("IP-001");
        line.setCustomerDeliveryDate(LocalDate.of(2026, 8, 1));
        line.setInternalDeliveryDate(LocalDate.of(2026, 8, 5));
        line.setPartNo(partNo);
        line.setQty(qty);
        line.setShippedQty(0);
        line.setUnshippedQty(qty);
        line.setDescription("desc-" + partNo);
        line.setVersion(version);
        line.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        line.setUntaxedUnitPrice(new BigDecimal("10.00"));
        line.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        line.setUntaxedTotalPrice(new BigDecimal("100.00"));
        return line;
    }
}
