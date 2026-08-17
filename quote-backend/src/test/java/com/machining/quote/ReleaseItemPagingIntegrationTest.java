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

class ReleaseItemPagingIntegrationTest extends LargeOrderItemPagingTestSupport {

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
}
