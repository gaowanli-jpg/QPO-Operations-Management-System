package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-status-stage2/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-status-stage2/backups/"
})
@Transactional
class QuoteStatusWorkflowStage2Test {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private SystemTaskMapper taskMapper;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private QuoteProcessService quoteProcessService;
    @Autowired
    private ExcelExportService excelExportService;
    @Autowired
    private DeliveryService deliveryService;

    @Test
    void engineeringCompletionThenMarketSaveQuoteControlsQuoteExportAndLeadTimeEditing() throws Exception {
        OrderRfq order = createOrder(QuoteStatusWorkflow.UNPRICED);
        ItemQuoteVersion item = createQuotedItem(order, "S2-A", "2026-03-20");

        engineerService.completeOrderQuote(order.getId());
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(quoteProcessService.getQuoteProcess(order.getId()).get("orderInfo")).isInstanceOf(OrderRfq.class);

        MockHttpServletResponse pricedExportResponse = new MockHttpServletResponse();
        assertThatThrownBy(() -> excelExportService.exportSimpleQuoteSheet(order.getId(), pricedExportResponse))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("保存报价单");
        assertThatThrownBy(() -> deliveryService.generateTickets(deliveryReq(order, item)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("保存报价单");

        updateQuoteLeadTime(order.getId(), item.getId(), "2026-04-01");
        quoteProcessService.saveQuoteSheet(order.getId());
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);

        assertThatThrownBy(() -> updateQuoteLeadTime(order.getId(), item.getId(), "2026-05-01"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已报价");
        assertThatThrownBy(() -> quoteProcessService.saveQuoteSheet(order.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已保存报价单");

        MockHttpServletResponse quotedExportResponse = new MockHttpServletResponse();
        excelExportService.exportSimpleQuoteSheet(order.getId(), quotedExportResponse);
        assertThat(quotedExportResponse.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void saveQuoteSheetRequiresPricedOrderAndCompleteQuoteLeadTimes() {
        OrderRfq unpricedOrder = createOrder(QuoteStatusWorkflow.UNPRICED);
        createQuotedItem(unpricedOrder, "S2-B", "2026-03-20");
        assertThatThrownBy(() -> quoteProcessService.saveQuoteSheet(unpricedOrder.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("尚未完成核价");

        OrderRfq pricedOrder = createOrder(QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion missingLeadTimeItem = createQuotedItem(pricedOrder, "S2-C", null);
        missingLeadTimeItem.setLeadTime(null);
        itemMapper.updateById(missingLeadTimeItem);
        assertThatThrownBy(() -> quoteProcessService.saveQuoteSheet(pricedOrder.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("报价交期");
    }

    @Test
    void activeRefreshTaskBlocksEngineeringCompletion() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.UNPRICED);
        createQuotedItem(order, "S2-D", "2026-03-20");
        createRefreshTask(order.getId(), "PENDING");

        assertThatThrownBy(() -> engineerService.completeOrderQuote(order.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("刷新核价任务");
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
    }

    @Test
    void activeRefreshTaskBlocksMarketSaveQuote() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.PRICED);
        createQuotedItem(order, "S2-E", "2026-03-20");
        createRefreshTask(order.getId(), "RUNNING");

        assertThatThrownBy(() -> quoteProcessService.saveQuoteSheet(order.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("刷新核价任务");
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    void refreshOrderQuotesRejectsOrdersAlreadyAdvancedPastEngineering() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.PRICED);
        createQuotedItem(order, "S2-F", "2026-03-20");

        assertThatThrownBy(() -> engineerService.refreshOrderQuotes(order.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("未核价");
    }

    private void updateQuoteLeadTime(Integer rfqId, Integer itemId, String leadTime) {
        QuoteLeadTimeUpdateReq req = new QuoteLeadTimeUpdateReq();
        req.setRfqId(rfqId);
        req.setItemId(itemId);
        req.setQuoteLeadTime(leadTime);
        quoteProcessService.updateLeadTime(req);
    }

    private DeliveryGenerateReq deliveryReq(OrderRfq order, ItemQuoteVersion item) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setInternalProjectNo("IPN-" + order.getId());
        req.setCustomerOrderNo("CO-" + order.getId());
        req.setCustomerProjectNo("CP-" + order.getId());
        req.setStartDate(LocalDate.of(2026, 4, 1));
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setAcceptedQty(1);
        req.setItems(List.of(line));
        return req;
    }

    private void createRefreshTask(Integer rfqId, String status) {
        SystemTask task = new SystemTask();
        task.setTaskType("REFRESH_ORDER_QUOTES");
        task.setStatus(status);
        task.setProgress(0);
        task.setTotal(1);
        task.setRequestJson(Map.of("rfqId", rfqId));
        taskMapper.insert(task);
    }

    private OrderRfq createOrder(String status) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("S2-" + System.nanoTime());
        order.setCustomerName("Stage2客户");
        order.setStatus(status);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion createQuotedItem(OrderRfq order, String partNo, String quoteLeadTime) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + " desc");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setLeadTime(quoteLeadTime);
        item.setQuoteLeadTime(quoteLeadTime);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setFinalUnitPrice(10.0);
        item.setFinalTotalPrice(10.0);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }
}
