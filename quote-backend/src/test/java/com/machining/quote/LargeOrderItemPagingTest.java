package com.machining.quote;

import com.machining.quote.controller.EngineerController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
    private InternalTargetLeadTimeService internalTargetLeadTimeService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private QuoteProcessService quoteProcessService;
    @Autowired
    private EngineerController engineerController;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;

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
    void pricingControllerPagesAssemblyChildCandidatesWithoutLoadingWholeOrder() {
        OrderRfq order = createOrder("ASSEMBLY-CHILD-PAGE");
        ItemQuoteVersion parent = createItem(order, "PARENT", QuoteStatusWorkflow.UNPRICED, null, "/parent.pdf");
        createItem(order, "CHILD-01", QuoteStatusWorkflow.PRICED, 11.0, "/child-1.pdf");
        createItem(order, "CHILD-02", QuoteStatusWorkflow.PRICED, 12.0, "/child-2.pdf");
        createItem(order, "CHILD-03", QuoteStatusWorkflow.PRICED, 13.0, "/child-3.pdf");
        createItem(order, "OTHER-01", QuoteStatusWorkflow.PRICED, 99.0, "/other.pdf");

        PageResult<ItemQuoteVersion> page = engineerController.listAssemblyChildCandidates(
                order.getId(), parent.getId(), "CHILD", 2, 2);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getRecords())
                .extracting(ItemQuoteVersion::getPartNo)
                .containsExactly("CHILD-03");
        assertThat(page.getRecords())
                .extracting(ItemQuoteVersion::getId)
                .doesNotContain(parent.getId());
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

        String quoteProcessController = source("controller", "QuoteProcessController.java");
        assertThat(quoteProcessController)
                .contains("@GetMapping(\"/{rfqId}/summary\")")
                .contains("@GetMapping(\"/{rfqId}/items\")")
                .contains("@PostMapping(\"/{rfqId}/parse-part-selection\")")
                .contains("@RequirePermission(\"market:quote-process:update-lead-time\")")
                .contains("PageResult<Map<String, Object>>");
        assertThat(method(quoteProcessController, "parsePartSelection"))
                .contains("partSelectionImportService.readPartSelectionRows(excelFile)")
                .contains("quoteProcessService.matchPartSelection(rfqId");
        assertThat(method(quoteProcessController, "getQuoteProcessSummary"))
                .contains("quoteProcessService.getQuoteProcessSummary(rfqId)")
                .doesNotContain("quoteProcessService.getQuoteProcess(rfqId)")
                .doesNotContain("itemList");
        assertThat(method(quoteProcessController, "listQuoteProcessItems"))
                .contains("quoteProcessService.listQuoteProcessItems(req)")
                .doesNotContain("quoteProcessService.getQuoteProcess(rfqId)")
                .doesNotContain("workspace.getOrDefault(\"itemList\"");

        String marketController = source("controller", "MarketController.java");
        assertThat(marketController)
                .contains("@GetMapping(\"/release/items/{rfqId}\")")
                .contains("@GetMapping(\"/tickets/{rfqId}\")")
                .contains("@GetMapping(\"/process-sheets/{rfqId}\")")
                .contains("@PostMapping(\"/release/parse-part-selection/{rfqId}\")");
        assertThat(method(marketController, "getReleaseSummary"))
                .contains("internalTargetLeadTimeService.releaseSummary(rfqId)")
                .doesNotContain("itemQuoteVersionMapper.selectList")
                .doesNotContain("result.put(\"itemList\"");
        assertThat(method(marketController, "listReleaseItems"))
                .contains("internalTargetLeadTimeService.pageItemRows")
                .doesNotContain("getReleaseSummary(rfqId)")
                .doesNotContain("page((List<ItemQuoteVersion>)");
        assertThat(method(marketController, "listDeliveryTickets"))
                .contains("deliveryService.pageTickets")
                .doesNotContain("deliveryService.listTickets(rfqId)");
        assertThat(method(marketController, "listProcessSheets"))
                .contains("deliveryService.pageProcessSheets")
                .doesNotContain("deliveryService.listProcessSheets(rfqId)");

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
                .contains("pageDynamicProcessSheets(rfqId, page, pageSize)")
                .contains("getDrawingSummary(rfqId)");
        assertThat(service)
                .contains("public PageResult<ItemQuoteVersion> pageOrderItems")
                .contains("public PageResult<OrderDeliveryTicket> pageOrderTickets")
                .contains("public PageResult<ProcessSheetDTO> pageDynamicProcessSheets")
                .contains("public Map<String, Object> getDrawingSummary");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderBaseDetailDoesNotEmbedWholeDetailLists() {
        OrderRfq order = createOrder("ORDER-DETAIL-PAGED");
        createItem(order, "DETAIL-01", QuoteStatusWorkflow.PRICED, 10.0, "/detail-1.pdf");
        ItemQuoteVersion missing = createItem(order, "DETAIL-02", QuoteStatusWorkflow.UNPRICED, null, null);
        createTicket(order, missing, 1);

        Map<String, Object> detail = orderManagementService.getOrderDetail(order.getId());

        assertThat(detail)
                .containsKeys("summary", "orderInfo", "fileRoot", "drawingSummary")
                .doesNotContainKeys("itemList", "ticketList", "processSheetList", "missingDrawingFiles");
        Map<String, Object> summary = (Map<String, Object>) detail.get("summary");
        Map<String, Object> drawingSummary = (Map<String, Object>) detail.get("drawingSummary");
        assertThat(summary)
                .containsEntry("itemCount", 2)
                .containsEntry("missingDrawingCount", 1)
                .containsEntry("ticketCount", 1)
                .containsEntry("processSheetCount", 1);
        assertThat(drawingSummary)
                .containsEntry("itemCount", 2L)
                .containsEntry("boundDrawingCount", 1L)
                .containsEntry("missingDrawingCount", 1L);
    }

    @Test
    void orderDetailTabsUsePagedQueriesAndDrawingSummaryUsesAggregates() throws Exception {
        OrderRfq order = createOrder("ORDER-TAB-PAGED");
        ItemQuoteVersion first = createItem(order, "TAB-01", QuoteStatusWorkflow.PRICED, 10.0, "/tab-1.pdf");
        ItemQuoteVersion second = createItem(order, "TAB-02", QuoteStatusWorkflow.PRICED, 11.0, null);
        createTicket(order, first, 1);
        createTicket(order, second, 1);

        PageResult<ItemQuoteVersion> itemPage = orderManagementService.pageOrderItems(order.getId(), 2, 1);
        PageResult<OrderDeliveryTicket> ticketPage = orderManagementService.pageOrderTickets(order.getId(), 2, 1);
        PageResult<ProcessSheetDTO> processSheetPage = orderManagementService.pageDynamicProcessSheets(order.getId(), 2, 1);

        assertThat(itemPage.getTotal()).isEqualTo(2);
        assertThat(itemPage.getRecords()).extracting(ItemQuoteVersion::getPartNo).containsExactly("TAB-02");
        assertThat(ticketPage.getTotal()).isEqualTo(2);
        assertThat(ticketPage.getRecords()).extracting(OrderDeliveryTicket::getPartNo).containsExactly("TAB-02");
        assertThat(processSheetPage.getTotal()).isEqualTo(2);
        assertThat(processSheetPage.getRecords()).extracting(ProcessSheetDTO::getPartNo).containsExactly("TAB-02");

        String service = source("service", "OrderManagementService.java");
        assertThat(method(service, "getOrderDetail"))
                .doesNotContain("detail.put(\"itemList\"")
                .doesNotContain("detail.put(\"ticketList\"")
                .doesNotContain("detail.put(\"processSheetList\"");
        assertThat(method(service, "pageOrderItems"))
                .contains("itemMapper.selectCount")
                .contains("LIMIT")
                .doesNotContain("pageRows(listItems");
        assertThat(method(service, "pageOrderTickets"))
                .contains("ticketMapper.selectCount")
                .contains("LIMIT")
                .doesNotContain("pageRows(listTickets");
        assertThat(method(service, "pageDynamicProcessSheets"))
                .contains("deliveryService.pageProcessSheets")
                .doesNotContain("pageRows(listProcessSheets");
        assertThat(method(service, "getDrawingSummary"))
                .contains("itemMapper.selectMaps")
                .doesNotContain("listItems(rfqId)");
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

    @Test
    void quoteProcessServiceUsesPagedItemsAndSummaryResults() throws Exception {
        String service = source("service", "QuoteProcessService.java");

        assertThat(service)
                .contains("public Map<String, Object> getQuoteProcessSummary(Integer rfqId)")
                .contains("public PageResult<Map<String, Object>> listQuoteProcessItems(OrderItemPageReq req)")
                .contains("public Map<String, Object> matchPartSelection(Integer rfqId, List<Map<String, String>> importedItems)")
                .contains("orderItemPageService.pageItems(req)");
        assertThat(javaMethod(service, "getQuoteProcessSummary"))
                .contains("orderItemPageService.summarizeItems(rfqId)")
                .doesNotContain("listItems(rfqId)")
                .doesNotContain("itemList");
        assertThat(method(service, "shiftLeadTimes"))
                .contains("quoteLeadTimeMutationSummary")
                .doesNotContain("result.put(\"itemList\"");
        assertThat(method(service, "setLeadTimes"))
                .contains("quoteLeadTimeMutationSummary")
                .doesNotContain("result.put(\"itemList\"");
        assertThat(method(service, "saveQuoteSheet"))
                .contains("quoteSheetSummary")
                .doesNotContain("result.put(\"itemList\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void quoteProcessPartSelectionMatchesCurrentEditableOrderWithoutLoadingPage() {
        OrderRfq editableOrder = createOrder("QUOTE-IMPORT");
        editableOrder.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.updateById(editableOrder);
        ItemQuoteVersion firstMatch = createItem(editableOrder, "QUOTE-IMPORT-01", QuoteStatusWorkflow.PRICED, 10.0, "/quote-import-1.pdf");
        ItemQuoteVersion secondMatch = createItem(editableOrder, "QUOTE-IMPORT-01", QuoteStatusWorkflow.PRICED, 11.0, "/quote-import-2.pdf");
        createItem(editableOrder, "QUOTE-IMPORT-02", QuoteStatusWorkflow.PRICED, 12.0, "/quote-import-3.pdf");
        OrderRfq quotedOrder = createOrder("QUOTE-IMPORT-LOCKED");
        quotedOrder.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.updateById(quotedOrder);
        createItem(quotedOrder, "QUOTE-IMPORT-01", QuoteStatusWorkflow.QUOTED, 13.0, "/quote-import-locked.pdf");

        Map<String, Object> result = quoteProcessService.matchPartSelection(
                editableOrder.getId(),
                List.of(importedSelection("", "QUOTE-IMPORT-01", "A")));
        List<Map<String, Object>> selectedRows = (List<Map<String, Object>>) result.get("data");

        assertThat(result)
                .containsEntry("importedCount", 1)
                .containsEntry("matchedCount", 2)
                .containsEntry("selectedCount", 2)
                .containsEntry("skippedCount", 0);
        assertThat(selectedRows)
                .extracting(row -> row.get("id"))
                .containsExactly(firstMatch.getId(), secondMatch.getId());
        assertThat(selectedRows)
                .extracting(row -> row.get("rfqId"))
                .containsOnly(editableOrder.getId());
    }

    @Test
    void releaseItemPagingReturnsBackendRemainingQuantityForCurrentPage() {
        OrderRfq order = createOrder("RELEASE-PAGE");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.updateById(order);
        ItemQuoteVersion first = createItem(order, "REL-01", QuoteStatusWorkflow.QUOTED, 10.0, "/rel-1.pdf");
        first.setQty(10);
        itemMapper.updateById(first);
        ItemQuoteVersion second = createItem(order, "REL-02", QuoteStatusWorkflow.ITEM_RELEASED, 20.0, "/rel-2.pdf");
        second.setQty(5);
        itemMapper.updateById(second);
        createTicket(order, first, 3);
        createTicket(order, second, 5);

        PageResult<Map<String, Object>> firstPage = internalTargetLeadTimeService.pageItemRows(
                order.getId(), 1, 1, null, null, false);
        PageResult<Map<String, Object>> secondPage = internalTargetLeadTimeService.pageItemRows(
                order.getId(), 2, 1, null, null, false);

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getRecords()).hasSize(1);
        assertThat(firstPage.getRecords().get(0))
                .containsEntry("id", first.getId())
                .containsEntry("ticketedQty", 3)
                .containsEntry("remainingQty", 7)
                .containsEntry("canSelect", true)
                .containsEntry("releaseBlockedReason", "");
        assertThat(secondPage.getRecords().get(0))
                .containsEntry("id", second.getId())
                .containsEntry("ticketedQty", 5)
                .containsEntry("remainingQty", 0)
                .containsEntry("canSelect", false);
    }

    @Test
    void releaseBatchItemPagingCombinesSelectedOrdersWithBackendPagination() {
        OrderRfq firstOrder = createReleaseOrder("RELEASE-BATCH-A");
        OrderRfq secondOrder = createReleaseOrder("RELEASE-BATCH-B");
        OrderRfq unrelatedOrder = createReleaseOrder("RELEASE-BATCH-C");
        createItem(firstOrder, "BATCH-A-01", QuoteStatusWorkflow.QUOTED, 10.0, "/batch-a-1.pdf");
        createItem(firstOrder, "BATCH-A-02", QuoteStatusWorkflow.QUOTED, 11.0, "/batch-a-2.pdf");
        ItemQuoteVersion secondFirst = createItem(secondOrder, "BATCH-B-01", QuoteStatusWorkflow.QUOTED, 20.0, "/batch-b-1.pdf");
        ItemQuoteVersion secondSecond = createItem(secondOrder, "BATCH-B-02", QuoteStatusWorkflow.QUOTED, 21.0, "/batch-b-2.pdf");
        createItem(unrelatedOrder, "BATCH-C-99", QuoteStatusWorkflow.QUOTED, 99.0, "/batch-c-99.pdf");

        ReleaseItemBatchQueryReq req = new ReleaseItemBatchQueryReq();
        req.setRfqIds(List.of(firstOrder.getId(), secondOrder.getId()));
        req.setPage(2);
        req.setPageSize(2);

        PageResult<Map<String, Object>> page = internalTargetLeadTimeService.pageItemRowsBatch(req);

        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getRecords())
                .extracting(row -> row.get("id"))
                .containsExactly(secondFirst.getId(), secondSecond.getId());
        assertThat(page.getRecords())
                .extracting(row -> row.get("rfqId"))
                .containsOnly(secondOrder.getId());
        assertThat(page.getRecords().get(0))
                .containsEntry("rfqNo", secondOrder.getRfqNo())
                .containsEntry("customerName", secondOrder.getCustomerName())
                .containsEntry("orderStatus", secondOrder.getStatus());
        assertThat(page.getRecords())
                .extracting(row -> row.get("partNo"))
                .doesNotContain("BATCH-C-99");
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchPartSelectionWithoutRfqNoMatchesAcrossAllSelectedOrders() {
        OrderRfq firstOrder = createReleaseOrder("BATCH-IMPORT-A");
        OrderRfq secondOrder = createReleaseOrder("BATCH-IMPORT-B");
        OrderRfq unrelatedOrder = createReleaseOrder("BATCH-IMPORT-C");
        ItemQuoteVersion firstItem = createItem(firstOrder, "SHARED-IMPORT-01", QuoteStatusWorkflow.QUOTED, 10.0, "/import-a.pdf");
        ItemQuoteVersion secondItem = createItem(secondOrder, "SHARED-IMPORT-01", QuoteStatusWorkflow.QUOTED, 11.0, "/import-b.pdf");
        createItem(unrelatedOrder, "SHARED-IMPORT-01", QuoteStatusWorkflow.QUOTED, 12.0, "/import-c.pdf");

        Map<String, Object> result = internalTargetLeadTimeService.matchPartSelectionBatch(
                List.of(ReleaseOrderSource.rfq(firstOrder.getId()), ReleaseOrderSource.rfq(secondOrder.getId())),
                List.of(importedSelection("", "SHARED-IMPORT-01", "A")));
        List<Map<String, Object>> selectedRows = (List<Map<String, Object>>) result.get("data");

        assertThat(result)
                .containsEntry("importedCount", 1)
                .containsEntry("matchedCount", 2)
                .containsEntry("selectedCount", 2)
                .containsEntry("skippedCount", 0);
        assertThat(selectedRows)
                .extracting(row -> row.get("id"))
                .containsExactly(firstItem.getId(), secondItem.getId());
        assertThat(selectedRows)
                .extracting(row -> row.get("rfqNo"))
                .containsExactly(firstOrder.getRfqNo(), secondOrder.getRfqNo());
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchPartSelectionWithRfqNoMatchesOnlySpecifiedOrder() {
        OrderRfq firstOrder = createReleaseOrder("BATCH-IMPORT-RFQ-A");
        OrderRfq secondOrder = createReleaseOrder("BATCH-IMPORT-RFQ-B");
        createItem(firstOrder, "RFQ-SPECIFIC-01", QuoteStatusWorkflow.QUOTED, 10.0, "/rfq-a.pdf");
        ItemQuoteVersion secondItem = createItem(secondOrder, "RFQ-SPECIFIC-01", QuoteStatusWorkflow.QUOTED, 11.0, "/rfq-b.pdf");

        Map<String, Object> result = internalTargetLeadTimeService.matchPartSelectionBatch(
                List.of(ReleaseOrderSource.rfq(firstOrder.getId()), ReleaseOrderSource.rfq(secondOrder.getId())),
                List.of(importedSelection(secondOrder.getRfqNo(), "RFQ-SPECIFIC-01", "A")));
        List<Map<String, Object>> selectedRows = (List<Map<String, Object>>) result.get("data");

        assertThat(result)
                .containsEntry("importedCount", 1)
                .containsEntry("matchedCount", 1)
                .containsEntry("selectedCount", 1);
        assertThat(selectedRows)
                .extracting(row -> row.get("id"))
                .containsExactly(secondItem.getId());
        assertThat(selectedRows.get(0))
                .containsEntry("rfqId", secondOrder.getId())
                .containsEntry("rfqNo", secondOrder.getRfqNo())
                .containsEntry("customerName", secondOrder.getCustomerName())
                .containsEntry("orderStatus", secondOrder.getStatus());
    }

    @Test
    void releaseBatchItemPagingExposesStableBackendContracts() throws Exception {
        assertThat(MAIN_JAVA.resolve("dto").resolve("ReleaseItemBatchQueryReq.java")).exists();

        String marketController = source("controller", "MarketController.java");
        assertThat(marketController)
                .contains("@PostMapping(\"/release/items/batch\")")
                .contains("ReleaseItemBatchQueryReq")
                .contains("internalTargetLeadTimeService.pageItemRowsBatch(req)");
        assertThat(method(marketController, "listReleaseItemsBatch"))
                .doesNotContain("getReleaseSummary")
                .doesNotContain("page((List");

        String releaseService = source("service", "InternalTargetLeadTimeService.java");
        assertThat(releaseService)
                .contains("public PageResult<Map<String, Object>> pageItemRowsBatch(ReleaseItemBatchQueryReq req)")
                .contains("orderItemPageService.pageItemsBatch(queryReq)");
        assertThat(javaMethod(releaseService, "pageItemRowsBatch"))
                .doesNotContain("buildItemRows(")
                .doesNotContain("listItems(");

        String itemPageService = source("service", "OrderItemPageService.java");
        assertThat(itemPageService)
                .contains("public OrderItemPageDTO pageItemsBatch(ReleaseItemBatchQueryReq req)")
                .contains("selectCount(buildFilteredBatchQuery")
                .contains("LIMIT \" + pageSize + \" OFFSET \" + offset")
                .contains(".in(\"rfq_id\", accessibleRfqIds)")
                .contains(".orderByAsc(\"rfq_id\")")
                .contains(".orderByAsc(\"id\")");
    }

    @Test
    void releaseTicketsAndProcessSheetsArePagedByDeliveryService() {
        OrderRfq order = createOrder("RELEASE-TICKET-PAGE");
        order.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.updateById(order);
        ItemQuoteVersion first = createItem(order, "TICKET-01", QuoteStatusWorkflow.ITEM_RELEASED, 10.0, "/ticket-1.pdf");
        ItemQuoteVersion second = createItem(order, "TICKET-02", QuoteStatusWorkflow.ITEM_RELEASED, 20.0, "/ticket-2.pdf");
        createTicket(order, first, 1);
        createTicket(order, second, 1);

        PageResult<OrderDeliveryTicket> tickets = deliveryService.pageTickets(order.getId(), 2, 1);
        PageResult<?> processSheets = deliveryService.pageProcessSheets(order.getId(), 2, 1);

        assertThat(tickets.getTotal()).isEqualTo(2);
        assertThat(tickets.getRecords()).hasSize(1);
        assertThat(tickets.getRecords().get(0).getPartNo()).isEqualTo("TICKET-02");
        assertThat(processSheets.getTotal()).isEqualTo(2);
        assertThat(processSheets.getRecords()).hasSize(1);
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

    private String javaMethod(String source, String methodName) {
        int nameIndex = source.indexOf(methodName + "(");
        assertThat(nameIndex).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int start = source.lastIndexOf('\n', nameIndex);
        int firstBrace = source.indexOf('{', nameIndex);
        assertThat(firstBrace).as("method %s has a body", methodName).isGreaterThanOrEqualTo(0);
        int depth = 0;
        for (int index = firstBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(Math.max(0, start), index + 1);
                }
            }
        }
        throw new AssertionError("method body not closed: " + methodName);
    }

    private OrderRfq createOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.UNPRICED);
        orderMapper.insert(order);
        return order;
    }

    private OrderRfq createReleaseOrder(String prefix) {
        OrderRfq order = createOrder(prefix);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.updateById(order);
        return order;
    }

    private ItemQuoteVersion createItem(OrderRfq order, String partNo, String status, Double finalUnitPrice, String drawingUrl) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setQty(1);
        item.setStatus(status);
        item.setFinalUnitPrice(finalUnitPrice);
        item.setDrawingUrl(drawingUrl);
        itemMapper.insert(item);
        return item;
    }

    private OrderDeliveryTicket createTicket(OrderRfq order, ItemQuoteVersion item, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setPartNo(item.getPartNo());
        ticket.setVersion(item.getVersion());
        ticket.setQty(qty);
        ticketMapper.insert(ticket);
        return ticket;
    }

    private Map<String, String> importedSelection(String rfqNo, String partNo, String version) {
        return Map.of(
                "rfqNo", rfqNo,
                "normalizedRfqNo", rfqNo == null ? "" : rfqNo.trim().toUpperCase(),
                "partNo", partNo,
                "normalizedPartNo", partNo == null ? "" : partNo.trim().toUpperCase(),
                "version", version,
                "normalizedVersion", version == null ? "" : version.trim().toUpperCase()
        );
    }
}
