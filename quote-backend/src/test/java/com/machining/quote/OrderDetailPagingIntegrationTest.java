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

class OrderDetailPagingIntegrationTest extends LargeOrderItemPagingTestSupport {

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
                .contains("deliveryService.generateRfqReleaseBatch(req)");
    }

    @Test
    void quoteProcessServiceUsesPagedItemsAndSummaryResults() throws Exception {
        String service = source("service", "QuoteProcessService.java");

        assertThat(service)
                .contains("public Map<String, Object> getQuoteProcessSummary(Integer rfqId)")
                .contains("public Map<String, Object> getQuoteProcessSummary(Integer rfqId, String scope)")
                .contains("public PageResult<Map<String, Object>> listQuoteProcessItems(OrderItemPageReq req)")
                .contains("public Map<String, Object> matchPartSelection(Integer rfqId, List<Map<String, String>> importedItems)")
                .contains("orderItemPageService.pageItems(req)");
        String summaryMethod = service.substring(service.indexOf(
                "public Map<String, Object> getQuoteProcessSummary(Integer rfqId, String scope)"));
        assertThat(javaMethod(summaryMethod, "getQuoteProcessSummary"))
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
}
