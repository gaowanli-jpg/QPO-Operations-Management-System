package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.RfqReleaseAppendSalesOrderReq;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.entity.SalesOrderSourceRfq;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.SalesOrderSourceRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.RfqReleaseAppendSalesOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-append/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-append/backups/"
})
@Transactional
class RfqReleaseAppendSalesOrderIntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private RfqReleaseAppendSalesOrderService appendSalesOrderService;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;
    @Autowired
    private SalesOrderSourceRfqMapper sourceRfqMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper sourceAssemblyMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderItemAssemblyRelationMapper salesAssemblyMapper;

    @Test
    void appendsRfqReleaseLinesToExistingUnsyncedSalesOrderWithItemFieldsFirstAndReplaysIdempotently() {
        String suffix = String.valueOf(System.nanoTime());
        SalesOrderHeader target = targetSalesOrder("APPEND-CUST-" + suffix, "同步失败");
        SalesOrderItem existing = existingSalesItem(target, 5);
        OrderRfq rfq = rfq(target, "APPEND-RFQ-" + suffix);
        ItemQuoteVersion parent = rfqItem(rfq, "APPEND-PARENT-" + suffix, 2, 10, true);
        ItemQuoteVersion child = rfqItem(rfq, "APPEND-CHILD-" + suffix, 4, 20, false);
        parent.setSalesOrderDate(LocalDate.of(2026, 9, 9));
        parent.setCustomerOrderNo("ROW-PO-SHOULD-WIN");
        parent.setCustomerProjectNo("ROW-CP-SHOULD-WIN");
        parent.setCustomerOrderTitle("ROW-TITLE-SHOULD-WIN");
        parent.setOrderType("预测订单");
        parent.setInternalProjectNo("ROW-INTERNAL-SHOULD-WIN");
        itemMapper.updateById(parent);
        assembly(rfq.getId(), parent.getId(), child.getId());
        RfqReleaseAppendSalesOrderReq req = appendRequest(target, rfq, parent, child, "append-key-" + suffix);

        DeliveryBatchResultDTO result = deliveryService.appendRfqReleaseToSalesOrder(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isIdempotentReplay()).isFalse();
        assertThat(result.getSalesOrderId()).isEqualTo(target.getId());
        assertThat(result.getSalesOrderNo()).isEqualTo(target.getSalesOrderNo());
        assertThat(result.getTicketCount()).isEqualTo(2);
        assertThat(result.getSourceRfqIds()).containsExactly(rfq.getId());

        SalesOrderHeader reloadedTarget = headerMapper.selectById(target.getId());
        assertThat(reloadedTarget.getCustomerOrderNo()).isEqualTo("TARGET-PO");
        assertThat(reloadedTarget.getOrderType()).isEqualTo("备货订单");
        assertThat(reloadedTarget.getInternalProjectNo()).isEqualTo("TARGET-INTERNAL");
        assertThat(reloadedTarget.getU9SyncStatus()).isEqualTo("未同步");
        assertThat(reloadedTarget.getU9SyncMessage()).isNull();
        assertThat(reloadedTarget.getU9SyncRequestHash()).isNull();
        assertThat(reloadedTarget.getTotalItemCount()).isEqualTo(3);

        List<SalesOrderItem> items = salesItems(target.getId());
        assertThat(items).hasSize(3);
        assertThat(items).extracting(SalesOrderItem::getLineNo).containsExactly(5, 6, 7);
        assertThat(items.get(0).getId()).isEqualTo(existing.getId());

        Map<Long, SalesOrderItem> appendedBySource = items.stream()
                .filter(item -> item.getSourceItemId() != null)
                .collect(Collectors.toMap(SalesOrderItem::getSourceItemId, Function.identity()));
        SalesOrderItem parentSalesItem = appendedBySource.get(parent.getId().longValue());
        SalesOrderItem childSalesItem = appendedBySource.get(child.getId().longValue());
        assertThat(parentSalesItem).isNotNull();
        assertThat(childSalesItem).isNotNull();
        assertThat(parentSalesItem.getSourceType()).isEqualTo("RFQ");
        assertThat(parentSalesItem.getSourceTicketId()).isNotNull();
        assertThat(parentSalesItem.getCustomerOrderNo()).isEqualTo("ROW-PO-SHOULD-WIN");
        assertThat(parentSalesItem.getCustomerProjectNo()).isEqualTo("ROW-CP-SHOULD-WIN");
        assertThat(parentSalesItem.getCustomerOrderTitle()).isEqualTo("ROW-TITLE-SHOULD-WIN");
        assertThat(parentSalesItem.getOrderType()).isEqualTo("预测订单");
        assertThat(parentSalesItem.getInternalProjectNo()).isEqualTo("ROW-INTERNAL-SHOULD-WIN");
        assertThat(parentSalesItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(childSalesItem.getCustomerOrderNo()).isEqualTo("TARGET-PO");
        assertThat(childSalesItem.getCustomerProjectNo()).isEqualTo("TARGET-CP");
        assertThat(childSalesItem.getCustomerOrderTitle()).isEqualTo("TARGET-TITLE");
        assertThat(childSalesItem.getOrderType()).isEqualTo("备货订单");
        assertThat(childSalesItem.getInternalProjectNo()).isEqualTo("TARGET-INTERNAL");
        assertThat(childSalesItem.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(parentSalesItem.getReleasedQty()).isEqualTo(parentSalesItem.getQty());
        assertThat(parentSalesItem.getRemainingQty()).isZero();
        assertThat(parentSalesItem.getUnshippedQty()).isEqualTo(parentSalesItem.getQty());
        assertThat(parentSalesItem.getMaterialNo()).isNull();
        assertThat(parentSalesItem.getMaterialNoStatus()).isEqualTo("未确认");

        List<OrderDeliveryTicket> tickets = ticketMapper.selectBatchIds(result.getTicketIds());
        assertThat(tickets).hasSize(2);
        assertThat(tickets).allSatisfy(ticket -> {
            assertThat(ticket.getSourceType()).isEqualTo("RFQ");
            assertThat(ticket.getSourceOrderId()).isEqualTo(rfq.getId().longValue());
        });
        Map<Long, OrderDeliveryTicket> ticketBySource = tickets.stream()
                .collect(Collectors.toMap(OrderDeliveryTicket::getSourceItemId, Function.identity()));
        OrderDeliveryTicket parentTicket = ticketBySource.get(parent.getId().longValue());
        assertThat(parentTicket.getCustomerOrderNo()).isEqualTo("ROW-PO-SHOULD-WIN");
        assertThat(parentTicket.getCustomerProjectNo()).isEqualTo("ROW-CP-SHOULD-WIN");
        assertThat(parentTicket.getOrderType()).isEqualTo("预测订单");
        assertThat(parentTicket.getInternalProjectNo()).isEqualTo("ROW-INTERNAL-SHOULD-WIN");
        assertThat(parentTicket.getMaterialNo()).isNull();
        assertThat(parentTicket.getMesSyncStatus()).isEqualTo("PENDING");
        OrderDeliveryTicket childTicket = ticketBySource.get(child.getId().longValue());
        assertThat(childTicket.getCustomerOrderNo()).isEqualTo("TARGET-PO");
        assertThat(childTicket.getCustomerProjectNo()).isEqualTo("TARGET-CP");
        assertThat(childTicket.getOrderType()).isEqualTo("备货订单");
        assertThat(childTicket.getInternalProjectNo()).isEqualTo("TARGET-INTERNAL");
        assertThat(parentSalesItem.getSourceTicketId()).isIn(result.getTicketIds());
        assertThat(childSalesItem.getSourceTicketId()).isIn(result.getTicketIds());

        List<SalesOrderSourceRfq> sources = sourceRfqMapper.selectList(new QueryWrapper<SalesOrderSourceRfq>()
                .eq("sales_order_id", target.getId())
                .orderByAsc("rfq_id"));
        assertThat(sources).extracting(SalesOrderSourceRfq::getRfqId).containsExactly(rfq.getId());

        List<SalesOrderItemAssemblyRelation> relations = salesAssemblyMapper.selectList(
                new QueryWrapper<SalesOrderItemAssemblyRelation>().eq("sales_order_id", target.getId()));
        assertThat(relations).singleElement().satisfies(relation -> {
            assertThat(relation.getParentItemId()).isEqualTo(parentSalesItem.getId());
            assertThat(relation.getChildItemId()).isEqualTo(childSalesItem.getId());
            assertThat(relation.getSourceParentItemId()).isEqualTo(parent.getId());
            assertThat(relation.getSourceChildItemId()).isEqualTo(child.getId());
        });

        DeliveryBatchResultDTO replay = deliveryService.appendRfqReleaseToSalesOrder(req);
        assertThat(replay.isIdempotentReplay()).isTrue();
        assertThat(replay.getTicketIds()).containsExactlyElementsOf(result.getTicketIds());
        assertThat(salesItems(target.getId())).hasSize(3);
        assertThat(ticketCount(rfq.getId())).isEqualTo(2);

        ItemQuoteVersion reloadedParent = itemMapper.selectById(parent.getId());
        assertThat(reloadedParent.getCustomerOrderNo()).isEqualTo("ROW-PO-SHOULD-WIN");
        assertThat(reloadedParent.getOrderType()).isEqualTo("预测订单");
        assertThat(reloadedParent.getInternalProjectNo()).isEqualTo("ROW-INTERNAL-SHOULD-WIN");
        ItemQuoteVersion reloadedChild = itemMapper.selectById(child.getId());
        assertThat(reloadedChild.getCustomerOrderNo()).isNull();
        assertThat(reloadedChild.getOrderType()).isNull();
        assertThat(reloadedChild.getInternalProjectNo()).isNull();

        req.setTargetSalesOrderId(targetSalesOrder("OTHER-CUST-" + suffix, "未同步").getId());
        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(req))
                .hasMessageContaining("追加批次参数与首次提交不一致");
    }

    @Test
    void appendRejectsSyncedOrCrossCustomerTargets() {
        String suffix = String.valueOf(System.nanoTime());
        SalesOrderHeader sameCustomerSynced = targetSalesOrder("APPEND-BLOCK-" + suffix, "已同步");
        OrderRfq rfq = rfq(sameCustomerSynced, "APPEND-BLOCK-RFQ-" + suffix);
        ItemQuoteVersion item = rfqItem(rfq, "APPEND-BLOCK-PART-" + suffix, 1, 10, false);

        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(
                appendRequest(sameCustomerSynced, rfq, item, null, "append-synced-" + suffix)))
                .hasMessageContaining("不能追加到已同步或同步中的销售订单");

        SalesOrderHeader sameCustomerSyncing = targetSalesOrder(sameCustomerSynced.getCustomerCode(), "同步中");
        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(
                appendRequest(sameCustomerSyncing, rfq, item, null, "append-syncing-" + suffix)))
                .hasMessageContaining("不能追加到已同步或同步中的销售订单");

        SalesOrderHeader sameCustomerPending = targetSalesOrder(sameCustomerSynced.getCustomerCode(), "结果待确认");
        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(
                appendRequest(sameCustomerPending, rfq, item, null, "append-pending-" + suffix)))
                .hasMessageContaining("不能追加到已同步或同步中的销售订单");

        SalesOrderHeader crossCustomer = targetSalesOrder("APPEND-CROSS-" + suffix, "未同步");
        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(
                appendRequest(crossCustomer, rfq, item, null, "append-cross-" + suffix)))
                .hasMessageContaining("只能追加到同一客户的销售订单");
    }

    @Test
    void rejectsAppendWhenEffectiveCustomerOrderOrInternalProjectNumberIsMissingBeforeWrites() {
        assertAppendRejectedForMissingIdentifiers("CUSTOMER_ORDER", "append-missing-po");
        assertAppendRejectedForMissingIdentifiers("INTERNAL_PROJECT", "append-missing-internal");
    }

    private void assertAppendRejectedForMissingIdentifiers(String missingIdentifier, String batchKeyPrefix) {
        String suffix = String.valueOf(System.nanoTime());
        String customerCode = "APPEND-MISSING-" + suffix;
        String customerOrderNo = "CUSTOMER_ORDER".equals(missingIdentifier) ? null : "TARGET-PO";
        String internalProjectNo = "INTERNAL_PROJECT".equals(missingIdentifier) ? null : "TARGET-INTERNAL";
        SalesOrderHeader target = targetSalesOrder(
                customerCode, "同步失败", customerOrderNo, internalProjectNo);
        OrderRfq rfq = rfq(target, "APPEND-MISSING-RFQ-" + suffix);
        ItemQuoteVersion item = rfqItem(rfq, "APPEND-MISSING-PART-" + suffix, 1, 10, false);

        assertThatThrownBy(() -> deliveryService.appendRfqReleaseToSalesOrder(
                appendRequest(target, rfq, item, null, batchKeyPrefix + "-" + suffix)))
                .hasMessage("编号有缺失，请补齐内部项目号或客户订单号。");

        assertThat(salesItems(target.getId())).isEmpty();
        assertThat(ticketCount(rfq.getId())).isZero();
    }

    @Test
    void appendTargetListIncludesBlankUnsyncedStatusAndFiltersBlockedTargets() {
        String suffix = String.valueOf(System.nanoTime());
        String customerCode = "APPEND-LIST-" + suffix;
        SalesOrderHeader nullStatus = targetSalesOrder(customerCode, null);
        SalesOrderHeader blankStatus = targetSalesOrder(customerCode, "");
        SalesOrderHeader unsynced = targetSalesOrder(customerCode, "未同步");
        SalesOrderHeader failed = targetSalesOrder(customerCode, "同步失败");
        SalesOrderHeader synced = targetSalesOrder(customerCode, "已同步");
        targetSalesOrder(customerCode, "同步中");
        targetSalesOrder(customerCode, "结果待确认");
        SalesOrderHeader notReleased = targetSalesOrder(customerCode, "未同步");
        notReleased.setStatus(QuoteStatusWorkflow.QUOTED);
        headerMapper.updateById(notReleased);

        PageResult<SalesOrderU9SyncOrderRow> page = appendSalesOrderService.listAppendTargetSalesOrders(
                customerCode, null, null, QuoteStatusWorkflow.RELEASED, 1, 100);

        assertThat(page.getRecords())
                .extracting(SalesOrderU9SyncOrderRow::getSalesOrderId)
                .contains(nullStatus.getId(), blankStatus.getId(), unsynced.getId(), failed.getId())
                .doesNotContain(synced.getId(), notReleased.getId());

        PageResult<SalesOrderU9SyncOrderRow> rejectedStatus = appendSalesOrderService.listAppendTargetSalesOrders(
                customerCode, null, null, QuoteStatusWorkflow.QUOTED, 1, 100);
        assertThat(rejectedStatus.getRecords()).isEmpty();
    }

    private SalesOrderHeader targetSalesOrder(String customerCode, String u9SyncStatus) {
        return targetSalesOrder(customerCode, u9SyncStatus, "TARGET-PO", "TARGET-INTERNAL");
    }

    private SalesOrderHeader targetSalesOrder(
            String customerCode,
            String u9SyncStatus,
            String customerOrderNo,
            String internalProjectNo) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setSalesOrderNo("SO-APPEND-" + System.nanoTime());
        header.setDocumentNo(header.getSalesOrderNo());
        header.setDocumentTypeCode("SO06");
        header.setCustomerCode(customerCode);
        header.setCustomerShortName("追加客户简称");
        header.setCustomerName("追加客户");
        header.setSourceType("DIRECT_IMPORT");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 20));
        header.setCustomerOrderNo(customerOrderNo);
        header.setCustomerProjectNo("TARGET-CP");
        header.setCustomerOrderTitle("TARGET-TITLE");
        header.setOrderType("备货订单");
        header.setInternalProjectNo(internalProjectNo);
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(u9SyncStatus);
        header.setU9SyncMessage("旧 U9 失败摘要");
        header.setU9SyncAttemptId("OLD-ATTEMPT");
        header.setU9SyncRequestHash("OLD-HASH");
        header.setU9SyncStartedAt(LocalDateTime.of(2026, 8, 1, 8, 0));
        header.setU9SyncFinishedAt(LocalDateTime.of(2026, 8, 1, 8, 1));
        header.setTotalItemCount(0);
        header.setCreatedAt(LocalDateTime.now());
        header.setUpdatedAt(LocalDateTime.now());
        headerMapper.insert(header);
        return header;
    }

    private SalesOrderItem existingSalesItem(SalesOrderHeader header, int lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(header.getId());
        item.setSourceType("DIRECT_IMPORT");
        item.setLineNo(lineNo);
        item.setPartNo("EXISTING-PART");
        item.setVersion("A");
        item.setDescription("已有销售订单行");
        item.setQty(1);
        item.setReleasedQty(1);
        item.setRemainingQty(0);
        item.setUnshippedQty(1);
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setUntaxedTotalPrice(new BigDecimal("10.00"));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        salesItemMapper.insert(item);
        return item;
    }

    private OrderRfq rfq(SalesOrderHeader target, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqNo);
        order.setCustomerCode(target.getCustomerCode());
        order.setCustomerShortName(target.getCustomerShortName());
        order.setCustomerName(target.getCustomerName());
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        return order;
    }

    private ItemQuoteVersion rfqItem(OrderRfq order, String partNo, int qty, int lineNo, boolean materialConfirmed) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setSalesOrderLineNo(lineNo);
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("追加提单测试零件");
        item.setQty(qty);
        item.setMaterialName("AL6061");
        item.setEngineeringMaterialName("铝合金");
        item.setMaterialShape("板材");
        item.setTaxIncludedUnitPrice(12.0);
        item.setTaxIncludedTotalPrice(12.0 * qty);
        item.setUntaxedUnitPrice(10.0);
        item.setUntaxedTotalPrice(10.0 * qty);
        item.setLeadTime("5");
        item.setDrawingUrl("/append/" + partNo + ".pdf");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        if (materialConfirmed) {
            item.setMaterialNo("MES-APPEND-001");
            item.setMaterialNoStatus("已确认");
            item.setMaterialNoSource("MES_TICKET");
            item.setMaterialNoSourceTicketId(888L);
            item.setMaterialNoSyncedAt(LocalDateTime.of(2026, 8, 20, 9, 0));
        }
        itemMapper.insert(item);
        return item;
    }

    private void assembly(int rfqId, int parentItemId, int childItemId) {
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setRfqId(rfqId);
        relation.setParentItemId(parentItemId);
        relation.setChildItemId(childItemId);
        relation.setChildQtyPerParent(1.0);
        relation.setSortNo(1);
        sourceAssemblyMapper.insert(relation);
    }

    private RfqReleaseAppendSalesOrderReq appendRequest(
            SalesOrderHeader target,
            OrderRfq rfq,
            ItemQuoteVersion first,
            ItemQuoteVersion second,
            String appendBatchKey) {
        RfqReleaseAppendSalesOrderReq req = new RfqReleaseAppendSalesOrderReq();
        req.setTargetSalesOrderId(target.getId());
        req.setAppendBatchKey(appendBatchKey);
        req.setRfqIds(List.of(rfq.getId()));
        req.setItems(second == null
                ? List.of(line(rfq.getId(), first.getId(), first.getQty()))
                : List.of(line(rfq.getId(), first.getId(), first.getQty()), line(rfq.getId(), second.getId(), second.getQty())));
        return req;
    }

    private RfqReleaseAppendSalesOrderReq.ReleaseItemReq line(int rfqId, int itemId, int qty) {
        RfqReleaseAppendSalesOrderReq.ReleaseItemReq line = new RfqReleaseAppendSalesOrderReq.ReleaseItemReq();
        line.setItemId(itemId);
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId((long) rfqId);
        line.setSourceItemId((long) itemId);
        line.setAcceptedQty(qty);
        return line;
    }

    private List<SalesOrderItem> salesItems(Long salesOrderId) {
        return salesItemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                .eq("sales_order_id", salesOrderId)
                .orderByAsc("line_no")
                .orderByAsc("id"));
    }

    private long ticketCount(Integer rfqId) {
        return ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>().eq("rfq_id", rfqId));
    }
}
