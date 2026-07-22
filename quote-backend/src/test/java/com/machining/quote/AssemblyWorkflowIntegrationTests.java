package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMarkupFee;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
@Transactional
class AssemblyWorkflowIntegrationTests {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper assemblyRelationMapper;
    @Autowired
    private DictMarkupFeeMapper markupFeeMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private DeliveryService deliveryService;

    @Test
    @SuppressWarnings("unchecked")
    void assemblyQuoteAddsMarkupFeesToAssemblyFeeExceptTransport() {
        resetMarkupFeesForAssemblyTest();
        OrderRfq order = createOrder("ASSY-PRICE");
        ItemQuoteVersion childA = createQuotedItem(order, "A-1", 2, 10.0);
        ItemQuoteVersion childB = createQuotedItem(order, "B-1", 1, 20.0);
        ItemQuoteVersion parent = createPendingItem(order, "P-1", 3);

        submitAssembly(parent, 50.0, child(parent, childA, 2), child(parent, childB, 1));

        ItemQuoteVersion saved = itemMapper.selectById(parent.getId());
        assertThat(saved.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(saved.getAssemblyFee()).isEqualTo(50.0);
        assertThat(saved.getBaseCost()).isEqualTo(90.0);
        assertThat(saved.getFinalUnitPrice()).isEqualTo(106.5);
        assertThat(saved.getFinalTotalPrice()).isEqualTo(319.5);
        List<Map<String, Object>> feeRows = (List<Map<String, Object>>) saved.getFeeData();
        assertThat(feeRows).hasSize(3);
        assertThat(feeRows)
                .extracting(row -> row.get("feeName"))
                .containsExactly("管理费", "利润", "税费");
        assertThat(feeRows)
                .extracting(row -> row.get("cost"))
                .containsExactly(2.5, 7.5, 6.5);

        QueryWrapper<ItemAssemblyRelation> qw = new QueryWrapper<>();
        qw.eq("parent_item_id", parent.getId());
        assertThat(assemblyRelationMapper.selectList(qw)).hasSize(2);
    }

    @Test
    void refreshOrderQuotesRecalculatesNormalAndAssemblyItemsWithCurrentFees() {
        resetMarkupFeesForAssemblyTest();
        OrderRfq order = createOrder("ASSY-REFRESH");
        ItemQuoteVersion child = createPendingItem(order, "REF-CHILD", 2);
        submitNormalQuoteWithBaseCost(child, 100.0);
        child = itemMapper.selectById(child.getId());
        assertThat(child.getFinalUnitPrice()).isCloseTo(143.0, within(0.000001));

        ItemQuoteVersion parent = createPendingItem(order, "REF-PARENT", 1);
        submitAssembly(parent, 50.0, child(parent, child, 1));
        parent = itemMapper.selectById(parent.getId());
        assertThat(parent.getFinalUnitPrice()).isCloseTo(209.5, within(0.000001));

        upsertMarkupFee("管理费", 10.0, 2);

        Map<String, Object> result = engineerService.refreshOrderQuotes(order.getId());

        child = itemMapper.selectById(child.getId());
        parent = itemMapper.selectById(parent.getId());
        assertThat(result.get("refreshedCount")).isEqualTo(2);
        assertThat(result.get("normalCount")).isEqualTo(1);
        assertThat(result.get("assemblyCount")).isEqualTo(1);
        assertThat(child.getFinalUnitPrice()).isCloseTo(148.0, within(0.000001));
        assertThat(parent.getFinalUnitPrice()).isCloseTo(217.0, within(0.000001));
    }

    @Test
    void assemblyRelationsRejectCycles() {
        OrderRfq order = createOrder("ASSY-CYCLE");
        ItemQuoteVersion leaf = createQuotedItem(order, "LEAF", 5, 10.0);
        ItemQuoteVersion parent = createPendingItem(order, "PARENT", 2);
        ItemQuoteVersion grandParent = createPendingItem(order, "GRAND", 1);

        submitAssembly(parent, 5.0, child(parent, leaf, 1));
        submitAssembly(grandParent, 8.0, child(grandParent, parent, 1));

        assertThatThrownBy(() -> submitAssembly(parent, 1.0, child(parent, grandParent, 1)))
                .hasMessageContaining("循环");
    }

    @Test
    void childRequoteInvalidatesAncestorAssemblies() {
        OrderRfq order = createOrder("ASSY-INVALIDATE");
        ItemQuoteVersion leaf = createQuotedItem(order, "LEAF", 5, 10.0);
        ItemQuoteVersion parent = createPendingItem(order, "PARENT", 2);
        ItemQuoteVersion grandParent = createPendingItem(order, "GRAND", 1);

        submitAssembly(parent, 5.0, child(parent, leaf, 1));
        submitAssembly(grandParent, 8.0, child(grandParent, parent, 1));

        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(leaf.getId());
        req.setQuoteMode("NORMAL");
        req.setMaterialData(Map.of("cost", 30));
        req.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(req);

        ItemQuoteVersion invalidParent = itemMapper.selectById(parent.getId());
        ItemQuoteVersion invalidGrandParent = itemMapper.selectById(grandParent.getId());
        assertThat(invalidParent.getStatus()).isEqualTo("未核价");
        assertThat(invalidParent.getFinalUnitPrice()).isNull();
        assertThat(invalidGrandParent.getStatus()).isEqualTo("未核价");
        assertThat(invalidGrandParent.getFinalUnitPrice()).isNull();
    }

    @Test
    void deliveryGenerationTreatsAssemblyAndChildrenAsIndependentItems() {
        OrderRfq order = createOrder("ASSY-DELIVERY");
        order.setCustomerName("组合提单客户");
        order.setStatus("已报价");
        orderMapper.updateById(order);

        ItemQuoteVersion child = createQuotedItem(order, "CHILD", 4, 10.0);
        ItemQuoteVersion parent = createPendingItem(order, "PARENT", 2);
        submitAssembly(parent, 5.0, child(parent, child, 2));

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqId(order.getId());
        req.setOrderType("正式订单");
        req.setInternalProjectNo("IP-ASSY");
        req.setCustomerOrderNo("CO-ASSY");
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(parent.getId());
        line.setAcceptedQty(1);
        DeliveryGenerateReq.DeliveryItemReq childLine = new DeliveryGenerateReq.DeliveryItemReq();
        childLine.setItemId(child.getId());
        childLine.setAcceptedQty(2);
        req.setItems(List.of(line, childLine));

        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).extracting(OrderDeliveryTicket::getArrangeType)
                .containsExactly("计划", "计划");
        assertThat(tickets).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(1, 2);
        assertThat(tickets).extracting(OrderDeliveryTicket::getItemType)
                .containsExactly("组合件", "普通件");

        List<ProcessSheetDTO> sheets = deliveryService.listProcessSheets(order.getId());
        assertThat(sheets).extracting(ProcessSheetDTO::getItemId)
                .contains(parent.getId(), child.getId());
        assertThat(sheets).extracting(ProcessSheetDTO::getItemType)
                .contains("组合件", "普通件");

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", order.getId());
        assertThat(ticketMapper.selectList(ticketQw)).hasSize(2);
    }

