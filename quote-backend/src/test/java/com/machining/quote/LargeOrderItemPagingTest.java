package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
@Transactional
class LargeOrderItemPagingTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    @Autowired
    private OrderItemPageService orderItemPageService;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;

    @AfterEach
    void tearDown() {
        // Transactional rollback handles test data; method kept as a visible reminder that tests are isolated.
    }

    @Test
    void sharedPagingFoundationFilesAndIndexesExist() throws Exception {
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemPageReq.java")).exists();
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemPageDTO.java")).exists();
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemSummaryDTO.java")).exists();
        assertThat(MAIN_JAVA.resolve("service").resolve("OrderItemPageService.java")).exists();

        String migration = Files.readString(
                MAIN_RESOURCES.resolve("db").resolve("migration").resolve("V56__large_order_item_paging_indexes.sql"),
                StandardCharsets.UTF_8);
        assertThat(migration)
                .contains("idx_item_quote_version_rfq_id_id")
                .contains("idx_item_quote_version_rfq_status_id")
                .contains("idx_item_quote_version_rfq_part_version_id")
                .contains("idx_order_delivery_ticket_rfq_id_id")
                .contains("idx_order_delivery_ticket_rfq_item_id")
                .doesNotContain("customer_name");

        String schema = Files.readString(MAIN_RESOURCES.resolve("schema.sql"), StandardCharsets.UTF_8);
        assertThat(schema)
                .contains("idx_item_quote_version_rfq_id_id")
                .contains("idx_item_quote_version_rfq_status_id")
                .contains("idx_item_quote_version_rfq_part_version_id")
                .contains("idx_order_delivery_ticket_rfq_id_id")
                .contains("idx_order_delivery_ticket_rfq_item_id");
    }

    @Test
    void pagesItemsByRfqIdAndReturnsWholeOrderSummary() {
        OrderRfq firstOrder = createOrder("LARGE-PAGE-A");
        OrderRfq secondOrder = createOrder("LARGE-PAGE-B");
        createItem(firstOrder, "PAGE-01", QuoteStatusWorkflow.PRICED, 10.0, "/drawing-1.pdf");
        createItem(firstOrder, "PAGE-02", QuoteStatusWorkflow.QUOTED, 11.0, "/drawing-2.pdf");
        createItem(firstOrder, "PAGE-03", QuoteStatusWorkflow.RELEASED, 12.0, null);
        createItem(firstOrder, "PAGE-04", QuoteStatusWorkflow.UNPRICED, null, null);
        createItem(secondOrder, "PAGE-99", QuoteStatusWorkflow.PRICED, 99.0, "/other.pdf");

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(firstOrder.getId());
        req.setPage(2);
        req.setPageSize(2);
        OrderItemPageDTO page = orderItemPageService.pageItems(req);

        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getRecords())
                .extracting(ItemQuoteVersion::getPartNo)
                .containsExactly("PAGE-03", "PAGE-04");
        assertThat(page.getRecords())
                .extracting(ItemQuoteVersion::getRfqId)
                .containsOnly(firstOrder.getId());

        OrderItemSummaryDTO summary = page.getSummary();
        assertThat(summary.getItemCount()).isEqualTo(4);
        assertThat(summary.getPricedCount()).isEqualTo(3);
        assertThat(summary.getQuotedCount()).isEqualTo(1);
        assertThat(summary.getReleasedCount()).isEqualTo(1);
        assertThat(summary.getMissingDrawingCount()).isEqualTo(2);
    }

    @Test
    void summaryUsesDatabaseAggregatesRatherThanPageRecords() throws Exception {
        OrderRfq order = createOrder("LARGE-SUMMARY");
        createItem(order, "SUMMARY-01", QuoteStatusWorkflow.PRICED, 1.0, "/drawing-1.pdf");
        createItem(order, "SUMMARY-02", QuoteStatusWorkflow.PRICED, 2.0, "/drawing-2.pdf");
        createItem(order, "SUMMARY-03", QuoteStatusWorkflow.UNPRICED, null, null);

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(order.getId());
        req.setPage(1);
        req.setPageSize(1);
        OrderItemPageDTO page = orderItemPageService.pageItems(req);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getSummary().getItemCount()).isEqualTo(3);
        assertThat(page.getSummary().getPricedCount()).isEqualTo(2);
        assertThat(page.getSummary().getMissingDrawingCount()).isEqualTo(1);

        String serviceSource = assertSourceContains("service", "OrderItemPageService.java", "COUNT(*) AS item_count");
        assertThat(serviceSource)
                .contains("SUM(CASE WHEN final_unit_price IS NOT NULL THEN 1 ELSE 0 END)")
                .contains("SUM(CASE WHEN status =");
    }

    @Test
    void setupSummaryRemainsPreviewOnly() throws Exception {
        String source = source("controller", "EngineerController.java");

        assertThat(source)
                .contains("SETUP_PREVIEW_LIMIT = 10")
                .contains("@GetMapping(\"/setup/summary/{rfqId}\")")
                .contains("result.put(\"previewItemList\", previewItems)")
                .contains("result.put(\"itemCount\", itemCount == null ? 0L : itemCount)")
                .contains(".last(\"LIMIT \" + SETUP_PREVIEW_LIMIT)");
    }

    @Test
    void itemOperationModulesExposeStablePagingContracts() throws Exception {
        assertThat(source("controller", "EngineerController.java"))
                .contains("@GetMapping(\"/pricing/items/{rfqId}\")")
                .contains("@GetMapping(\"/pricing/assembly-child-candidates/{rfqId}\")")
                .contains("PageResult<ItemQuoteVersion>");

        assertThat(source("controller", "QuoteProcessController.java"))
                .contains("@GetMapping(\"/{rfqId}/summary\")")
                .contains("@GetMapping(\"/{rfqId}/items\")")
                .contains("PageResult<Map<String, Object>>");

        assertThat(source("controller", "MarketController.java"))
                .contains("@GetMapping(\"/release/items/{rfqId}\")")
                .contains("@GetMapping(\"/release/tickets/{rfqId}\")")
                .contains("@GetMapping(\"/release/process-sheets/{rfqId}\")");

        assertThat(source("controller", "OrderManagementController.java"))
                .contains("@GetMapping(\"/orders/{rfqId}/items\")")
                .contains("@GetMapping(\"/orders/{rfqId}/tickets\")")
                .contains("@GetMapping(\"/orders/{rfqId}/process-sheets\")")
                .contains("@GetMapping(\"/orders/{rfqId}/drawing-summary\")");
    }

    @Test
    void orderDetailPagingUsesExplicitServiceContracts() throws Exception {
        String controller = source("controller", "OrderManagementController.java");
        String service = source("service", "OrderManagementService.java");

        assertThat(controller)
                .contains("pageOrderItems(rfqId, page, pageSize)")
                .contains("pageOrderTickets(rfqId, page, pageSize)")
                .contains("pageOrderProcessSheets(rfqId, page, pageSize)")
                .contains("getDrawingSummary(rfqId)");
        assertThat(service)
                .contains("public PageResult<ItemQuoteVersion> pageOrderItems")
                .contains("public PageResult<OrderDeliveryTicket> pageOrderTickets")
                .contains("public PageResult<ProcessSheetDTO> pageOrderProcessSheets")
                .contains("public Map<String, Object> getDrawingSummary");
    }

    @Test
    void wholeOrderActionsRemainOrderScopedNotPageScoped() throws Exception {
        assertThat(method(source("controller", "EngineerController.java"), "completeOrderQuote"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.completeOrderQuote(rfqId)");
        assertThat(method(source("controller", "EngineerController.java"), "refreshOrderQuotes"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.refreshOrderQuotes(rfqId)");
        assertThat(method(source("controller", "EngineerController.java"), "applyRepeatQuotes"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("engineerService.applyRepeatQuotes(rfqId)");

        assertThat(method(source("controller", "QuoteProcessController.java"), "saveQuoteSheet"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("quoteProcessService.saveQuoteSheet(rfqId)");
        assertThat(method(source("controller", "MarketController.java"), "generateDeliveryTicket"))
                .doesNotContain("RequestParam(value = \"page\"")
                .contains("deliveryService.generateTickets(req)");
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }

    private String assertSourceContains(String packageName, String fileName, String expected) throws Exception {
        String source = source(packageName, fileName);
        assertThat(source).contains(expected);
        return source;
    }

    private String method(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertThat(start).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int nextAnnotation = source.indexOf("\n    @", start + methodName.length());
        int end = nextAnnotation >= 0 ? nextAnnotation : source.length();
        return source.substring(start, end);
    }

    private OrderRfq createOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.UNPRICED);
        orderMapper.insert(order);
        return order;
    }

    private void createItem(OrderRfq order, String partNo, String status, Double finalUnitPrice, String drawingUrl) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setQty(1);
        item.setStatus(status);
        item.setFinalUnitPrice(finalUnitPrice);
        item.setDrawingUrl(drawingUrl);
        itemMapper.insert(item);
    }
}
