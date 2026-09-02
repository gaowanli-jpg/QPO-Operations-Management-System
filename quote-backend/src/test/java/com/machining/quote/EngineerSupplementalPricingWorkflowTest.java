package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.OrderItemPageService;
import com.machining.quote.service.OrderManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/engineer-supplemental-pricing/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/engineer-supplemental-pricing/backups/"
})
@Transactional
class EngineerSupplementalPricingWorkflowTest {
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private OrderItemPageService orderItemPageService;
    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialPriceSpecMapper materialPriceSpecMapper;

    @Test
    void supplementalPricingOrdersAndItemsIncludePricedQuotedAndReleasedOrdersWithDrawingAndMissingPrice() {
        OrderRfq releasedOrder = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-RELEASED");
        ItemQuoteVersion pending = createItem(releasedOrder, "SUP-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);
        createItem(releasedOrder, "SUP-QUOTED", QuoteStatusWorkflow.QUOTED, true, 10.0);
        createItem(releasedOrder, "SUP-ITEM-RELEASED", QuoteStatusWorkflow.ITEM_RELEASED, true, 10.0);
        createItem(releasedOrder, "SUP-NO-DRAWING", QuoteStatusWorkflow.NO_DRAWING, false, null);

        OrderRfq pricedOrder = createOrder(QuoteStatusWorkflow.PRICED, "SUP-PRICED");
        createItem(pricedOrder, "SUP-PRICED-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);

        OrderRfq quotedOrder = createOrder(QuoteStatusWorkflow.QUOTED, "SUP-QUOTED-ORDER");
        createItem(quotedOrder, "SUP-QUOTED-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);

        OrderRfq normalUnpricedOrder = createOrder(QuoteStatusWorkflow.UNPRICED, "SUP-NORMAL");
        createItem(normalUnpricedOrder, "SUP-NORMAL-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);

        PageResult<OrderRfq> orders = engineerService.listSupplementalPricingOrders(null, null, null, 1, 20);

        assertThat(orders.getRecords()).extracting(OrderRfq::getId)
                .contains(releasedOrder.getId())
                .contains(pricedOrder.getId())
                .contains(quotedOrder.getId())
                .doesNotContain(normalUnpricedOrder.getId());

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(releasedOrder.getId());
        req.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING);
        req.setPage(1);
        req.setPageSize(20);
        OrderItemPageDTO items = orderItemPageService.pageItems(req);

        assertThat(items.getRecords()).extracting(ItemQuoteVersion::getId)
                .containsExactly(pending.getId());
    }

    @Test
    void supplementalPricingCompletionReusesItemSaveAndKeepsReleasedOrderStatus() {
        OrderRfq releasedOrder = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-COMPLETE");
        ItemQuoteVersion pending = createItem(releasedOrder, "SUP-COMPLETE-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);

        assertThatThrownBy(() -> engineerService.completeSupplementalDrawingQuote(releasedOrder.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("尚未完成补核价");

        engineerService.calculateAndSaveQuote(normalQuoteReq(pending));

        ItemQuoteVersion saved = itemMapper.selectById(pending.getId());
        assertThat(saved.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(saved.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(orderMapper.selectById(releasedOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        Map<String, Object> result = engineerService.completeSupplementalDrawingQuote(releasedOrder.getId());

        assertThat(result)
                .containsEntry("supplemental", true)
                .containsEntry("pricedSupplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.RELEASED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.RELEASED);
        assertThat(orderMapper.selectById(releasedOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThatThrownBy(() -> engineerService.completeOrderQuote(releasedOrder.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void supplementalPricingCompletionAllowsQuotedOrdersForAppendedPartsWithoutChangingHeaderStatus() {
        OrderRfq quotedOrder = createOrder(QuoteStatusWorkflow.QUOTED, "SUP-QUOTED-COMPLETE");
        ItemQuoteVersion pending = createItem(quotedOrder, "SUP-QUOTED-COMPLETE-P", QuoteStatusWorkflow.UNPRICED, true, null);

        engineerService.calculateAndSaveQuote(normalQuoteReq(pending));

        Map<String, Object> result = engineerService.completeSupplementalDrawingQuote(quotedOrder.getId());

        assertThat(result)
                .containsEntry("supplemental", true)
                .containsEntry("pricedSupplementalItemCount", 1)
                .containsEntry("orderStatusBefore", QuoteStatusWorkflow.QUOTED)
                .containsEntry("orderStatusAfter", QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(quotedOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(itemMapper.selectById(pending.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    void ordinaryQuoteCompletionIgnoresDiscardedItemsBecauseDiscardMeansUnused() {
        OrderRfq unpricedOrder = createOrder(QuoteStatusWorkflow.UNPRICED, "SUP-DISCARDED-IGNORED");
        createItem(unpricedOrder, "SUP-DISCARDED-PRICED", QuoteStatusWorkflow.PRICED, true, 10.0);
        ItemQuoteVersion discarded = createItem(unpricedOrder, "SUP-DISCARDED-PENDING", QuoteStatusWorkflow.DISCARDED, true, null);

        engineerService.completeOrderQuote(unpricedOrder.getId());

        assertThat(orderMapper.selectById(unpricedOrder.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(itemMapper.selectById(discarded.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
    }

    @Test
    void ordinaryPricingWorkspaceHidesDiscardedItemsAndRejectsSavingThem() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.UNPRICED, "SUP-DISCARDED-PRICING");
        ItemQuoteVersion active = createItem(order, "SUP-DISCARDED-ACTIVE", QuoteStatusWorkflow.UNPRICED, true, null);
        ItemQuoteVersion discarded = createItem(order, "SUP-DISCARDED-HIDDEN", QuoteStatusWorkflow.DISCARDED, true, null);

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(order.getId());
        req.setPage(1);
        req.setPageSize(20);
        OrderItemPageDTO page = orderItemPageService.pageItems(req);

        assertThat(page.getRecords()).extracting(ItemQuoteVersion::getId)
                .contains(active.getId())
                .doesNotContain(discarded.getId());
        assertThatThrownBy(() -> engineerService.calculateAndSaveQuote(normalQuoteReq(discarded)))
                .hasMessageContaining("废弃");
        assertThat(itemMapper.selectById(discarded.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
    }

    @Test
    void restoredUnpricedDiscardedItemClearsStalePricingAndEntersSupplementalPricing() {
        OrderRfq order = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-RESTORE-REPRICE");
        ItemQuoteVersion discarded = createItem(order, "SUP-RESTORE-REPRICE-P", QuoteStatusWorkflow.DISCARDED, true, 25.0);
        discarded.setMaterialData(Map.of("materialCode", "OLD-MAT"));
        discarded.setProcessRoute(List.of(Map.of("processName", "old-process")));
        discarded.setFeeData(Map.of("oldFee", 1));
        discarded.setBaseCost(20.0);
        discarded.setEngineeringMaterialCode("OLD-MAT");
        discarded.setEngineeringMaterialName("Old material");
        discarded.setEngineeringMaterialTypeCode("OLD-TYPE");
        discarded.setEngineeringMaterialTypeName("Old type");
        discarded.setEngineeringMaterialCategory("Old category");
        discarded.setMaterialShape("old-shape");
        discarded.setMaterialGrossWeight(2.5);
        discarded.setPartNetWeight(1.5);
        discarded.setQuoteMode("NORMAL");
        discarded.setAssemblyFee(3.0);
        discarded.setRepeatMatchEligible(true);
        discarded.setPreviousStatusBeforeDiscard(QuoteStatusWorkflow.UNPRICED);
        itemMapper.updateById(discarded);

        orderManagementService.restoreOrderItem(order.getId(), discarded.getId());

        ItemQuoteVersion restored = itemMapper.selectById(discarded.getId());
        assertThat(restored.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(restored.getPreviousStatusBeforeDiscard()).isNull();
        assertThat(restored.getMaterialData()).isNull();
        assertThat(restored.getProcessRoute()).isNull();
        assertThat(restored.getFeeData()).isNull();
        assertThat(restored.getBaseCost()).isNull();
        assertThat(restored.getEngineeringMaterialCode()).isNull();
        assertThat(restored.getEngineeringMaterialName()).isNull();
        assertThat(restored.getEngineeringMaterialTypeCode()).isNull();
        assertThat(restored.getEngineeringMaterialTypeName()).isNull();
        assertThat(restored.getEngineeringMaterialCategory()).isNull();
        assertThat(restored.getMaterialShape()).isNull();
        assertThat(restored.getMaterialGrossWeight()).isNull();
        assertThat(restored.getPartNetWeight()).isNull();
        assertThat(restored.getQuoteMode()).isNull();
        assertThat(restored.getAssemblyFee()).isNull();
        assertThat(restored.getRepeatMatchEligible()).isFalse();
        assertThat(restored.getTaxIncludedUnitPrice()).isNull();
        assertThat(restored.getTaxIncludedTotalPrice()).isNull();
        assertThat(restored.getUntaxedUnitPrice()).isNull();
        assertThat(restored.getUntaxedTotalPrice()).isNull();

        PageResult<OrderRfq> orders = engineerService.listSupplementalPricingOrders(null, null, null, 1, 20);
        assertThat(orders.getRecords()).extracting(OrderRfq::getId)
                .contains(order.getId());

        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(order.getId());
        req.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING);
        req.setPage(1);
        req.setPageSize(20);
        OrderItemPageDTO items = orderItemPageService.pageItems(req);

        assertThat(items.getRecords()).extracting(ItemQuoteVersion::getId)
                .containsExactly(restored.getId());
    }

    @Test
    void supplementalRepeatPricedItemStaysPendingUntilSupplementalCompletion() {
        OrderRfq historicalOrder = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-REPEAT-HIST");
        ItemQuoteVersion historicalItem = createItem(historicalOrder, "SUP-REPEAT-SOURCE", QuoteStatusWorkflow.ITEM_RELEASED, true, 20.0);
        DictMaterial material = ensureMaterial();
        historicalItem.setMaterialName(material.getMaterialName());
        historicalItem.setMaterialData(Map.of(
                "materialCode", material.getMaterialCode(),
                "priceSpecCode", "SPEC_1",
                "materialName", material.getMaterialName(),
                "shape", "sheet",
                "weight", 2.0,
                "cost", 10.0));
        historicalItem.setProcessRoute(List.of(Map.of(
                "routeType", "FEE",
                "sourceType", "extra",
                "processName", "repeat-test-fee",
                "calcType", "extra-fee",
                "inputVal", 1.0,
                "lineCost", 1.0,
                "sortNo", 10)));
        historicalItem.setPartNetWeight(1.0);
        itemMapper.updateById(historicalItem);

        OrderRfq currentOrder = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-REPEAT-CUR");
        ItemQuoteVersion currentItem = createItem(currentOrder, "SUP-REPEAT-CURRENT", QuoteStatusWorkflow.UNPRICED, true, null);
        currentItem.setPartNo(historicalItem.getPartNo());
        currentItem.setVersion(historicalItem.getVersion());
        itemMapper.updateById(currentItem);

        Map<String, Object> applyResult = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion repeatPriced = itemMapper.selectById(currentItem.getId());
        assertThat(applyResult.get("appliedCount")).isEqualTo(1);
        assertThat(repeatPriced.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(repeatPriced.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(engineerService.listSupplementalPricingOrders(null, null, null, 1, 20).getRecords())
                .extracting(OrderRfq::getId)
                .contains(currentOrder.getId());

        OrderItemPageReq pendingReq = new OrderItemPageReq();
        pendingReq.setRfqId(currentOrder.getId());
        pendingReq.setScope(OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING);
        pendingReq.setPage(1);
        pendingReq.setPageSize(20);
        assertThat(orderItemPageService.pageItems(pendingReq).getRecords())
                .extracting(ItemQuoteVersion::getId)
                .containsExactly(currentItem.getId());

        Map<String, Object> completeResult = engineerService.completeSupplementalDrawingQuote(currentOrder.getId());

        assertThat(completeResult).containsEntry("pricedSupplementalItemCount", 1);
        assertThat(itemMapper.selectById(currentItem.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(orderItemPageService.pageItems(pendingReq).getRecords())
                .extracting(ItemQuoteVersion::getId)
                .doesNotContain(currentItem.getId());
    }

    private OrderRfq createOrder(String status, String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName("Supplemental pricing customer");
        order.setStatus(status);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion createItem(OrderRfq order, String partNo, String status, boolean hasDrawing, Double price) {
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
        item.setTaxIncludedTotalPrice(price == null ? null : price);
        item.setUntaxedUnitPrice(price == null ? null : price);
        item.setUntaxedTotalPrice(price == null ? null : price);
        itemMapper.insert(item);
        return itemMapper.selectById(item.getId());
    }

    private QuoteSubmitReq normalQuoteReq(ItemQuoteVersion item) {
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setPartNetWeight(1.0);
        DictMaterial material = ensureMaterial();
        req.setMaterialData(Map.of(
                "materialCode", material.getMaterialCode(),
                "priceSpecCode", "SPEC_1",
                "materialName", material.getMaterialName(),
                "shape", "板材",
                "weight", 2.0));
        req.setProcessRoute(List.of());
        return req;
    }

    private DictMaterial ensureMaterial() {
        DictMaterial existing = materialMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictMaterial>()
                .eq("material_code", "MAT-SUPPLEMENTAL-PRICING"));
        if (existing != null) {
            ensureMaterialPriceSpecs(existing.getId(), 5.0);
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-SUPPLEMENTAL-PRICING");
        material.setMaterialName("Supplemental pricing test material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("钢铁");
        material.setDensity(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        ensureMaterialPriceSpecs(material.getId(), 5.0);
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
}