    @Test
    void repeatQuoteReusesHistoricalAssemblyParentWithCurrentChildIds() {
        resetMarkupFeesForAssemblyTest();
        String customerName = "组合重复复用客户-" + System.nanoTime();

        OrderRfq historicalOrder = createOrder("ASSY-HIST-REUSE");
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus("已生成提单");
        orderMapper.updateById(historicalOrder);
        ItemQuoteVersion historicalChild = createPendingItem(historicalOrder, "H-CHILD-REUSE", 2);
        submitNormalQuoteWithBaseCost(historicalChild, 10.0);
        historicalChild = markItemReleased(historicalChild);
        ItemQuoteVersion historicalParent = createPendingItem(historicalOrder, "H-PARENT-REUSE", 1);
        submitAssembly(historicalParent, 30.0, child(historicalParent, historicalChild, 2.5, 30));
        historicalParent = markItemReleased(historicalParent);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalParent.getId());
        ticket.setPartNo(historicalParent.getPartNo());
        ticket.setVersion(historicalParent.getVersion());
        ticket.setQty(1);
        ticket.setArrangeType("组合件组装");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = createOrder("ASSY-CUR-REUSE");
        currentOrder.setCustomerName(customerName);
        orderMapper.updateById(currentOrder);
        ItemQuoteVersion currentChild = createPendingItem(currentOrder, historicalChild.getPartNo(), 2);
        ItemQuoteVersion currentParent = createPendingItem(currentOrder, historicalParent.getPartNo(), 1);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion savedChild = itemMapper.selectById(currentChild.getId());
        ItemQuoteVersion savedParent = itemMapper.selectById(currentParent.getId());
        assertThat(result.get("appliedCount")).isEqualTo(2);
        assertThat(savedChild.getFinalUnitPrice()).isNotNull();
        assertThat(savedParent.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(savedParent.getAssemblyFee()).isEqualTo(30.0);
        assertThat(savedParent.getFinalUnitPrice()).isNotNull();

        QueryWrapper<ItemAssemblyRelation> relationQw = new QueryWrapper<>();
        relationQw.eq("parent_item_id", currentParent.getId());
        List<ItemAssemblyRelation> relations = assemblyRelationMapper.selectList(relationQw);
        assertThat(relations).hasSize(1);
        ItemAssemblyRelation relation = relations.get(0);
        assertThat(relation.getChildItemId()).isEqualTo(currentChild.getId());
        assertThat(relation.getChildItemId()).isNotEqualTo(historicalChild.getId());
        assertThat(relation.getChildQtyPerParent()).isEqualTo(2.5);
        assertThat(relation.getSortNo()).isEqualTo(30);
    }

