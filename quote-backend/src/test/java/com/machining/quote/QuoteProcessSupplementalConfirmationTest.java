package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteProcessSupplementalConfirmationTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    void supplementalQuoteCandidateOrdersAndItemsIncludeQuotedAndReleasedOrdersWithPricedSupplementalRows() {
        String customerName = "补充报价候选客户-" + System.nanoTime();
        OrderRfq releasedOrder = createOrder(customerName, "SUP-QP-REL", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion supplemental = createQuoteItem(releasedOrder, "SUP-QP-PENDING", QuoteStatusWorkflow.PRICED, true, 18.0);
        createQuoteItem(releasedOrder, "SUP-QP-QUOTED", QuoteStatusWorkflow.QUOTED, true, 10.0);
        createQuoteItem(releasedOrder, "SUP-QP-RELEASED", QuoteStatusWorkflow.ITEM_RELEASED, true, 10.0);
        createQuoteItem(releasedOrder, "SUP-QP-NO-PRICE", QuoteStatusWorkflow.UNPRICED, true, null);

        OrderRfq quotedOrder = createOrder(customerName, "SUP-QP-QUOTED-ORDER", QuoteStatusWorkflow.QUOTED);
        ItemQuoteVersion quotedSupplemental = createQuoteItem(quotedOrder, "SUP-QP-QUOTED-P", QuoteStatusWorkflow.PRICED, true, 19.0);

        OrderRfq normalPricedOrder = createOrder(customerName, "SUP-QP-NORMAL", QuoteStatusWorkflow.PRICED);
        createQuoteItem(normalPricedOrder, "SUP-QP-NORMAL-P", QuoteStatusWorkflow.PRICED, true, 12.0);

        PageResult<OrderRfq> supplementalOrders = orderManagementService.listQuoteProcessSupplementalCandidateOrders(
                null, customerName, null, null, 1, 20);
        assertThat(supplementalOrders.getRecords()).extracting(OrderRfq::getId)
                .contains(releasedOrder.getId(), quotedOrder.getId())
                .doesNotContain(normalPricedOrder.getId());

        PageResult<OrderRfq> ordinaryOrders = orderManagementService.listQuoteProcessCandidateOrders(
                null, customerName, null, null, 1, 20);
        assertThat(ordinaryOrders.getRecords()).extracting(OrderRfq::getId)
                .contains(normalPricedOrder.getId())
                .doesNotContain(releasedOrder.getId());

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(releasedOrder.getId());
        req.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING_QUOTE);
        req.setPage(1);
        req.setPageSize(20);

        PageResult<Map<String, Object>> rows = quoteProcessService.listQuoteProcessItems(req);
        assertThat(rows.getRecords()).extracting(row -> row.get("id"))
                .containsExactly(supplemental.getId());

        req.setRfqId(quotedOrder.getId());
        PageResult<Map<String, Object>> quotedRows = quoteProcessService.listQuoteProcessItems(req);
        assertThat(quotedRows.getRecords()).extracting(row -> row.get("id"))
                .containsExactly(quotedSupplemental.getId());
    }

    @Test
    void supplementalQuoteSaveOnlyConfirmsSupplementalItemsRecalculatesReleasedOrderAndKeepsTickets() {
        String customerName = "补充报价保存客户-" + System.nanoTime();
        OrderRfq order = createOrder(customerName, "SUP-QP-SAVE", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion supplemental = createQuoteItem(order, "SUP-QP-SAVE-P", QuoteStatusWorkflow.PRICED, true, 21.0);
        ItemQuoteVersion quoted = createQuoteItem(order, "SUP-QP-SAVE-Q", QuoteStatusWorkflow.QUOTED, true, 11.0);
        quoted.setQuoteLeadTime("2026-05-01");
        itemMapper.updateById(quoted);
        OrderDeliveryTicket ticket = createTicket(order, quoted, 3);

        QuoteLeadTimeUpdateReq updateReq = new QuoteLeadTimeUpdateReq();
        updateReq.setRfqId(order.getId());
        updateReq.setItemId(supplemental.getId());
        updateReq.setQuoteLeadTime("2026.05.20");
        updateReq.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING_QUOTE);
        quoteProcessService.updateLeadTime(updateReq);

        Map<String, Object> result = quoteProcessService.saveSupplementalQuoteSheet(order.getId());

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("supplemental", true)
                .containsEntry("supplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.RELEASED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(supplemental.getId()).getQuoteLeadTime()).isEqualTo("2026-05-20");
        assertThat(itemMapper.selectById(quoted.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(quoted.getId()).getQuoteLeadTime()).isEqualTo("2026-05-01");
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>().eq("rfq_id", order.getId()))).isEqualTo(1L);
        OrderDeliveryTicket savedTicket = ticketMapper.selectById(ticket.getId());
        assertThat(savedTicket.getItemId()).isEqualTo(quoted.getId());
        assertThat(savedTicket.getQty()).isEqualTo(3);
    }

    @Test
    void supplementalQuoteSaveConfirmsAppendedPartsOnQuotedOrderWithoutChangingHeaderStatus() {
        String customerName = "补充报价已报价客户-" + System.nanoTime();
        OrderRfq order = createOrder(customerName, "SUP-QP-QUOTED-SAVE", QuoteStatusWorkflow.QUOTED);
        ItemQuoteVersion supplemental = createQuoteItem(order, "SUP-QP-QUOTED-SAVE-P", QuoteStatusWorkflow.PRICED, true, 22.0);
        supplemental.setQuoteLeadTime("2026-08-01");
        itemMapper.updateById(supplemental);

        Map<String, Object> result = quoteProcessService.saveSupplementalQuoteSheet(order.getId());

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("supplemental", true)
                .containsEntry("supplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.QUOTED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void supplementalQuoteLeadTimeCannotModifyHistoricalRows() {
        String customerName = "补充报价锁定客户-" + System.nanoTime();
        OrderRfq order = createOrder(customerName, "SUP-QP-LOCK", QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion historical = createQuoteItem(order, "SUP-QP-LOCK-Q", QuoteStatusWorkflow.QUOTED, true, 11.0);
        historical.setQuoteLeadTime("2026-06-01");
        itemMapper.updateById(historical);

        QuoteLeadTimeUpdateReq updateReq = new QuoteLeadTimeUpdateReq();
        updateReq.setRfqId(order.getId());
        updateReq.setItemId(historical.getId());
        updateReq.setQuoteLeadTime("2026.06.20");
        updateReq.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING_QUOTE);

        assertThatThrownBy(() -> quoteProcessService.updateLeadTime(updateReq))
                .hasMessageContaining("补充报价待确认范围");
        assertThat(itemMapper.selectById(historical.getId()).getQuoteLeadTime()).isEqualTo("2026-06-01");
    }

    @Test
    void ordinaryQuoteSheetSaveBehaviorStaysUnchanged() {
        String customerName = "普通报价保存客户-" + System.nanoTime();
        OrderRfq order = createOrder(customerName, "SUP-QP-ORD", QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion item = createQuoteItem(order, "SUP-QP-ORD-P", QuoteStatusWorkflow.PRICED, true, 15.0);
        item.setQuoteLeadTime("2026-07-01");
        itemMapper.updateById(item);

        Map<String, Object> result = quoteProcessService.saveQuoteSheet(order.getId());

        assertThat(result).containsEntry("success", true);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(item.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void controllerContractsExposeSupplementalQuoteEndpoints() throws Exception {
        String marketController = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "controller", "MarketController.java"),
                StandardCharsets.UTF_8);
        String quoteProcessController = Files.readString(
                Path.of("src", "main", "java", "com", "machining", "quote", "controller", "QuoteProcessController.java"),
                StandardCharsets.UTF_8);

        assertThat(marketController)
                .contains("@GetMapping(\"/quote-process/supplemental-drawing/orders\")")
                .contains("@RequirePermission(\"market:quote-process:order-select\")")
                .contains("listQuoteProcessSupplementalCandidateOrders");
        assertThat(quoteProcessController)
                .contains("@RequestParam(value = \"scope\", required = false) String scope")
                .contains("@PostMapping(\"/{rfqId}/save-supplemental-quote\")")
                .contains("quoteProcessService.saveSupplementalQuoteSheet(rfqId)");
    }

    private OrderRfq createOrder(String customerName, String prefix, String status) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(customerName);
        order.setStatus(status);
        orderMapper.insert(order);
        return orderMapper.selectById(order.getId());
    }

    private ItemQuoteVersion createQuoteItem(OrderRfq order, String partNo, String status, boolean hasDrawing, Double price) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo + "-" + System.nanoTime());
        item.setVersion("A");
        item.setDescription(partNo + " desc");
        item.setQty(1);
        item.setUnit("pcs");
        item.setDrawingUrl(hasDrawing ? "/uploads/test/" + partNo + ".pdf" : null);
        item.setStatus(status);
        item.setTaxIncludedUnitPrice(price);
        item.setTaxIncludedTotalPrice(price);
        item.setUntaxedUnitPrice(price);
        item.setUntaxedTotalPrice(price);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private OrderDeliveryTicket createTicket(OrderRfq order, ItemQuoteVersion item, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setSourceType("RFQ");
        ticket.setSourceOrderId(order.getId().longValue());
        ticket.setSourceItemId(item.getId().longValue());
        ticket.setPartNo(item.getPartNo());
        ticket.setVersion(item.getVersion());
        ticket.setQty(qty);
        ticketMapper.insert(ticket);
        return ticketMapper.selectById(ticket.getId());
    }
}
