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

class LargeOrderItemPagingFoundationTest extends LargeOrderItemPagingTestSupport {

    @Test
    void sharedPagingFoundationFilesAndIndexesExist() throws Exception {
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemPageReq.java")).exists();
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemPageDTO.java")).exists();
        assertThat(MAIN_JAVA.resolve("dto").resolve("OrderItemSummaryDTO.java")).exists();
        assertThat(MAIN_JAVA.resolve("service").resolve("OrderItemPageService.java")).exists();

        String migration = Files.readString(
                MAIN_RESOURCES.resolve("db").resolve("migration").resolve("V1__qpo_baseline_schema.sql"),
                StandardCharsets.UTF_8);
        assertThat(migration)
                .contains("idx_item_quote_version_rfq_id_id")
                .contains("idx_item_quote_version_rfq_status_id")
                .contains("idx_item_quote_version_rfq_part_version_id")
                .contains("idx_order_delivery_ticket_rfq_id_id")
                .contains("idx_order_delivery_ticket_rfq_item_id")
                .doesNotContain("idx_item_quote_version_customer_name")
                .doesNotContain("idx_order_delivery_ticket_customer_name");

        String schema = Files.readString(
                MAIN_RESOURCES.resolve(Path.of("db", "migration", "V1__qpo_baseline_schema.sql")),
                StandardCharsets.UTF_8);
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
                .contains("SUM(CASE WHEN tax_included_unit_price IS NOT NULL THEN 1 ELSE 0 END)")
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
                .contains("quoteProcessService.getQuoteProcessSummary(rfqId, scope)")
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
                .contains("pageOrderItemRows(rfqId, page, pageSize)")
                .contains("pageOrderTickets(rfqId, page, pageSize)")
                .contains("pageDynamicProcessSheets(rfqId, page, pageSize)")
                .contains("getDrawingSummary(rfqId)");
        assertThat(service)
                .contains("public PageResult<Map<String, Object>> pageOrderItemRows")
                .contains("public PageResult<ItemQuoteVersion> pageOrderItems")
                .contains("public PageResult<OrderDeliveryTicket> pageOrderTickets")
                .contains("public PageResult<ProcessSheetDTO> pageDynamicProcessSheets")
                .contains("public Map<String, Object> getDrawingSummary");
    }
}