    @Test
    void repeatQuoteReusesNestedHistoricalAssembliesInDependencyOrder() {
        resetMarkupFeesForAssemblyTest();
        String customerName = "组合多层复用客户-" + System.nanoTime();

        OrderRfq historicalOrder = createOrder("ASSY-NEST-HIST");
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus("已生成提单");
        orderMapper.updateById(historicalOrder);
        ItemQuoteVersion historicalLeaf = createPendingItem(historicalOrder, "N-LEAF", 4);
        submitNormalQuoteWithBaseCost(historicalLeaf, 12.0);
        historicalLeaf = markItemReleased(historicalLeaf);
        ItemQuoteVersion historicalMiddle = createPendingItem(historicalOrder, "N-MID", 2);
        submitAssembly(historicalMiddle, 6.0, child(historicalMiddle, historicalLeaf, 3, 20));
        historicalMiddle = markItemReleased(historicalMiddle);
        ItemQuoteVersion historicalTop = createPendingItem(historicalOrder, "N-TOP", 1);
        submitAssembly(historicalTop, 9.0, child(historicalTop, historicalMiddle, 2, 40));
        historicalTop = markItemReleased(historicalTop);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalTop.getId());
        ticket.setPartNo(historicalTop.getPartNo());
        ticket.setVersion(historicalTop.getVersion());
        ticket.setQty(1);
        ticket.setArrangeType("组合件组装");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = createOrder("ASSY-NEST-CUR");
        currentOrder.setCustomerName(customerName);
        orderMapper.updateById(currentOrder);
        ItemQuoteVersion currentLeaf = createPendingItem(currentOrder, historicalLeaf.getPartNo(), 4);
        ItemQuoteVersion currentMiddle = createPendingItem(currentOrder, historicalMiddle.getPartNo(), 2);
        ItemQuoteVersion currentTop = createPendingItem(currentOrder, historicalTop.getPartNo(), 1);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        assertThat(result.get("appliedCount")).isEqualTo(3);
        assertThat(itemMapper.selectById(currentLeaf.getId()).getFinalUnitPrice()).isNotNull();
        assertThat(itemMapper.selectById(currentMiddle.getId()).getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(itemMapper.selectById(currentTop.getId()).getQuoteMode()).isEqualTo("ASSEMBLY");

        QueryWrapper<ItemAssemblyRelation> middleRelationQw = new QueryWrapper<>();
        middleRelationQw.eq("parent_item_id", currentMiddle.getId());
        List<ItemAssemblyRelation> middleRelations = assemblyRelationMapper.selectList(middleRelationQw);
        assertThat(middleRelations).hasSize(1);
        assertThat(middleRelations.get(0).getChildItemId()).isEqualTo(currentLeaf.getId());
        assertThat(middleRelations.get(0).getChildQtyPerParent()).isEqualTo(3.0);
        assertThat(middleRelations.get(0).getSortNo()).isEqualTo(20);

        QueryWrapper<ItemAssemblyRelation> topRelationQw = new QueryWrapper<>();
        topRelationQw.eq("parent_item_id", currentTop.getId());
        List<ItemAssemblyRelation> topRelations = assemblyRelationMapper.selectList(topRelationQw);
        assertThat(topRelations).hasSize(1);
        assertThat(topRelations.get(0).getChildItemId()).isEqualTo(currentMiddle.getId());
        assertThat(topRelations.get(0).getChildQtyPerParent()).isEqualTo(2.0);
        assertThat(topRelations.get(0).getSortNo()).isEqualTo(40);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatQuoteSkipsAssemblyWhenCurrentChildIsMissing() {
        String customerName = "组合重复客户-" + System.nanoTime();

        OrderRfq historicalOrder = createOrder("ASSY-HIST");
        historicalOrder.setCustomerName(customerName);
        historicalOrder.setStatus("已生成提单");
        orderMapper.updateById(historicalOrder);
        ItemQuoteVersion historicalChild = markItemReleased(createQuotedItem(historicalOrder, "H-CHILD", 2, 10.0));
        ItemQuoteVersion historicalParent = createPendingItem(historicalOrder, "H-PARENT", 1);
        submitAssembly(historicalParent, 30.0, child(historicalParent, historicalChild, 1));
        historicalParent = markItemReleased(historicalParent);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(historicalOrder.getId());
        ticket.setItemId(historicalParent.getId());
        ticket.setPartNo(historicalParent.getPartNo());
        ticket.setVersion(historicalParent.getVersion());
        ticket.setQty(1);
        ticket.setArrangeType("组合件组装");
        ticketMapper.insert(ticket);

        OrderRfq currentOrder = createOrder("ASSY-CUR");
        currentOrder.setCustomerName(customerName);
        orderMapper.updateById(currentOrder);
        ItemQuoteVersion currentParent = createPendingItem(currentOrder, historicalParent.getPartNo(), 1);
        ItemQuoteVersion before = itemMapper.selectById(currentParent.getId());

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion saved = itemMapper.selectById(currentParent.getId());
        assertThat(result.get("appliedCount")).isEqualTo(0);
        assertThat(saved.getQuoteMode()).isEqualTo(before.getQuoteMode());
        assertThat(saved.getAssemblyFee()).isEqualTo(before.getAssemblyFee());
        assertThat(saved.getFinalUnitPrice()).isNull();
        assertThat(saved.getStatus()).isEqualTo("待核价");
        List<Map<String, Object>> skippedItems = (List<Map<String, Object>>) result.get("skippedItems");
        assertThat(skippedItems).extracting(row -> row.get("reason").toString())
                .anyMatch(reason -> reason.contains("缺少子件"));

        QueryWrapper<ItemAssemblyRelation> relationQw = new QueryWrapper<>();
        relationQw.eq("parent_item_id", currentParent.getId());
        assertThat(assemblyRelationMapper.selectList(relationQw))
                .as("缺少当前订单子件时，重复组合件父件不能写入关系表")
                .isEmpty();
    }

    @Test
    void repeatQuoteReusesReleasedHistoricalAssemblyAcrossCustomersWithoutDeliveryTickets() {
        resetMarkupFeesForAssemblyTest();

        OrderRfq historicalOrder = createOrder("ASSY-REL-HIST");
        historicalOrder.setCustomerName("历史组合客户");
        historicalOrder.setStatus(QuoteStatusWorkflow.RELEASED);
        orderMapper.updateById(historicalOrder);
        ItemQuoteVersion historicalChild = createPendingItem(historicalOrder, "REL-ASSY-CHILD", 2);
        submitNormalQuoteWithBaseCost(historicalChild, 18.0);
        historicalChild = markItemReleased(historicalChild);
        ItemQuoteVersion historicalParent = createPendingItem(historicalOrder, "REL-ASSY-PARENT", 1);
        submitAssembly(historicalParent, 25.0, child(historicalParent, historicalChild, 2, 20));
        historicalParent = markItemReleased(historicalParent);

        OrderRfq currentOrder = createOrder("ASSY-REL-CUR");
        currentOrder.setCustomerName("当前组合客户");
        orderMapper.updateById(currentOrder);
        ItemQuoteVersion currentChild = createPendingItem(currentOrder, historicalChild.getPartNo(), 2);
        ItemQuoteVersion currentParent = createPendingItem(currentOrder, historicalParent.getPartNo(), 1);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion savedChild = itemMapper.selectById(currentChild.getId());
        ItemQuoteVersion savedParent = itemMapper.selectById(currentParent.getId());
        assertThat(result.get("appliedCount")).isEqualTo(2);
        assertThat(savedChild.getFinalUnitPrice()).isNotNull();
        assertThat(savedParent.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(savedParent.getAssemblyFee()).isEqualTo(25.0);

        QueryWrapper<ItemAssemblyRelation> relationQw = new QueryWrapper<>();
        relationQw.eq("parent_item_id", currentParent.getId());
        List<ItemAssemblyRelation> relations = assemblyRelationMapper.selectList(relationQw);
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getChildItemId()).isEqualTo(currentChild.getId());
    }

    private OrderRfq createOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "客户");
        order.setStatus("待工程部评估");
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion createPendingItem(OrderRfq order, String partNo, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription(partNo + "描述");
        item.setQty(qty);
        item.setDrawingUrl("/uploads/test/" + partNo + ".pdf");
        item.setStatus("待核价");
        itemMapper.insert(item);
        return item;
    }

