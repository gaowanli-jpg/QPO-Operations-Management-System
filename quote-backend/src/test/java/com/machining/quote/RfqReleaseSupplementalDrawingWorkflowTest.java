package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RfqReleaseSupplementalDrawingWorkflowTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    void pricedSupplementalDrawingItemCannotBeSelectedUntilMarketConfirmsQuote() {
        OrderRfq order = releasedOrder("SUPP-SELECT");
        ItemQuoteVersion supplemental = rfqItem(order, "SUPP-SELECT-PART", QuoteStatusWorkflow.PRICED, 2, 32.0);

        Map<String, Object> beforeConfirmation = releaseRow(order, supplemental);

        assertThat(beforeConfirmation)
                .containsEntry("status", QuoteStatusWorkflow.PRICED)
                .containsEntry("canSelect", false)
                .containsEntry("releaseBlockedReason", "补充报价尚未确认");

        supplemental.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.updateById(supplemental);

        Map<String, Object> afterConfirmation = releaseRow(order, supplemental);

        assertThat(afterConfirmation)
                .containsEntry("status", QuoteStatusWorkflow.QUOTED)
                .containsEntry("canSelect", true)
                .containsEntry("releaseBlockedReason", "");
    }

    @Test
    void supplementalQuoteRecalculatesReleasedOrderThenAppendReleaseRestoresReleasedWithoutChangingHistory() {
        OrderRfq order = releasedOrder("SUPP-APPEND");
        ItemQuoteVersion historical = rfqItem(order, "SUPP-HISTORY", QuoteStatusWorkflow.ITEM_RELEASED, 1, 18.0);
        OrderDeliveryTicket historicalTicket = rfqTicket(order, historical, 1, "/historical-drawing.pdf", "{\"snapshot\":\"historical\"}");
        ItemQuoteVersion supplemental = rfqItem(order, "SUPP-NEW", QuoteStatusWorkflow.PRICED, 2, 26.0);

        Map<String, Object> quoteResult = quoteProcessService.saveSupplementalQuoteSheet(order.getId());

        assertThat(quoteResult)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.RELEASED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);

        DeliveryBatchResultDTO releaseResult = deliveryService.generateRfqReleaseBatch(
                releaseRequest(order, supplemental, 2, "supplemental-append-" + System.nanoTime()));

        assertThat(releaseResult.isSuccess()).isTrue();
        assertThat(releaseResult.getTicketCount()).isEqualTo(1);
        OrderDeliveryTicket newTicket = ticketMapper.selectById(releaseResult.getTicketIds().get(0));
        assertThat(newTicket.getItemId()).isEqualTo(supplemental.getId());
        assertThat(newTicket.getQty()).isEqualTo(2);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.ITEM_RELEASED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        OrderDeliveryTicket reloadedHistoricalTicket = ticketMapper.selectById(historicalTicket.getId());
        assertThat(reloadedHistoricalTicket.getQty()).isEqualTo(1);
        assertThat(reloadedHistoricalTicket.getDrawingUrl()).isEqualTo("/historical-drawing.pdf");
        assertThat(reloadedHistoricalTicket.getProcessRoute()).isEqualTo(Map.of("snapshot", "historical"));
    }

    @Test
    void releasedRfqItemCanAppendAnotherReleaseBecauseRemainingQuantityIsOnlyDisplayReference() {
        OrderRfq order = releasedOrder("SUPP-REPEAT");
        ItemQuoteVersion released = rfqItem(order, "SUPP-REPEAT-PART", QuoteStatusWorkflow.ITEM_RELEASED, 1, 42.0);
        OrderDeliveryTicket firstTicket = rfqTicket(order, released, 1, "/repeat-original.pdf", "{\"snapshot\":\"repeat\"}");

        Map<String, Object> row = releaseRow(order, released);

        assertThat(row)
                .containsEntry("ticketedQty", 1)
                .containsEntry("remainingQty", 0)
                .containsEntry("canSelect", true)
                .containsEntry("releaseBlockedReason", "");

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(
                releaseRequest(order, released, 3, "released-repeat-" + System.nanoTime()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>()
                .eq("rfq_id", order.getId())
                .eq("item_id", released.getId()))).isEqualTo(2);
        OrderDeliveryTicket appendedTicket = ticketMapper.selectById(result.getTicketIds().get(0));
        assertThat(appendedTicket.getQty()).isEqualTo(3);
        assertThat(itemMapper.selectById(released.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.ITEM_RELEASED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        OrderDeliveryTicket unchangedFirstTicket = ticketMapper.selectById(firstTicket.getId());
        assertThat(unchangedFirstTicket.getQty()).isEqualTo(1);
        assertThat(unchangedFirstTicket.getDrawingUrl()).isEqualTo("/repeat-original.pdf");
        assertThat(unchangedFirstTicket.getProcessRoute()).isEqualTo(Map.of("snapshot", "repeat"));
    }

    private OrderRfq releasedOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(order);
        ensureCustomer(order.getCustomerName());
        return orderMapper.selectById(order.getId());
    }

    private ItemQuoteVersion rfqItem(OrderRfq order, String partNo, String status, int qty, double unitPrice) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo + "-" + System.nanoTime());
        item.setVersion("A");
        item.setDescription(partNo + " 描述");
        item.setQty(qty);
        item.setDrawingUrl("/supplemental/" + partNo + ".pdf");
        item.setLeadTime("5");
        item.setQuoteLeadTime("7");
        item.setTaxIncludedUnitPrice(unitPrice);
        item.setTaxIncludedTotalPrice(unitPrice * qty);
        item.setUntaxedUnitPrice(unitPrice / 1.13);
        item.setUntaxedTotalPrice(unitPrice / 1.13 * qty);
        item.setStatus(status);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private OrderDeliveryTicket rfqTicket(
            OrderRfq order,
            ItemQuoteVersion item,
            int qty,
            String drawingUrl,
            String processRoute) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setPartNo(item.getPartNo());
        ticket.setVersion(item.getVersion());
        ticket.setQty(qty);
        ticket.setDrawingUrl(drawingUrl);
        ticket.setProcessRoute(processRoute);
        ticketMapper.insert(ticket);
        return ticketMapper.selectById(ticket.getId());
    }

    private Map<String, Object> releaseRow(OrderRfq order, ItemQuoteVersion item) {
        ReleaseItemBatchQueryReq req = new ReleaseItemBatchQueryReq();
        req.setSources(List.of(ReleaseOrderSource.rfq(order.getId())));
        req.setPage(1);
        req.setPageSize(20);
        return internalTargetLeadTimeService.pageItemRowsBatch(req).getRecords().stream()
                .filter(row -> item.getId().equals(row.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private DeliveryGenerateReq releaseRequest(OrderRfq order, ItemQuoteVersion item, int acceptedQty, String batchKey) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setDocumentTypeCode("SO06");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 25));
        req.setCustomerOrderTitle("补图追加提单");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("SUPP-INTERNAL-" + item.getId());
        req.setCustomerOrderNo("SUPP-CUSTOMER-ORDER-" + item.getId());
        req.setCustomerProjectNo("SUPP-CUSTOMER-PROJECT");
        req.setProjectNo("SUPP-PROJECT");
        req.setArrangeType("计划");
        req.setReleaseBatchKey(batchKey);

        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId(order.getId().longValue());
        line.setSourceItemId(item.getId().longValue());
        line.setItemId(item.getId());
        line.setAcceptedQty(acceptedQty);
        req.setItems(List.of(line));
        return req;
    }
}
