package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private DictPricingMarkupFeeMapper pricingMarkupFeeMapper;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private CustomerMasterMapper customerMapper;

    @Test
    @SuppressWarnings("unchecked")
    void assemblyQuoteUsesAssemblyScopeMarkupOnlyOnAssemblyFeeMultiplicatively() {
        resetMarkupFeesForAssemblyTest();
        upsertPricingMarkupFee("NORMAL", "BASIC_PERCENT", "Normal only fee", 100.0, 9);
        OrderRfq order = createOrder("ASSY-PRICE");
        ItemQuoteVersion childA = createQuotedItem(order, "A-1", 2, 10.0);
        ItemQuoteVersion childB = createQuotedItem(order, "B-1", 1, 20.0);
        ItemQuoteVersion parent = createPendingItem(order, "P-1", 3);

        submitAssembly(parent, 50.0, child(parent, childA, 2), child(parent, childB, 1));

        ItemQuoteVersion saved = itemMapper.selectById(parent.getId());
        assertThat(saved.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(saved.getAssemblyFee()).isEqualTo(50.0);
        assertThat(saved.getBaseCost()).isEqualTo(90.0);
        assertThat(saved.getUntaxedUnitPrice()).isCloseTo(100.38, within(0.000001));
        assertThat(saved.getTaxIncludedUnitPrice()).isCloseTo(108.22, within(0.000001));
        assertThat(saved.getTaxIncludedTotalPrice()).isCloseTo(324.67, within(0.000001));
        List<Map<String, Object>> feeRows = (List<Map<String, Object>>) saved.getFeeData();
        assertThat(feeRows).hasSize(3);
        assertThat(feeRows)
                .extracting(row -> row.get("feeName"))
                .containsExactly("Assembly management", "Assembly profit", "Assembly tax");
        assertThat(feeRows)
                .extracting(row -> row.get("feeCategory"))
                .containsExactly("BASIC_PERCENT", "BASIC_PERCENT", "TAX");
        assertThat((Double) feeRows.get(0).get("cost")).isCloseTo(2.5, within(0.000001));
        assertThat((Double) feeRows.get(1).get("cost")).isCloseTo(7.875, within(0.000001));
        assertThat((Double) feeRows.get(2).get("cost")).isCloseTo(7.84875, within(0.000001));

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
        assertThat(child.getTaxIncludedUnitPrice()).isCloseTo(150.09, within(0.000001));

        ItemQuoteVersion parent = createPendingItem(order, "REF-PARENT", 1);
        submitAssembly(parent, 50.0, child(parent, child, 1));
        parent = itemMapper.selectById(parent.getId());
        assertThat(parent.getTaxIncludedUnitPrice()).isCloseTo(218.31, within(0.000001));

        upsertPricingMarkupFee("NORMAL", "BASIC_PERCENT", "Management", 10.0, 2);

        Map<String, Object> result = engineerService.refreshOrderQuotes(order.getId());

        child = itemMapper.selectById(child.getId());
        parent = itemMapper.selectById(parent.getId());
        assertThat(result.get("refreshedCount")).isEqualTo(2);
        assertThat(result.get("normalCount")).isEqualTo(1);
        assertThat(result.get("assemblyCount")).isEqualTo(1);
        assertThat(child.getTaxIncludedUnitPrice()).isCloseTo(157.24, within(0.000001));
        assertThat(parent.getTaxIncludedUnitPrice()).isCloseTo(225.46, within(0.000001));
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
        req.setMaterialData(materialDataWithCost(30.0));
        req.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(req);

        ItemQuoteVersion invalidParent = itemMapper.selectById(parent.getId());
        ItemQuoteVersion invalidGrandParent = itemMapper.selectById(grandParent.getId());
        assertThat(invalidParent.getStatus()).isEqualTo("未核价");
        assertThat(invalidParent.getTaxIncludedUnitPrice()).isNull();
        assertThat(invalidParent.getTaxIncludedTotalPrice()).isNull();
        assertThat(invalidParent.getUntaxedUnitPrice()).isNull();
        assertThat(invalidParent.getUntaxedTotalPrice()).isNull();
        assertThat(invalidGrandParent.getStatus()).isEqualTo("未核价");
        assertThat(invalidGrandParent.getTaxIncludedUnitPrice()).isNull();
        assertThat(invalidGrandParent.getTaxIncludedTotalPrice()).isNull();
        assertThat(invalidGrandParent.getUntaxedUnitPrice()).isNull();
        assertThat(invalidGrandParent.getUntaxedTotalPrice()).isNull();
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
        parent = itemMapper.selectById(parent.getId());
        parent.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.updateById(parent);

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
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

        ensureCustomer(order.getCustomerName());
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);

        assertThat(tickets).extracting(OrderDeliveryTicket::getArrangeType)
                .containsExactly("计划", "计划");
        assertThat(tickets).extracting(OrderDeliveryTicket::getQty)
                .containsExactly(2, 1);
        assertThat(tickets).extracting(OrderDeliveryTicket::getItemType)
                .containsExactly("普通件", "组合件");

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
    @SuppressWarnings("unchecked")
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
        assertThat(savedChild.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(savedParent.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(savedParent.getAssemblyFee()).isEqualTo(30.0);
        assertThat(savedParent.getTaxIncludedUnitPrice()).isNotNull();
        List<Map<String, Object>> parentRoutes = (List<Map<String, Object>>) savedParent.getProcessRoute();
        assertThat(parentRoutes).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("routeType", "FEE");
            assertThat(route).containsEntry("sourceType", "extra");
            assertThat(route).containsEntry("processName", "组装费");
            assertThat(route).containsEntry("calcType", "非工时费用");
            assertThat(route).containsEntry("lineCost", 30.0);
        });

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
        assertThat(itemMapper.selectById(currentLeaf.getId()).getTaxIncludedUnitPrice()).isNotNull();
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
        assertThat(saved.getTaxIncludedUnitPrice()).isNull();
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
        historicalChild.setMaterialNo("MES-ASSY-CHILD");
        historicalChild.setMaterialNoStatus("已确认");
        historicalChild.setMaterialNoSource("MES_TICKET");
        itemMapper.updateById(historicalChild);
        ItemQuoteVersion historicalParent = createPendingItem(historicalOrder, "REL-ASSY-PARENT", 1);
        submitAssembly(historicalParent, 25.0, child(historicalParent, historicalChild, 2, 20));
        historicalParent = markItemReleased(historicalParent);
        historicalParent.setMaterialNo("MES-ASSY-PARENT");
        historicalParent.setMaterialNoStatus("已确认");
        historicalParent.setMaterialNoSource("MES_TICKET");
        itemMapper.updateById(historicalParent);

        OrderRfq currentOrder = createOrder("ASSY-REL-CUR");
        currentOrder.setCustomerName("当前组合客户");
        orderMapper.updateById(currentOrder);
        ItemQuoteVersion currentChild = createPendingItem(currentOrder, historicalChild.getPartNo(), 2);
        ItemQuoteVersion currentParent = createPendingItem(currentOrder, historicalParent.getPartNo(), 1);

        Map<String, Object> result = engineerService.applyRepeatQuotes(currentOrder.getId());

        ItemQuoteVersion savedChild = itemMapper.selectById(currentChild.getId());
        ItemQuoteVersion savedParent = itemMapper.selectById(currentParent.getId());
        assertThat(result.get("appliedCount")).isEqualTo(2);
        assertThat(result.get("materialNoCopiedCount")).isEqualTo(2);
        assertThat(result.get("materialNoSkippedCount")).isEqualTo(0);
        assertThat(result.get("materialNoConflictCount")).isEqualTo(0);
        assertThat(savedChild.getTaxIncludedUnitPrice()).isNotNull();
        assertThat(savedChild.getMaterialNo()).isEqualTo("MES-ASSY-CHILD");
        assertThat(savedChild.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(savedParent.getQuoteMode()).isEqualTo("ASSEMBLY");
        assertThat(savedParent.getAssemblyFee()).isEqualTo(25.0);
        assertThat(savedParent.getMaterialNo()).isEqualTo("MES-ASSY-PARENT");
        assertThat(savedParent.getMaterialNoStatus()).isEqualTo("已确认");

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

    private void ensureCustomer(String customerName) {
        CustomerMaster customer = customerMapper.selectOne(new QueryWrapper<CustomerMaster>()
                .eq("customer_name", customerName)
                .last("LIMIT 1"));
        if (customer == null) {
            customer = new CustomerMaster();
            customer.setCustomerCode("TEST-" + System.nanoTime());
            customer.setCustomerShortName(customerName);
            customer.setCustomerName(customerName);
            customer.setEnabled(true);
            customerMapper.insert(customer);
        }
        UpdateWrapper<OrderRfq> update = new UpdateWrapper<>();
        update.eq("customer_name", customerName)
                .and(wrapper -> wrapper.isNull("customer_code").or().eq("customer_code", ""))
                .set("customer_code", customer.getCustomerCode())
                .set("customer_short_name", customer.getCustomerShortName());
        orderMapper.update(null, update);
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
        item.setTaxIncludedUnitPrice(unitPrice);
        item.setTaxIncludedTotalPrice(unitPrice * qty);
        item.setStatus("已报价");
        itemMapper.updateById(item);
        return itemMapper.selectById(item.getId());
    }

    private ItemQuoteVersion markItemReleased(ItemQuoteVersion item) {
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        saved.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        saved.setRepeatMatchEligible(true);
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
        req.setMaterialData(materialDataWithCost(baseCost));
        req.setProcessRoute(List.of());
        engineerService.calculateAndSaveQuote(req);
    }

    private Map<String, Object> materialDataWithCost(double baseCost) {
        DictMaterial material = ensureFlatPriceMaterial();
        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("materialCode", material.getMaterialCode());
        materialData.put("shape", "\u677f\u6750");
        materialData.put("weight", baseCost);
        return materialData;
    }

    private DictMaterial ensureFlatPriceMaterial() {
        DictMaterial existing = materialMapper.selectOne(new QueryWrapper<DictMaterial>()
                .eq("material_code", "MAT-ASSY-FLAT"));
        if (existing != null) {
            return existing;
        }
        DictMaterial material = new DictMaterial();
        material.setMaterialCode("MAT-ASSY-FLAT");
        material.setMaterialName("Assembly flat test material");
        material.setMaterialTypeId(1L);
        material.setMaterialCategory("\u94a2\u94c1");
        material.setDensity(1.0);
        material.setPlatePrice(1.0);
        material.setBarPrice(1.0);
        material.setTubePrice(1.0);
        material.setEnabled(true);
        material.setSortNo(0);
        material.setRemarks("test material");
        materialMapper.insert(material);
        return material;
    }

    private void resetMarkupFeesForAssemblyTest() {
        UpdateWrapper<DictPricingMarkupFee> disableAll = new UpdateWrapper<>();
        disableAll.set("enabled", false);
        pricingMarkupFeeMapper.update(null, disableAll);
        upsertPricingMarkupFee("NORMAL", "BASIC_PERCENT", "Freight", 10.0, 1);
        upsertPricingMarkupFee("NORMAL", "BASIC_PERCENT", "Management", 5.0, 2);
        upsertPricingMarkupFee("NORMAL", "BASIC_PERCENT", "Profit", 15.0, 3);
        upsertPricingMarkupFee("NORMAL", "TAX", "Normal category tax", 13.0, 4);
        upsertPricingMarkupFee("ASSEMBLY", "BASIC_PERCENT", "Assembly management", 5.0, 1);
        upsertPricingMarkupFee("ASSEMBLY", "BASIC_PERCENT", "Assembly profit", 15.0, 2);
        upsertPricingMarkupFee("ASSEMBLY", "TAX", "Assembly tax", 13.0, 3);
    }

    private void upsertPricingMarkupFee(String scope, String category, String feeName, double ratePercent, int sortNo) {
        QueryWrapper<DictPricingMarkupFee> qw = new QueryWrapper<>();
        qw.eq("pricing_scope", scope).eq("fee_category", category).eq("fee_name", feeName);
        DictPricingMarkupFee fee = pricingMarkupFeeMapper.selectOne(qw);
        if (fee == null) {
            fee = new DictPricingMarkupFee();
        }
        fee.setPricingScope(scope);
        fee.setFeeCategory(category);
        fee.setFeeName(feeName);
        fee.setRatePercent(BigDecimal.valueOf(ratePercent));
        fee.setSortNo(sortNo);
        fee.setEnabled(true);
        fee.setSystemRequired("TAX".equals(category) || "CUSTOMER_WEIGHT".equals(category));
        if (fee.getId() == null) {
            pricingMarkupFeeMapper.insert(fee);
        } else {
            pricingMarkupFeeMapper.updateById(fee);
        }
    }
}
