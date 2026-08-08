package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.entity.SalesOrderSourceRfq;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SalesOrderSourceRfqMapper;
import com.machining.quote.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-stage4/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-stage4/backups/"
})
@Transactional
class RfqReleaseSalesOrderStage4IntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper sourceAssemblyMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;
    @Autowired
    private SalesOrderSourceRfqMapper sourceRfqMapper;
    @Autowired
    private SalesOrderItemAssemblyRelationMapper salesAssemblyMapper;

    @Test
    void atomicallyCreatesOneDerivedSalesOrderWithEffectiveItemValuesAndStableRelations() {
        String suffix = String.valueOf(System.nanoTime());
        String customerName = "阶段4派生客户-" + suffix;
        customer(customerName, "STAGE4-" + suffix);
        OrderRfq firstOrder = order(customerName, "STAGE4-A-" + suffix);
        OrderRfq secondOrder = order(customerName, "STAGE4-B-" + suffix);

        ItemQuoteVersion parent = item(firstOrder, "STAGE4-PARENT-" + suffix, 2, 10);
        parent.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        parent.setCustomerOrderNo("ITEM-CUSTOMER-ORDER");
        parent.setCustomerProjectNo("ITEM-CUSTOMER-PROJECT");
        parent.setCustomerOrderTitle("ITEM-TITLE");
        parent.setOrderType("备货订单");
        parent.setInternalProjectNo("ITEM-INTERNAL");
        parent.setQuoteMode("ASSEMBLY");
        parent.setMaterialNo("MES-STAGE4-001");
        parent.setMaterialNoStatus("已确认");
        parent.setMaterialNoSource("MES");
        parent.setMaterialNoSourceTicketId(701L);
        parent.setMaterialNoSyncedAt(LocalDateTime.of(2026, 8, 2, 9, 30));
        parent.setMaterialNoMessage("MES confirmed");
        itemMapper.updateById(parent);

        ItemQuoteVersion child = item(firstOrder, "STAGE4-CHILD-" + suffix, 4, 20);
        ItemQuoteVersion other = item(secondOrder, "STAGE4-OTHER-" + suffix, 1, 10);
        assembly(firstOrder.getId(), parent.getId(), child.getId());

        DeliveryGenerateReq req = request(firstOrder, secondOrder, parent, child, other, "stage4-" + suffix);

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSalesOrderId()).isNotNull();
        assertThat(result.getSalesOrderNo()).isNotBlank();
        assertThat(result.getDocumentNo()).isNotBlank();
        assertThat(result.getSourceRfqIds()).containsExactly(firstOrder.getId(), secondOrder.getId());
        assertThat(result.getTicketCount()).isEqualTo(3);
        assertThat(result.getConfirmedMaterialCount()).isEqualTo(1);
        assertThat(result.getPendingMaterialCount()).isEqualTo(2);

        SalesOrderHeader header = headerMapper.selectById(result.getSalesOrderId());
        assertThat(header.getSourceType()).isEqualTo("RFQ_RELEASE");
        assertThat(header.getSourceReleaseBatchKey()).isEqualTo(req.getReleaseBatchKey());
        assertThat(header.getSourceReleaseRequestHash()).hasSize(64);
        assertThat(header.getStatus()).isEqualTo("已生成提单");
        assertThat(header.getDocumentTypeCode()).isEqualTo("SO01");
        assertThat(header.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(header.getCustomerOrderNo()).isEqualTo("DEFAULT-CUSTOMER-ORDER");
        assertThat(header.getCustomerProjectNo()).isEqualTo("DEFAULT-CUSTOMER-PROJECT");
        assertThat(header.getCustomerOrderTitle()).isEqualTo("DEFAULT-TITLE");
        assertThat(header.getOrderType()).isEqualTo("正式订单");
        assertThat(header.getInternalProjectNo()).isEqualTo("DEFAULT-INTERNAL");
        assertThat(header.getTotalItemCount()).isEqualTo(3);

        List<SalesOrderSourceRfq> sources = sourceRfqMapper.selectList(
                new QueryWrapper<SalesOrderSourceRfq>()
                        .eq("sales_order_id", header.getId())
                        .orderByAsc("rfq_id"));
        assertThat(sources).extracting(SalesOrderSourceRfq::getRfqId)
                .containsExactly(firstOrder.getId(), secondOrder.getId());

        List<SalesOrderItem> salesItems = salesItemMapper.selectList(
                new QueryWrapper<SalesOrderItem>()
                        .eq("sales_order_id", header.getId())
                        .orderByAsc("line_no"));
        assertThat(salesItems).hasSize(3);
        assertThat(salesItems).extracting(SalesOrderItem::getLineNo).containsExactly(1, 2, 3);
        assertThat(salesItems).extracting(SalesOrderItem::getSourceItemId)
                .containsExactly(parent.getId().longValue(), child.getId().longValue(), other.getId().longValue());
        assertThat(salesItems).allSatisfy(item -> {
            assertThat(item.getSourceType()).isEqualTo("RFQ");
            assertThat(item.getSourceTicketId()).isNotNull();
            assertThat(item.getReleasedQty()).isEqualTo(item.getQty());
            assertThat(item.getRemainingQty()).isZero();
            assertThat(item.getStatus()).isEqualTo("已提单");
        });

        Map<Long, SalesOrderItem> salesItemBySource = salesItems.stream()
                .collect(Collectors.toMap(SalesOrderItem::getSourceItemId, Function.identity()));
        SalesOrderItem parentSalesItem = salesItemBySource.get(parent.getId().longValue());
        assertThat(parentSalesItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(parentSalesItem.getCustomerOrderNo()).isEqualTo("ITEM-CUSTOMER-ORDER");
        assertThat(parentSalesItem.getCustomerProjectNo()).isEqualTo("ITEM-CUSTOMER-PROJECT");
        assertThat(parentSalesItem.getCustomerOrderTitle()).isEqualTo("ITEM-TITLE");
        assertThat(parentSalesItem.getOrderType()).isEqualTo("备货订单");
        assertThat(parentSalesItem.getInternalProjectNo()).isEqualTo("ITEM-INTERNAL");
        assertThat(parentSalesItem.getMaterialNo()).isEqualTo("MES-STAGE4-001");
        assertThat(parentSalesItem.getMaterialNoStatus()).isEqualTo("已确认");
        assertThat(parentSalesItem.getMaterialNoSource()).isEqualTo("MES");
        assertThat(parentSalesItem.getTaxIncludedTotalPrice()).isEqualByComparingTo("24.00");
        assertStandardProcessRoute(parentSalesItem.getProcessRoute());

        SalesOrderItem childSalesItem = salesItemBySource.get(child.getId().longValue());
        assertThat(childSalesItem.getSalesOrderDate()).isEqualTo(req.getSalesOrderDate());
        assertThat(childSalesItem.getCustomerOrderNo()).isEqualTo(req.getCustomerOrderNo());
        assertThat(childSalesItem.getCustomerProjectNo()).isEqualTo(req.getCustomerProjectNo());
        assertThat(childSalesItem.getCustomerOrderTitle()).isEqualTo(req.getCustomerOrderTitle());
        assertThat(childSalesItem.getOrderType()).isEqualTo(req.getOrderType());
        assertThat(childSalesItem.getInternalProjectNo()).isEqualTo(req.getInternalProjectNo());

        ItemQuoteVersion reloadedParent = itemMapper.selectById(parent.getId());
        assertThat(reloadedParent.getCustomerOrderNo()).isEqualTo("ITEM-CUSTOMER-ORDER");
        assertThat(reloadedParent.getOrderType()).isEqualTo("备货订单");
        ItemQuoteVersion reloadedChild = itemMapper.selectById(child.getId());
        assertThat(reloadedChild.getSalesOrderDate()).isEqualTo(req.getSalesOrderDate());
        assertThat(reloadedChild.getCustomerOrderNo()).isEqualTo(req.getCustomerOrderNo());
        assertThat(reloadedChild.getCustomerProjectNo()).isEqualTo(req.getCustomerProjectNo());
        assertThat(reloadedChild.getCustomerOrderTitle()).isEqualTo(req.getCustomerOrderTitle());
        assertThat(reloadedChild.getOrderType()).isEqualTo(req.getOrderType());
        assertThat(reloadedChild.getInternalProjectNo()).isEqualTo(req.getInternalProjectNo());

        List<OrderDeliveryTicket> tickets = ticketMapper.selectBatchIds(result.getTicketIds());
        Map<Long, OrderDeliveryTicket> ticketBySource = tickets.stream()
                .collect(Collectors.toMap(OrderDeliveryTicket::getSourceItemId, Function.identity()));
        OrderDeliveryTicket parentTicket = ticketBySource.get(parent.getId().longValue());
        assertThat(parentTicket.getSourceType()).isEqualTo("RFQ");
        assertThat(parentTicket.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(parentTicket.getCustomerOrderNo()).isEqualTo("ITEM-CUSTOMER-ORDER");
        assertThat(parentTicket.getCustomerProjectNo()).isEqualTo("ITEM-CUSTOMER-PROJECT");
        assertThat(parentTicket.getOrderType()).isEqualTo("备货订单");
        assertThat(parentTicket.getInternalProjectNo()).isEqualTo("ITEM-INTERNAL");
        assertThat(parentTicket.getMaterialNo()).isEqualTo("MES-STAGE4-001");
        assertThat(parentTicket.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(parentTicket.getProcessRoute()).isEqualTo(parentSalesItem.getProcessRoute());
        assertStandardProcessRoute(parentTicket.getProcessRoute());
        assertThat(ticketBySource.get(child.getId().longValue()).getMesSyncStatus()).isEqualTo("PENDING");

        List<SalesOrderItemAssemblyRelation> relations = salesAssemblyMapper.selectList(
                new QueryWrapper<SalesOrderItemAssemblyRelation>()
                        .eq("sales_order_id", header.getId()));
        assertThat(relations).singleElement().satisfies(relation -> {
            assertThat(relation.getParentItemId()).isEqualTo(parentSalesItem.getId());
            assertThat(relation.getChildItemId()).isEqualTo(childSalesItem.getId());
            assertThat(relation.getSourceParentItemId()).isEqualTo(parent.getId());
            assertThat(relation.getSourceChildItemId()).isEqualTo(child.getId());
        });

        assertThat(itemMapper.selectById(parent.getId()).getStatus()).isEqualTo("已提单");
        assertThat(itemMapper.selectById(child.getId()).getStatus()).isEqualTo("已提单");
        assertThat(orderMapper.selectById(firstOrder.getId()).getStatus()).isEqualTo("已生成提单");
        assertThat(orderMapper.selectById(secondOrder.getId()).getStatus()).isEqualTo("已生成提单");

        DeliveryBatchResultDTO replay = deliveryService.generateRfqReleaseBatch(req);
        assertThat(replay.getSalesOrderId()).isEqualTo(result.getSalesOrderId());
        assertThat(replay.getTicketIds()).containsExactlyElementsOf(result.getTicketIds());
        assertThat(countHeaders(req.getReleaseBatchKey())).isEqualTo(1);
        assertThat(countSalesItems(header.getId())).isEqualTo(3);
        assertThat(countTickets(firstOrder.getId(), secondOrder.getId())).isEqualTo(3);

        req.setCustomerProjectNo("CHANGED-CUSTOMER-PROJECT");
        assertThatThrownBy(() -> deliveryService.generateRfqReleaseBatch(req))
                .hasMessageContaining("提单批次参数与首次提交不一致");
        assertThat(countHeaders(req.getReleaseBatchKey())).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private void assertStandardProcessRoute(Object processRoute) {
        assertThat(processRoute).isInstanceOf(List.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) processRoute;
        assertThat(rows).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("processCode", "PACKAGING");
            assertThat(route).containsEntry("categoryName", "包装");
            assertThat(route).containsEntry("detailName", "精细包装");
            assertThat(((Number) route.get("processCategoryId")).longValue()).isEqualTo(3L);
            assertThat(((Number) route.get("processDetailId")).longValue()).isEqualTo(7L);
            assertThat(((Number) route.get("hourlyRate")).doubleValue()).isEqualTo(10.0);
            assertThat(((Number) route.get("unitWorkHours")).doubleValue()).isEqualTo(1.5);
            assertThat(((Number) route.get("lineCost")).doubleValue()).isEqualTo(15.0);
            assertThat(route).containsEntry("sortNo", 10);
        });
    }

    private CustomerMaster customer(String customerName, String customerCode) {
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode(customerCode);
        customer.setCustomerShortName("简称-" + customerCode);
        customer.setCustomerName(customerName);
        customer.setEnabled(true);
        customerMapper.insert(customer);
        return customer;
    }

    private OrderRfq order(String customerName, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqNo);
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion item(OrderRfq order, String partNo, int qty, int sourceLineNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setSalesOrderLineNo(sourceLineNo);
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("阶段4派生零件");
        item.setQty(qty);
        item.setMaterialName("导入材料");
        item.setEngineeringMaterialName("真实材料");
        item.setMaterialShape("板材");
        item.setSurfaceTreatment("阳极氧化");
        item.setTaxIncludedUnitPrice(12.0);
        item.setTaxIncludedTotalPrice(12.0 * qty);
        item.setUntaxedUnitPrice(10.0);
        item.setUntaxedTotalPrice(10.0 * qty);
        item.setLeadTime("5");
        item.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("sortNo", 10),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("processName", "精细包装"),
                Map.entry("calcType", "工时"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("inputVal", 1.5),
                Map.entry("unitRate", 10.0),
                Map.entry("lineCost", 15.0))));
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setDrawingUrl("/stage4/" + partNo + ".pdf");
        itemMapper.insert(item);
        return item;
    }

    private void assembly(int rfqId, int parentItemId, int childItemId) {
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setRfqId(rfqId);
        relation.setParentItemId(parentItemId);
        relation.setChildItemId(childItemId);
        relation.setChildQtyPerParent(2.0);
        relation.setSortNo(1);
        sourceAssemblyMapper.insert(relation);
    }

    private DeliveryGenerateReq request(
            OrderRfq firstOrder,
            OrderRfq secondOrder,
            ItemQuoteVersion parent,
            ItemQuoteVersion child,
            ItemQuoteVersion other,
            String batchKey) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(firstOrder.getId(), secondOrder.getId()));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 7));
        req.setCustomerOrderTitle("DEFAULT-TITLE");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("DEFAULT-INTERNAL");
        req.setCustomerOrderNo("DEFAULT-CUSTOMER-ORDER");
        req.setCustomerProjectNo("DEFAULT-CUSTOMER-PROJECT");
        req.setProjectNo("DEFAULT-PROJECT");
        req.setArrangeType("计划");
        req.setReleaseBatchKey(batchKey);
        req.setItems(List.of(
                line(firstOrder.getId(), parent.getId(), parent.getQty()),
                line(firstOrder.getId(), child.getId(), child.getQty()),
                line(secondOrder.getId(), other.getId(), other.getQty())));
        return req;
    }

    private DeliveryGenerateReq.DeliveryItemReq line(int rfqId, int itemId, int qty) {
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(itemId);
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId((long) rfqId);
        line.setSourceItemId((long) itemId);
        line.setAcceptedQty(qty);
        return line;
    }

    private long countHeaders(String batchKey) {
        return headerMapper.selectCount(new QueryWrapper<SalesOrderHeader>()
                .eq("source_release_batch_key", batchKey));
    }

    private long countSalesItems(Long salesOrderId) {
        return salesItemMapper.selectCount(new QueryWrapper<SalesOrderItem>()
                .eq("sales_order_id", salesOrderId));
    }

    private long countTickets(Integer firstRfqId, Integer secondRfqId) {
        return ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>()
                .in("rfq_id", List.of(firstRfqId, secondRfqId)));
    }
}
