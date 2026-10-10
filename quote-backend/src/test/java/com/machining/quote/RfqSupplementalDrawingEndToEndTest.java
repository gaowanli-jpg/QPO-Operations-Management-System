package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.storage.FileStorageConfig;
import com.machining.quote.storage.FileStorageConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RfqSupplementalDrawingEndToEndTest extends CoreWorkflowIntegrationTestSupport {
    @MockBean
    private FileStorageConfigService fileStorageConfigService;

    @BeforeEach
    void configureIsolatedLocalStorage() {
        String storageRoot = Path.of(
                System.getProperty("java.io.tmpdir"),
                "qpo-rfq-supplemental-drawing-e2e",
                String.valueOf(System.nanoTime())).toString();
        when(fileStorageConfigService.activeConfig())
                .thenReturn(FileStorageConfig.local(null, "E2E local storage", storageRoot, null));
    }

    @Test
    void noDrawingSupplementalItemFlowsThroughPricingQuoteConfirmationAndAppendRelease() {
        String customerName = "补图闭环客户-" + System.nanoTime();
        OrderRfq order = releasedOrder(customerName, "SUP-E2E");
        ItemQuoteVersion historical = quotedReleasedItem(order, "SUP-E2E-HISTORY", 1, 16.0);
        OrderDeliveryTicket historicalTicket = ticket(order, historical, 1, "/historical/e2e.pdf", Map.of("snapshot", "historical"));
        ItemQuoteVersion supplemental = noDrawingItem(order, "SUP-E2E-NEW", 1);

        Map<String, Object> supplementResult = orderManagementService.supplementDrawings(
                order.getId(),
                List.of(drawing(supplemental.getPartNo() + ".pdf")));

        assertThat(supplementResult)
                .containsEntry("success", true)
                .containsEntry("boundCount", 1)
                .containsEntry("restoredUnpricedCount", 1);
        supplemental = itemMapper.selectById(supplemental.getId());
        assertThat(supplemental.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(supplemental.getDrawingUrl()).isNotBlank();
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        engineerService.calculateAndSaveQuote(normalQuoteReq(supplemental));
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(itemMapper.selectById(supplemental.getId()).getTaxIncludedUnitPrice()).isNotNull();

        Map<String, Object> supplementalPricingComplete = engineerService.completeSupplementalDrawingQuote(order.getId());
        assertThat(supplementalPricingComplete)
                .containsEntry("supplemental", true)
                .containsEntry("pricedSupplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.RELEASED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.RELEASED);
        assertThat(releaseRow(order, supplemental)).containsEntry("canSelect", false)
                .containsEntry("releaseBlockedReason", "补充报价尚未确认");

        supplemental = itemMapper.selectById(supplemental.getId());
        supplemental.setQuoteLeadTime("2026-09-01");
        itemMapper.updateById(supplemental);

        Map<String, Object> supplementalQuoteResult = quoteProcessService.saveSupplementalQuoteSheet(order.getId());
        assertThat(supplementalQuoteResult)
                .containsEntry("success", true)
                .containsEntry("supplemental", true)
                .containsEntry("supplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.RELEASED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(releaseRow(order, supplemental)).containsEntry("canSelect", true)
                .containsEntry("releaseBlockedReason", "");

        DeliveryBatchResultDTO releaseResult = deliveryService.generateRfqReleaseBatch(
                releaseRequest(order, supplemental, 1, "supp-e2e-" + System.nanoTime()));

        assertThat(releaseResult.isSuccess()).isTrue();
        assertThat(releaseResult.getTicketCount()).isEqualTo(1);
        OrderDeliveryTicket newTicket = ticketMapper.selectById(releaseResult.getTicketIds().get(0));
        assertThat(newTicket.getItemId()).isEqualTo(supplemental.getId());
        assertThat(newTicket.getQty()).isEqualTo(1);
        assertThat(itemMapper.selectById(supplemental.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.ITEM_RELEASED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThat(ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>().eq("rfq_id", order.getId()))).isEqualTo(2L);

        OrderDeliveryTicket unchangedHistoricalTicket = ticketMapper.selectById(historicalTicket.getId());
        assertThat(unchangedHistoricalTicket.getItemId()).isEqualTo(historical.getId());
        assertThat(unchangedHistoricalTicket.getQty()).isEqualTo(1);
        assertThat(unchangedHistoricalTicket.getDrawingUrl()).isEqualTo("/historical/e2e.pdf");
        assertThat(unchangedHistoricalTicket.getProcessRoute()).isEqualTo(Map.of("snapshot", "historical"));
    }

    private OrderRfq releasedOrder(String customerName, String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.insert(order);
        ensureCustomer(customerName);
        return orderMapper.selectById(order.getId());
    }

    private ItemQuoteVersion quotedReleasedItem(OrderRfq order, String partNo, int qty, double unitPrice) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo + "-" + System.nanoTime());
        item.setVersion("A");
        item.setDescription(partNo + " 历史已提单零件");
        item.setQty(qty);
        item.setUnit("pcs");
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        item.setTaxIncludedUnitPrice(unitPrice);
        item.setTaxIncludedTotalPrice(unitPrice * qty);
        item.setUntaxedUnitPrice(unitPrice / 1.13);
        item.setUntaxedTotalPrice(unitPrice / 1.13 * qty);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private ItemQuoteVersion noDrawingItem(OrderRfq order, String partNo, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo + "-" + System.nanoTime());
        item.setVersion("A");
        item.setDescription(partNo + " 后补图零件");
        item.setQty(qty);
        item.setUnit("pcs");
        item.setStatus(QuoteStatusWorkflow.NO_DRAWING);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private QuoteSubmitReq normalQuoteReq(ItemQuoteVersion item) {
        DictMaterial material = ensureRepeatMaterial();
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setPartNetWeight(1.0);
        req.setMaterialData(standardMaterialData(material, 4.0));
        req.setProcessRoute(List.of());
        return req;
    }

    private OrderDeliveryTicket ticket(OrderRfq order, ItemQuoteVersion item, int qty, String drawingUrl, Object processRoute) {
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
        req.setCustomerOrderTitle("补图闭环追加提单");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("SUP-E2E-INTERNAL-" + item.getId());
        req.setCustomerOrderNo("SUP-E2E-CUSTOMER-ORDER-" + item.getId());
        req.setCustomerProjectNo("SUP-E2E-CUSTOMER-PROJECT");
        req.setProjectNo("SUP-E2E-PROJECT");
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

    private MockMultipartFile drawing(String fileName) {
        return new MockMultipartFile(
                "drawings",
                fileName,
                "application/pdf",
                ("%PDF-1.4 " + fileName).getBytes(StandardCharsets.UTF_8)
        );
    }
}
