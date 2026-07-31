package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderIndependentStage8Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path TEST_JAVA = Path.of("src", "test", "java", "com", "machining", "quote");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO\u6743\u9650\u7528\u6237\u4f53\u7cfb", "02-\u6743\u9650\u70b9\u6e05\u5355\u4e0e\u73b0\u6709\u89d2\u8272\u6620\u5c04.md");
    private static final Path LOG_DOC = Path.of(
            "..", "docs", "QPO\u65e5\u5fd7\u6a21\u5757", "03-\u65e5\u5fd7\u8fd0\u7ef4\u8bf4\u660e.md");
    private static final Path DISCUSSION_DOC = Path.of(
            "..", "docs", "QPO\u62a5\u4ef7\u72b6\u6001\u6d41\u8f6c", "10-\u9500\u552e\u8ba2\u5355\u76f4\u63d0\u6d41\u7a0b\u65b9\u6848\u8ba8\u8bba\u6587\u6863.md");
    private static final Path LEGACY_DOC_13 = Path.of(
            "..", "docs", "QPO\u62a5\u4ef7\u72b6\u6001\u6d41\u8f6c", "13-\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8fdb\u5165QPO\u4e3b\u6d41\u7a0b\u5f00\u53d1\u6587\u6863.md");

    @Test
    void stage8DocsCloseIndependentSalesOrderCutover() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);
        String discussionDoc = read(DISCUSSION_DOC);
        String legacyDoc = read(LEGACY_DOC_13);

        assertThat(permissionDoc)
                .contains(
                        "`market:rfq:import-sales-order`",
                        "`market:u9-sync:view`",
                        "`market:u9-sync:submit`",
                        "sales_order_header",
                        "sales_order_item",
                        "\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u3001\u6a21\u677f\u4e0b\u8f7d\u4ecd\u6cbf\u7528 `market:rfq:import-sales-order`",
                        "\u540e\u7eed U9 \u6574\u5355\u540c\u6b65\u5165\u53e3\u6539\u4e3a\u72ec\u7acb `market:u9-sync:*`",
                        "\u540c\u6b65\u8bf7\u6c42\u4ece `sales_order_header` / `sales_order_item` \u7ec4\u88c5",
                        "`ItemCode` \u4f7f\u7528 `sales_order_item.material_no`",
                        "`material_no_status = \u5df2\u786e\u8ba4`",
                        "\u4e0d\u65b0\u589e\u53ef\u5206\u914d `common:*` \u6743\u9650"
                )
                .doesNotContain("`market:sales-order:view` |")
                .doesNotContain("ItemCode` \u4f7f\u7528 `order_delivery_ticket.material_no`");
        assertThat(logDoc)
                .contains(
                        "\u9500\u552e\u8ba2\u5355\u72ec\u7acb\u4e3b\u8868\u9636\u6bb5 8 \u56de\u5f52\u6536\u53e3",
                        "SALES_ORDER_IMPORT_QPO",
                        "SALES_ORDER_U9_SYNC",
                        "RFQ \u5bfc\u5165\u3001\u6838\u4ef7\u3001\u62a5\u4ef7\u3001\u8bae\u4ef7\u63d0\u5355\u3001\u63d0\u5355\u8868\u636e\u3001\u5df2\u63d0\u96f6\u4ef6\u3001\u9501\u5e93\u96f6\u4ef6",
                        "\u9500\u552e\u8ba2\u5355\u6570\u636e\u4e0d\u8fdb\u5165 `order_rfq` / `item_quote_version`",
                        "\u4e0d\u8bb0\u5f55 token\u3001\u5bc6\u94a5\u6216\u5b8c\u6574\u8bf7\u6c42\u4f53"
                );
        assertThat(discussionDoc)
                .contains(
                        "### 12.6 \u9636\u6bb5 8 \u6587\u6863\u4e0e\u56de\u5f52\u6536\u53e3\u7ed3\u8bba",
                        "\u65b0\u5b9e\u65bd\u4f9d\u636e\u4e3a `docs/QPO\u62a5\u4ef7\u72b6\u6001\u6d41\u8f6c/14-\u9500\u552e\u8ba2\u5355\u72ec\u7acb\u4e3b\u8868\u5f00\u53d1\u6587\u6863.md`",
                        "\u65e7 `13-\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8fdb\u5165QPO\u4e3b\u6d41\u7a0b\u5f00\u53d1\u6587\u6863.md` \u5df2\u5e9f\u5f03",
                        "\u9500\u552e\u8ba2\u5355\u6570\u636e\u4e0d\u8fdb\u5165 `order_rfq` / `item_quote_version`",
                        "RFQ \u5bfc\u5165\u3001\u6838\u4ef7\u3001\u62a5\u4ef7\u3001\u8bae\u4ef7\u63d0\u5355\u3001\u63d0\u5355\u8868\u636e\u3001\u5df2\u63d0\u96f6\u4ef6\u3001\u9501\u5e93\u96f6\u4ef6\u539f\u6d41\u7a0b\u4e0d\u56de\u9000"
                );
        assertThat(legacyDoc.lines().limit(8).reduce("", (left, right) -> left + "\n" + right))
                .contains(
                        "\u5df2\u5e9f\u5f03",
                        "\u4e0d\u518d\u4f5c\u4e3a\u5f00\u53d1\u6267\u884c\u6587\u6863",
                        "14-\u9500\u552e\u8ba2\u5355\u72ec\u7acb\u4e3b\u8868\u5f00\u53d1\u6587\u6863.md"
                );
    }

    @Test
    void salesOrderImportKeepsLegacyRfqTablesOutOfWritePathAndProtectsOldFlowTests() throws IOException {
        String importService = read(MAIN_JAVA.resolve(Path.of("service", "SalesOrderQpoImportService.java")));
        String enrichmentService = read(MAIN_JAVA.resolve(Path.of("service", "SalesOrderRepeatEnrichmentService.java")));
        String salesMatchService = read(MAIN_JAVA.resolve(Path.of("service", "SalesOrderMatchService.java")));
        String importResultRow = read(MAIN_JAVA.resolve(Path.of("dto", "SalesOrderQpoImportResultRow.java")));
        String orderService = read(MAIN_JAVA.resolve(Path.of("service", "OrderManagementService.java")));
        String releaseService = read(MAIN_JAVA.resolve(Path.of("service", "InternalTargetLeadTimeService.java")));
        String dataScopeService = read(MAIN_JAVA.resolve(Path.of("service", "DataScopeService.java")));
        String marketController = read(MAIN_JAVA.resolve(Path.of("controller", "MarketController.java")));
        String coreWorkflowTests = read(TEST_JAVA.resolve("CoreWorkflowIntegrationTests.java"));
        String auditActionTests = read(TEST_JAVA.resolve("AuditActionCoverageTests.java"));
        String releaseTests = read(TEST_JAVA.resolve("SalesOrderReleaseSourceStage5Test.java"));
        String stage6Tests = read(TEST_JAVA.resolve("SalesOrderDeliveryTicketStage6Test.java"));
        String deliveredTests = read(TEST_JAVA.resolve("DeliveredItemQueryBackendTest.java"));
        String lockedTests = read(TEST_JAVA.resolve("LockedItemQueryTest.java"));
        String deliveredMapper = read(MAIN_JAVA.resolve(Path.of("mapper", "OrderDeliveryTicketMapper.java")));
        String lockedMapper = read(MAIN_JAVA.resolve(Path.of("mapper", "OrderDeliveryTicketAllocationMapper.java")));

        assertThat(importService)
                .contains(
                        "SalesOrderHeaderMapper",
                        "SalesOrderItemMapper",
                        "headerMapper.insert(header)",
                        "itemMapper.insert(item)",
                        "IMPORT_MODE_QPO_INDEPENDENT"
                )
                .doesNotContain("OrderRfqMapper")
                .doesNotContain("ItemQuoteVersionMapper")
                .doesNotContain("SalesOrderRepeatEnrichmentService")
                .doesNotContain("repeatEnrichmentService")
                .doesNotContain("enrichSalesOrderItems")
                .doesNotContain("SalesOrderRepeatEnrichmentResult")
                .doesNotContain("new OrderRfq")
                .doesNotContain("new ItemQuoteVersion");
        assertThat(importResultRow)
                .contains("private Long salesOrderId;", "private String salesOrderNo;")
                .doesNotContain("rfqId")
                .doesNotContain("rfqNo");
        assertThat(orderService)
                .contains("row.put(\"sourceType\", \"SALES_ORDER\")")
                .contains("row.put(\"sourceOrderNo\", order.getSalesOrderNo())")
                .contains("row.put(\"salesOrderNo\", order.getSalesOrderNo())")
                .doesNotContain("row.put(\"rfqNo\", order.getSalesOrderNo())");
        assertThat(releaseService)
                .contains("header.sales_order_no AS source_order_no")
                .contains("row.put(\"rfqNo\", ReleaseSourceType.RFQ.name().equals(sourceType)")
                .contains("row.put(\"sourceOrderNo\", order == null ? \"\" : order.getSalesOrderNo())")
                .contains("row.put(\"rfqNo\", \"\")")
                .doesNotContain("row.put(\"rfqNo\", order == null ? \"\" : order.getSalesOrderNo())")
                .doesNotContain("header.sales_order_no AS rfq_no");
        assertThat(enrichmentService)
                .contains("ItemQuoteVersionMapper")
                .contains("selectLatestReleasedRepeatSources")
                .contains("salesOrderItemMapper.updateById(item)")
                .contains("releasedProductionSnapshotsByKey")
                .doesNotContain("public SalesOrderRepeatEnrichmentResult enrich(Integer rfqId")
                .doesNotContain("salesOrderItemMapper.insert");
        assertThat(salesMatchService)
                .contains("public SalesOrderMatchPreviewResp preview")
                .contains("public SalesOrderMatchApplyResp apply")
                .contains("selectLatestReleasedRepeatSources")
                .contains("salesOrderItemMapper.updateById(item)")
                .doesNotContain("orderRfqMapper.insert")
                .doesNotContain("itemQuoteVersionMapper.insert");
        assertThat(dataScopeService)
                .contains("FROM sales_order_header")
                .contains("FROM order_rfq")
                .contains("snapshot.customerNames()");

        assertThat(auditActionTests).contains("importRfq");
        assertThat(coreWorkflowTests)
                .contains("completeOrderQuote")
                .contains("saveQuoteSheet")
                .contains("generateTickets");
        assertThat(releaseTests).contains("RFQ", "SALES_ORDER");
        assertThat(marketController).contains("@PostMapping(\"/release/items/batch\")");
        assertThat(stage6Tests)
                .contains("rfqGenerationWritesSourceIdentityForBackwardCompatibleTickets")
                .contains("generatesSalesOrderTicketsAndRollsHeaderStatusFromPartialToReleased");
        assertThat(deliveredTests).contains("LEFT JOIN order_rfq rfq", "sales_item.drawing_url");
        assertThat(deliveredMapper).contains("LEFT JOIN sales_order_item sales_item");
        assertThat(lockedTests).contains("LEFT JOIN order_rfq rfq", "LEFT JOIN order_delivery_ticket ticket");
        assertThat(lockedMapper).contains("alloc.source_type", "alloc.source_order_id", "alloc.source_item_id");
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
