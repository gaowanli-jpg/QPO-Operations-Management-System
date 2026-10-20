package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
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
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialPriceSpecMapper materialPriceSpecMapper;
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
    void itemAndAssemblyQuoteSavesPricedStatusUntilMarketSavesQuoteSheet() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.UNPRICED);
        ItemQuoteVersion normal = createUnpricedItem(order, "S2-STATE-NORMAL");
        ItemQuoteVersion child = createQuotedItem(order, "S2-STATE-CHILD", "2026-03-20");
        child.setStatus(QuoteStatusWorkflow.PRICED);
        itemMapper.updateById(child);
        ItemQuoteVersion assembly = createUnpricedItem(order, "S2-STATE-ASM");
        assembly.setQuoteMode("ASSEMBLY");
        itemMapper.updateById(assembly);

        engineerService.calculateAndSaveQuote(normalQuoteReq(normal));
        assertThat(itemMapper.selectById(normal.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);

        QuoteSubmitReq assemblyReq = new QuoteSubmitReq();
        assemblyReq.setItemId(assembly.getId());
        assemblyReq.setQuoteMode("ASSEMBLY");
        assemblyReq.setAssemblyFee(5.0);
        QuoteSubmitReq.AssemblyChildDTO childLine = new QuoteSubmitReq.AssemblyChildDTO();
        childLine.setChildItemId(child.getId());
        childLine.setQtyPerParent(1.0);
        assemblyReq.setAssemblyChildren(List.of(childLine));
        engineerService.calculateAndSaveQuote(assemblyReq);
        assertThat(itemMapper.selectById(assembly.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);

        engineerService.completeOrderQuote(order.getId());
        quoteProcessService.saveQuoteSheet(order.getId());

        assertThat(itemMapper.selectById(normal.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(child.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(assembly.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

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
        MockHttpServletResponse pricingSheetResponse = new MockHttpServletResponse();
        excelExportService.exportPricingSheet(order.getId(), pricingSheetResponse);
        assertThat(pricingSheetResponse.getContentAsByteArray()).isNotEmpty();
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
        assertThatThrownBy(() -> excelExportService.exportPricingSheet(order.getId(), new MockHttpServletResponse()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已核价");
    }

    @Test
    void saveQuoteSheetRequiresPricedOrderAndAllowsMissingLeadTimes() {
        OrderRfq unpricedOrder = createOrder(QuoteStatusWorkflow.UNPRICED);
        createQuotedItem(unpricedOrder, "S2-B", "2026-03-20");
        assertThatThrownBy(() -> quoteProcessService.saveQuoteSheet(unpricedOrder.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("尚未完成核价");

        OrderRfq pricedOrder = createOrder(QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion missingLeadTimeItem = createQuotedItem(pricedOrder, "S2-C", null);
        missingLeadTimeItem.setLeadTime(null);
        itemMapper.updateById(missingLeadTimeItem);
        Map<String, Object> result = quoteProcessService.saveQuoteSheet(pricedOrder.getId());

        assertThat(result).containsEntry("success", true);
        assertThat(orderMapper.selectById(pricedOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(missingLeadTimeItem.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void saveQuoteSheetIgnoresDiscardedItemsWhenCheckingQuoteLeadTimes() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion activeItem = createQuotedItem(order, "S2-DISCARD-ACTIVE", "2026-03-20");
        activeItem.setStatus(QuoteStatusWorkflow.PRICED);
        itemMapper.updateById(activeItem);

        ItemQuoteVersion discardedItem = createQuotedItem(order, "S2-DISCARD-MISSING", null);
        discardedItem.setStatus(QuoteStatusWorkflow.DISCARDED);
        discardedItem.setLeadTime(null);
        discardedItem.setQuoteLeadTime(null);
        discardedItem.setPreviousStatusBeforeDiscard(QuoteStatusWorkflow.PRICED);
        itemMapper.updateById(discardedItem);

        quoteProcessService.saveQuoteSheet(order.getId());

        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(activeItem.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(discardedItem.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
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
        req.setRfqIds(List.of(order.getId()));
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IPN-" + order.getId());
        req.setCustomerOrderNo("CO-" + order.getId());
        req.setCustomerProjectNo("CP-" + order.getId());
        req.setSalesOrderDate(LocalDate.of(2026, 4, 1));
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

    private ItemQuoteVersion createUnpricedItem(OrderRfq order, String partNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + " desc");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-03-20");
        item.setStatus(QuoteStatusWorkflow.UNPRICED);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private QuoteSubmitReq normalQuoteReq(ItemQuoteVersion item) {
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setPartNetWeight(1.0);
        DictMaterial material = ensureStatusMaterial();
        req.setMaterialData(Map.of(
                "materialCode", material.getMaterialCode(),
                "priceSpecCode", "SPEC_1",
                "shape", "\u677f\u6750",
                "weight", 10.0));
        req.setProcessRoute(List.of());
        return req;
    }

    private DictMaterial ensureStatusMaterial() {
        DictMaterial existing = materialMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictMaterial>()
                .eq("material_code", "MAT-STATUS-WORKFLOW"));
        if (existing != null) {
            ensureMaterialPriceSpecs(existing.getId(), 1.0);
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-STATUS-WORKFLOW");
        material.setMaterialName("Status workflow test material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setDensity(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        ensureMaterialPriceSpecs(material.getId(), 1.0);
        return material;
    }

    private void ensureMaterialPriceSpecs(Long materialId, double unifiedPrice) {
        Long count = materialPriceSpecMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictMaterialPriceSpec>()
                .eq("material_id", materialId));
        if (count != null && count > 0) {
            return;
        }
        materialPriceSpecMapper.insert(priceSpec(materialId, "SPEC_1", "规格一", unifiedPrice, 10));
        materialPriceSpecMapper.insert(priceSpec(materialId, "SPEC_2", "规格二", null, 20));
        materialPriceSpecMapper.insert(priceSpec(materialId, "SPEC_3", "规格三", null, 30));
    }

    private DictMaterialPriceSpec priceSpec(Long materialId, String code, String name, Double unifiedPrice, int sortNo) {
        DictMaterialPriceSpec spec = new DictMaterialPriceSpec();
        spec.setMaterialId(materialId);
        spec.setSpecCode(code);
        spec.setSpecName(name);
        spec.setUnifiedPrice(unifiedPrice);
        spec.setSortNo(sortNo);
        return spec;
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
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(10.0);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }
}
