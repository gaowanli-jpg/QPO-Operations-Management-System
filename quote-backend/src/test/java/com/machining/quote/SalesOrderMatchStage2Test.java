package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderMatchItemRow;
import com.machining.quote.dto.SalesOrderMatchOrderRow;
import com.machining.quote.dto.SalesOrderMatchPreviewReq;
import com.machining.quote.dto.SalesOrderMatchPreviewResp;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderMatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderMatchStage2Test {
    @Test
    void salesMatchControllerExposesReadOnlyPreviewEndpointsWithIndependentPermissions() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/machining/quote/controller/SalesOrderMatchController.java"), StandardCharsets.UTF_8);

        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/market/sales-match\")",
                        "@GetMapping(\"/customers\")",
                        "@RequirePermission(\"market:sales-match:customer-select\")",
                        "@GetMapping(\"/orders\")",
                        "@RequirePermission(\"market:sales-match:order-select\")",
                        "@GetMapping(\"/orders/{salesOrderId}/items\")",
                        "@RequirePermission(\"market:sales-match:view\")",
                        "@GetMapping(\"/orders/{salesOrderId}/preview\")",
                        "@PostMapping(\"/orders/{salesOrderId}/apply\")",
                        "@PostMapping(\"/preview\")")
                .contains("@RequirePermission(\"market:sales-match:view\")")
                .doesNotContain("OrderRfqMapper")
                .doesNotContain("order_rfq");
    }

    @Test
    void orderListReturnsOnlySalesOrderRowsAndAppliesSalesOrderDataScope() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001", "客户A");
        header.setTotalItemCount(3);
        header.setMatchedItemCount(1);
        header.setUnmatchedItemCount(2);
        when(fixture.headerMapper.selectCount(any())).thenReturn(1L);
        when(fixture.headerMapper.selectList(any())).thenReturn(List.of(header));

        PageResult<SalesOrderMatchOrderRow> result = fixture.service.listOrders(
                "客户A", "SOQPO", QuoteStatusWorkflow.QUOTED, "未匹配", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSourceType()).isEqualTo("SALES_ORDER");
                    assertThat(row.getSalesOrderId()).isEqualTo(101L);
                    assertThat(row.getSalesOrderNo()).isEqualTo("SOQPO-20260731-001");
                    assertThat(row.getCustomerName()).isEqualTo("客户A");
                    assertThat(row.getTotalItemCount()).isEqualTo(3);
                    assertThat(row.getMatchedItemCount()).isEqualTo(1);
                    assertThat(row.getUnmatchedItemCount()).isEqualTo(2);
                });
        verify(fixture.dataScopeService).applySalesOrderScope(any());
    }

    @Test
    void orderListDefaultsToQuotedSalesOrdersAndRejectsReleasedStatusFilters() {
        Fixture fixture = fixture();

        PageResult<SalesOrderMatchOrderRow> rejected = fixture.service.listOrders(
                null, null, QuoteStatusWorkflow.PARTIALLY_RELEASED, null, 1, 20);

        assertThat(rejected.getTotal()).isZero();
        assertThat(rejected.getRecords()).isEmpty();
        verify(fixture.headerMapper, never()).selectCount(any());

        when(fixture.headerMapper.selectCount(any())).thenReturn(0L);
        PageResult<SalesOrderMatchOrderRow> result = fixture.service.listOrders(
                null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isZero();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SalesOrderHeader>> queryCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(fixture.headerMapper).selectCount(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("status")
                .doesNotContain(QuoteStatusWorkflow.PARTIALLY_RELEASED);
    }

    @Test
    void itemPageRequiresSalesOrderAccessAndDoesNotReturnProductionJsonSnapshots() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001", "客户A");
        SalesOrderItem item = salesOrderItem(501L, 101L, "P-001", "A");
        item.setMaterialData(Map.of("large", "MASSIVE_JSON"));
        item.setProcessRoute(List.of(Map.of("process", "CNC")));
        item.setFeeData(List.of(Map.of("fee", "profit")));
        when(fixture.headerMapper.selectById(101L)).thenReturn(header);
        when(fixture.salesOrderItemMapper.selectCount(any())).thenReturn(1L);
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        PageResult<SalesOrderMatchItemRow> result = fixture.service.listItems(101L, null, 1, 20);

        assertThat(result.getRecords()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSalesOrderItemId()).isEqualTo(501L);
                    assertThat(row.getPartNo()).isEqualTo("P-001");
                    assertThat(row.getVersion()).isEqualTo("A");
                    assertThat(row.getCurrentMatchStatus()).isEqualTo("待匹配");
                });
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json)
                .doesNotContain("materialData")
                .doesNotContain("processRoute")
                .doesNotContain("feeData")
                .doesNotContain("MASSIVE_JSON");
    }

    @Test
    void previewMatchesLatestReleasedRepeatSourceByPartNoAndVersionWithoutUpdatingSalesOrderItems() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001", "客户A");
        SalesOrderItem item = salesOrderItem(501L, 101L, "P-001", "A");
        item.setLineNo(7);
        item.setMaterialName("模板材料");
        item.setSurfaceTreatment("模板表处");
        when(fixture.headerMapper.selectById(101L)).thenReturn(header);
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion older = releasedSource(900, 20, "P-001", "A", "旧材料");
        ItemQuoteVersion newer = releasedSource(901, 21, "P-001", "A", "新材料");
        newer.setQuoteMode("NORMAL");
        newer.setMaterialData(Map.of("name", "REAL-MATERIAL", "shape", "PLATE"));
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(older, newer));

        SalesOrderMatchPreviewReq req = new SalesOrderMatchPreviewReq();
        req.setSalesOrderIds(List.of(101L));
        SalesOrderMatchPreviewResp resp = fixture.service.preview(req);

        assertThat(resp.getTotalItemCount()).isEqualTo(1);
        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getUnmatchedItemCount()).isZero();
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getMatched()).isTrue();
                    assertThat(row.getSalesOrderNo()).isEqualTo("SOQPO-20260731-001");
                    assertThat(row.getLineNo()).isEqualTo(7);
                    assertThat(row.getSourceRfqId()).isEqualTo(21);
                    assertThat(row.getSourceItemId()).isEqualTo(901);
                    assertThat(row.getMaterialName()).isEqualTo("新材料");
                    assertThat(row.getEngineeringMaterialName()).isEqualTo("ENG-新材料");
                    assertThat(row.getMaterialShape()).isEqualTo("PLATE");
                    assertThat(row.getQuoteMode()).isEqualTo("NORMAL");
                    assertThat(row.getSourceProcessRouteSummary()).contains("PACKAGING", "包装", "精细包装", "¥10", "¥15");
                    assertThat(row.getCurrentProcessRouteSummary()).isNull();
                    assertThat(row.getChangedFields()).containsKeys("engineeringMaterialName", "quoteMode");
                    assertThat(row.getProtectedFields()).containsKeys("materialName", "surfaceTreatment", "materialNo");
                });

        ArgumentCaptor<List<PartVersionKey>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.sourceMapper).selectLatestReleasedRepeatSources(isNull(), keysCaptor.capture());
        assertThat(keysCaptor.getValue()).singleElement()
                .satisfies(key -> {
                    assertThat(key.partNo()).isEqualTo("P-001");
                    assertThat(key.version()).isEqualTo("A");
                });
        verify(fixture.salesOrderItemMapper, never()).updateById(any());
        verify(fixture.headerMapper, never()).updateById(any());
        String json = new ObjectMapper().writeValueAsString(resp);
        assertThat(json)
                .doesNotContain("materialData")
                .doesNotContain("feeData")
                .doesNotContain("MASSIVE_JSON");
    }

    @Test
    void previewTreatsImportFactsAsProtectedEvenWhenBlank() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001", "CUSTOMER-A");
        SalesOrderItem item = salesOrderItem(501L, 101L, "P-001", "A");
        when(fixture.headerMapper.selectById(101L)).thenReturn(header);
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        ItemQuoteVersion source = releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL");
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(source));

        SalesOrderMatchPreviewReq req = new SalesOrderMatchPreviewReq();
        req.setSalesOrderIds(List.of(101L));
        SalesOrderMatchPreviewResp resp = fixture.service.preview(req);

        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getChangedFields()).doesNotContainKeys("materialName", "surfaceTreatment", "drawingUrl");
                    assertThat(row.getProtectedFields()).containsKeys("materialName", "surfaceTreatment", "drawingUrl");
                });
    }

    @Test
    void previewOrderWrapsSingleQuotedSalesOrder() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SOQPO-20260731-001", "CUSTOMER-A");
        SalesOrderItem item = salesOrderItem(501L, header.getId(), "P-001", "A");
        when(fixture.headerMapper.selectById(header.getId())).thenReturn(header);
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.sourceMapper.selectLatestReleasedRepeatSources(isNull(), any()))
                .thenReturn(List.of(releasedSource(901, 21, "P-001", "A", "HISTORY-MATERIAL")));

        SalesOrderMatchPreviewResp resp = fixture.service.previewOrder(header.getId());

        assertThat(resp.getTotalItemCount()).isEqualTo(1);
        assertThat(resp.getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getSalesOrderId()).isEqualTo(header.getId());
                    assertThat(row.getSalesOrderNo()).isEqualTo("SOQPO-20260731-001");
                });
    }

    @Test
    void previewOrderRejectsNonQuotedSalesOrdersAndLegacyPayloadCannotBypassStatusGuard() {
        Fixture fixture = fixture();
        SalesOrderHeader partial = header(101L, "SOQPO-20260731-001", "CUSTOMER-A");
        partial.setStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        SalesOrderItem item = salesOrderItem(501L, partial.getId(), "P-001", "A");
        when(fixture.headerMapper.selectById(partial.getId())).thenReturn(partial);
        when(fixture.salesOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        assertThatThrownBy(() -> fixture.service.previewOrder(partial.getId()))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);

        SalesOrderMatchPreviewReq orderReq = new SalesOrderMatchPreviewReq();
        orderReq.setSalesOrderIds(List.of(partial.getId()));
        assertThatThrownBy(() -> fixture.service.preview(orderReq))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);

        SalesOrderMatchPreviewReq itemReq = new SalesOrderMatchPreviewReq();
        itemReq.setSalesOrderItemIds(List.of(item.getId()));
        assertThatThrownBy(() -> fixture.service.preview(itemReq))
                .hasMessageContaining(QuoteStatusWorkflow.QUOTED);
        verify(fixture.sourceMapper, never()).selectLatestReleasedRepeatSources(isNull(), any());
    }

    @Test
    void customerOptionsDelegateToDataScopedCustomerSelection() {
        Fixture fixture = fixture();
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerName("客户A");
        when(fixture.dataScopeService.listCustomerOptions("name", "客")).thenReturn(List.of(option));

        assertThat(fixture.service.listCustomerOptions("name", "客")).containsExactly(option);
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        ReflectionTestUtils.setField(fixture.service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderItemMapper", fixture.salesOrderItemMapper);
        ReflectionTestUtils.setField(fixture.service, "itemQuoteVersionMapper", fixture.sourceMapper);
        ReflectionTestUtils.setField(fixture.service, "dataScopeService", fixture.dataScopeService);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String customerName) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setCustomerName(customerName);
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
        item.setLineNo(1);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("零件 " + partNo);
        item.setQty(10);
        item.setCustomerOrderNo("PO-" + salesOrderId);
        item.setInternalProjectNo("IP-" + salesOrderId);
        item.setMaterialNo("MES-MAT-" + id);
        item.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setMatchStatus("待匹配");
        item.setAssemblyRelationStatus("不适用");
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
        source.setSurfaceTreatment("历史表处-" + materialName);
        source.setMaterialData(Map.of("large", "MASSIVE_JSON"));
        source.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("processName", "精细包装"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("lineCost", 15.0),
                Map.entry("payload", "MASSIVE_JSON"))));
        source.setFeeData(List.of(Map.of("fee", "profit", "payload", "MASSIVE_JSON")));
        source.setDrawingUrl("history/" + partNo + ".pdf");
        source.setMaterialGrossWeight(1.25);
        source.setPartNetWeight(0.80);
        source.setQuoteMode("ASSEMBLY");
        source.setAssemblyFee(12.5);
        source.setBaseCost(99.75);
        source.setTaxIncludedUnitPrice(999.0);
        return source;
    }

    private static final class Fixture {
        private final SalesOrderMatchService service = new SalesOrderMatchService();
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        private final ItemQuoteVersionMapper sourceMapper = mock(ItemQuoteVersionMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
    }
}
