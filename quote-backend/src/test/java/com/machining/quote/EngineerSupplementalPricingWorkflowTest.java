package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.OrderItemPageService;
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
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private DictMaterialMapper materialMapper;

    @Test
    void supplementalPricingOrdersAndItemsOnlyIncludeReleasedOrdersWithDrawingAndMissingPrice() {
        OrderRfq releasedOrder = createOrder(QuoteStatusWorkflow.RELEASED, "SUP-RELEASED");
        ItemQuoteVersion pending = createItem(releasedOrder, "SUP-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);
        createItem(releasedOrder, "SUP-QUOTED", QuoteStatusWorkflow.QUOTED, true, 10.0);
        createItem(releasedOrder, "SUP-ITEM-RELEASED", QuoteStatusWorkflow.ITEM_RELEASED, true, 10.0);
        createItem(releasedOrder, "SUP-NO-DRAWING", QuoteStatusWorkflow.NO_DRAWING, false, null);

        OrderRfq normalUnpricedOrder = createOrder(QuoteStatusWorkflow.UNPRICED, "SUP-NORMAL");
        createItem(normalUnpricedOrder, "SUP-NORMAL-PENDING", QuoteStatusWorkflow.UNPRICED, true, null);

        PageResult<OrderRfq> orders = engineerService.listSupplementalPricingOrders(null, null, null, 1, 20);

        assertThat(orders.getRecords()).extracting(OrderRfq::getId)
                .contains(releasedOrder.getId())
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
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-SUPPLEMENTAL-PRICING");
        material.setMaterialName("Supplemental pricing test material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("钢铁");
        material.setDensity(1.0);
        material.setPlatePrice(5.0);
        material.setBarPrice(5.0);
        material.setTubePrice(5.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        return material;
    }
}