    private ItemQuoteVersion createQuotedItem(OrderRfq order, String partNo, int qty, double unitPrice) {
        ItemQuoteVersion item = createPendingItem(order, partNo, qty);
        item.setQuoteMode("NORMAL");
        item.setBaseCost(unitPrice);
        item.setFinalUnitPrice(unitPrice);
        item.setFinalTotalPrice(unitPrice * qty);
        item.setStatus("已报价");
        itemMapper.updateById(item);
        return itemMapper.selectById(item.getId());
    }

    private ItemQuoteVersion markItemReleased(ItemQuoteVersion item) {
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        saved.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        itemMapper.updateById(saved);
        return itemMapper.selectById(item.getId());
    }

    private QuoteSubmitReq.AssemblyChildDTO child(ItemQuoteVersion parent, ItemQuoteVersion child, double qtyPerParent) {
        return child(parent, child, qtyPerParent, 10);
    }

    private QuoteSubmitReq.AssemblyChildDTO child(ItemQuoteVersion parent, ItemQuoteVersion child, double qtyPerParent, int sortNo) {
        QuoteSubmitReq.AssemblyChildDTO dto = new QuoteSubmitReq.AssemblyChildDTO();
        dto.setChildItemId(child.getId());
        dto.setQtyPerParent(qtyPerParent);
        dto.setSortNo(sortNo);
        return dto;
    }

