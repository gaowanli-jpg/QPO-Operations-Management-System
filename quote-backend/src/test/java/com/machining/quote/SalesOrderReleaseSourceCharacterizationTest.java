package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.OrderManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderReleaseSourceCharacterizationTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void releaseSourceContractsExposeRfqAndSalesOrderIdentity() throws Exception {
        String sourceType = source("dto", "ReleaseSourceType.java");
        String orderSource = source("dto", "ReleaseOrderSource.java");
        String batchReq = source("dto", "ReleaseItemBatchQueryReq.java");

        assertThat(sourceType)
                .contains("RFQ")
                .contains("SALES_ORDER");
        assertThat(orderSource)
                .contains("private ReleaseSourceType sourceType;")
                .contains("private Long sourceOrderId;");
        assertThat(batchReq)
                .contains("private List<ReleaseOrderSource> sources;")
                .contains("private List<Integer> rfqIds;");
    }

    @Test
    void releaseOrderSelectionReturnsOnlyRfqRows() throws Exception {
        String marketController = source("controller", "MarketController.java");
        String orderService = source("service", "OrderManagementService.java");

        assertThat(marketController)
                .contains("@GetMapping(\"/release/orders\")")
                .contains("@RequirePermission(\"market:release:order-select\")")
                .contains("orderManagementService.listReleaseOrderSources");
        assertThat(orderService)
                .contains("public PageResult<Map<String, Object>> listReleaseOrderSources")
                .contains("listReleaseCandidateOrders(customerCode, customerName, keyword, status, safePage, safePageSize)")
                .contains("\"sourceType\", \"RFQ\"")
                .contains("\"sourceOrderId\"")
                .doesNotContain("salesOrderHeaderMapper")
                .doesNotContain("listReleaseSalesOrderCandidates")
                .doesNotContain("buildReleaseSalesOrderSourceRow");
    }

    @Test
    void releaseOrderSelectionUsesOnlyRfqMapperForUiCandidates() {
        OrderManagementService service = new OrderManagementService();
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

        OrderRfq rfq = new OrderRfq();
        rfq.setId(1001);
        rfq.setRfqNo("RFQ-1001");
        rfq.setCustomerName("ACME");
        rfq.setStatus(QuoteStatusWorkflow.QUOTED);

        when(orderMapper.selectCount(any())).thenReturn(1L);
        when(orderMapper.selectList(any())).thenReturn(List.of(rfq));

        PageResult<Map<String, Object>> page = service.listReleaseOrderSources(null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0))
                .containsEntry("sourceType", "RFQ")
                .containsEntry("sourceOrderNo", "RFQ-1001");
        assertThat(OrderManagementService.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("salesOrderHeaderMapper");
    }

    @Test
    void mixedReleaseItemPagingUsesBackendUnionPagingAndSalesOrderRows() throws Exception {
        String releaseService = source("service", "InternalTargetLeadTimeService.java");

        assertThat(releaseService)
                .contains("public PageResult<Map<String, Object>> pageItemRowsBatch(ReleaseItemBatchQueryReq req)")
                .contains("normalizeReleaseSources(req)")
                .contains("pageMixedSourceItemRows")
                .contains("UNION ALL")
                .contains("sales_order_item")
                .contains("sales_order_header")
                .contains("LIMIT \" + pageSize + \" OFFSET \" + offset")
                .contains("row.put(\"sourceType\", \"SALES_ORDER\")")
                .contains("row.put(\"sourceOrderId\", item.getSalesOrderId())");
    }

    @Test
    void batchPartSelectionAcceptsSourceObjectsForRfqAndSalesOrderMatching() throws Exception {
        String marketController = source("controller", "MarketController.java");
        String releaseService = source("service", "InternalTargetLeadTimeService.java");

        assertThat(marketController)
                .contains("@RequestParam(value = \"sources\", required = false) String sourcesJson")
                .contains("parseReleaseSources(sourcesJson, rfqIdsJson)");
        assertThat(releaseService)
                .contains("public Map<String, Object> matchPartSelectionBatch(List<ReleaseOrderSource> sources")
                .contains("matchSalesOrderPartSelection")
                .contains("sourceType")
                .contains("sourceOrderId");
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }
}
