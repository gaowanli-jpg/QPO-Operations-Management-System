package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderReleaseSourceStage5Test {
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
    void releaseOrderSelectionReturnsSourceAwareRfqAndSalesOrderRows() throws Exception {
        String marketController = source("controller", "MarketController.java");
        String orderService = source("service", "OrderManagementService.java");

        assertThat(marketController)
                .contains("@GetMapping(\"/release/orders\")")
                .contains("@RequirePermission(\"market:release:order-select\")")
                .contains("orderManagementService.listReleaseOrderSources");
        assertThat(orderService)
                .contains("public PageResult<Map<String, Object>> listReleaseOrderSources")
                .contains("\"sourceType\", \"RFQ\"")
                .contains("\"sourceType\", \"SALES_ORDER\"")
                .contains("\"sourceOrderId\"")
                .contains("salesOrderHeaderMapper");
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