    private void submitAssembly(ItemQuoteVersion parent, double assemblyFee, QuoteSubmitReq.AssemblyChildDTO... children) {
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(parent.getId());
        req.setQuoteMode("ASSEMBLY");
        req.setAssemblyFee(assemblyFee);
        req.setAssemblyChildren(List.of(children));
        engineerService.calculateAndSaveQuote(req);
    }

    private void submitNormalQuoteWithBaseCost(ItemQuoteVersion item, double baseCost) {
        QuoteSubmitReq req = new QuoteSubmitReq();
        req.setItemId(item.getId());
        req.setQuoteMode("NORMAL");
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("cost", baseCost);
        req.setMaterialData(materialData);
        req.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(req);
    }

    private void resetMarkupFeesForAssemblyTest() {
        UpdateWrapper<DictMarkupFee> disableAll = new UpdateWrapper<>();
        disableAll.set("enabled", false);
        markupFeeMapper.update(null, disableAll);
        upsertMarkupFee("运输费", 10.0, 1);
        upsertMarkupFee("管理费", 5.0, 2);
        upsertMarkupFee("利润", 15.0, 3);
        upsertMarkupFee("税费", 13.0, 4);
    }

    private void upsertMarkupFee(String feeName, double ratePercent, int sortNo) {
        QueryWrapper<DictMarkupFee> qw = new QueryWrapper<>();
        qw.eq("fee_name", feeName);
        DictMarkupFee fee = markupFeeMapper.selectOne(qw);
        if (fee == null) {
            fee = new DictMarkupFee();
        }
        fee.setFeeName(feeName);
        fee.setRatePercent(ratePercent);
        fee.setSortNo(sortNo);
        fee.setEnabled(true);
        if (fee.getId() == null) {
            markupFeeMapper.insert(fee);
        } else {
            markupFeeMapper.updateById(fee);
        }
    }
}
