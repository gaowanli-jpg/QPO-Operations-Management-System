package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.OrderStatusManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusManagementTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V2__qpo_baseline_seed_data.sql");

    @Test
    void freezesAllowedStatusManagementTransitions() {
        assertThat(QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.QUOTED, false))
                .as("已报价可以退回已核价，保留报价数据作为草稿")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.PRICED, false))
                .as("已核价可以退回未核价，保留核价数据作为草稿")
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.UNPRICED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.PRICED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.QUOTED, false)).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        assertThat(QuoteStatusWorkflow.nextAfterRestore(QuoteStatusWorkflow.DISCARDED))
                .as("废弃恢复统一进入已核价，由后续报价保存校验数据完整性")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    void rejectsDiscardForReleasedOrders() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.PARTIALLY_RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
    }

    @Test
    void rejectsRollbackForReleasedOrders() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.PARTIALLY_RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only quoted orders");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.RELEASED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only priced orders");
    }

    @Test
    void rejectsDiscardOrRollbackWhenDeliveryTicketsExist() {
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterDiscard(QuoteStatusWorkflow.QUOTED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackQuoted(QuoteStatusWorkflow.QUOTED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
        assertThatThrownBy(() -> QuoteStatusWorkflow.nextAfterRollbackPriced(QuoteStatusWorkflow.PRICED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery tickets");
    }

    @Test
    void rollbackQuotedUpdatesOnlyStatusesAndWritesAuditLog() {
        Fixture fixture = fixture(order(10, QuoteStatusWorkflow.QUOTED), List.of(item(101, QuoteStatusWorkflow.QUOTED)), 0L);

        OrderStatusChangeResult result = fixture.service.rollbackQuoted(10, req("重新确认报价交期"));

        assertThat(result.getFromStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(result.getToStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(result.getUpdatedItemCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("已报价", "已核价");
        verify(fixture.dataScopeService).assertCanAccessOrder(fixture.order);
        verify(fixture.orderMapper).selectByIdForUpdate(10);
        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        verify(fixture.orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);

        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        ItemQuoteVersion updated = itemCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(updated.getQuoteLeadTime()).isEqualTo("2026-03-20");
        assertThat(updated.getDrawingUrl()).isEqualTo("/uploads/drawing.pdf");
        assertThat(updated.getTaxIncludedUnitPrice()).isEqualTo(12.3);
        assertThat(updated.getProcessRoute()).isEqualTo(List.of(Map.of("processName", "表面处理")));
        verify(fixture.auditService).record(
                eq("ORDER_STATUS_ROLLBACK_QUOTED"),
                eq("order_rfq"),
                eq(10),
                any(),
                any(),
                eq("ORDER"),
                eq("SUCCESS"),
                contains("已报价"),
                isNull()
        );
    }

    @Test
    void rollbackQuotedTreatsOnlyCurrentItemReleasedStatusAsProtected() {
        ItemQuoteVersion currentReleased = item(107, QuoteStatusWorkflow.ITEM_RELEASED);
        ItemQuoteVersion legacyGeneratedOrderStatus = item(108, "\u5df2\u751f\u6210\u63d0\u5355");
        ItemQuoteVersion legacyPartialItemStatus = item(109, "\u5df2\u90e8\u5206\u63d0\u5355");
        Fixture fixture = fixture(
                order(16, QuoteStatusWorkflow.QUOTED),
                List.of(currentReleased, legacyGeneratedOrderStatus, legacyPartialItemStatus),
                0L);

        OrderStatusChangeResult result = fixture.service.rollbackQuoted(16, req("cleanup legacy item statuses"));

        assertThat(result.getUpdatedItemCount()).isEqualTo(2);
        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper, times(2)).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ItemQuoteVersion::getId)
                .containsExactly(108, 109);
        assertThat(itemCaptor.getAllValues())
                .extracting(ItemQuoteVersion::getStatus)
                .containsExactly(QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.PRICED);
        assertThat(currentReleased.getStatus()).isEqualTo(QuoteStatusWorkflow.ITEM_RELEASED);
    }

    @Test
    void rollbackPricedUpdatesOrderAndItemsToUnpriced() {
        Fixture fixture = fixture(order(11, QuoteStatusWorkflow.PRICED), List.of(item(102, QuoteStatusWorkflow.PRICED)), 0L);

        OrderStatusChangeResult result = fixture.service.rollbackPriced(11, req("工程重新核价"));

        assertThat(result.getToStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        verify(fixture.orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        verify(fixture.auditService).record(eq("ORDER_STATUS_ROLLBACK_PRICED"), eq("order_rfq"), eq(11), any(), any(), eq("ORDER"), eq("SUCCESS"), contains("未核价"), isNull());
    }

    @Test
    void discardNonReleasedOrderUpdatesOrderAndItemsToDiscarded() {
        Fixture fixture = fixture(order(12, QuoteStatusWorkflow.PRICED), List.of(item(103, QuoteStatusWorkflow.PRICED)), 0L);

        OrderStatusChangeResult result = fixture.service.discard(12, req("客户取消"));

        assertThat(result.getToStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        verify(fixture.orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        verify(fixture.auditService).record(eq("ORDER_DISCARD"), eq("order_rfq"), eq(12), any(), any(), eq("ORDER"), eq("SUCCESS"), contains("废弃"), isNull());
    }

    @Test
    void restoreDiscardedOrderUpdatesOrderAndItemsToPriced() {
        Fixture fixture = fixture(order(13, QuoteStatusWorkflow.DISCARDED), List.of(item(104, QuoteStatusWorkflow.DISCARDED)), 0L);

        OrderStatusChangeResult result = fixture.service.restore(13, req("恢复处理"));

        assertThat(result.getToStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        verify(fixture.orderMapper).updateById(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        verify(fixture.auditService).record(eq("ORDER_RESTORE"), eq("order_rfq"), eq(13), any(), any(), eq("ORDER"), eq("SUCCESS"), contains("恢复"), isNull());
    }

    @Test
    void rejectsReleasedOrTicketedOrdersWithoutMutatingAnything() {
        Fixture released = fixture(order(14, QuoteStatusWorkflow.PARTIALLY_RELEASED), List.of(item(105, QuoteStatusWorkflow.QUOTED)), 0L);
        assertThatThrownBy(() -> released.service.discard(14, req("不能废弃")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot be discarded");
        verify(released.orderMapper, never()).updateById(any());
        verify(released.itemMapper, never()).updateById(any());

        Fixture ticketed = fixture(order(15, QuoteStatusWorkflow.QUOTED), List.of(item(106, QuoteStatusWorkflow.QUOTED)), 1L);
        assertThatThrownBy(() -> ticketed.service.rollbackQuoted(15, req("已有提单")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("delivery tickets");
        verify(ticketed.orderMapper, never()).updateById(any());
        verify(ticketed.itemMapper, never()).updateById(any());
    }

    @Test
    void permissionMigrationAddsOrderStatusActionsUnderOrderManagement() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("market:order:view")
                .contains("market:order:rollback-quoted")
                .contains("market:order:rollback-priced")
                .contains("market:order:discard")
                .contains("market:order:restore")
                .contains("已报价退回已核价")
                .contains("已核价退回未核价")
                .contains("废弃订单")
                .contains("恢复废弃订单")
                .contains("SUPER_ADMIN")
                .contains("MARKET_MANAGER");
    }

    @Test
    void orderManagementControllerExposesSecuredStatusActionSkeletons() throws IOException {
        String controller = source("controller/OrderManagementController.java");

        assertThat(controller)
                .contains("OrderStatusManagementService")
                .contains("OrderStatusChangeReq")
                .contains("@PostMapping(\"/orders/{rfqId}/rollback-quoted\")")
                .contains("@RequirePermission(\"market:order:rollback-quoted\")")
                .contains("@PostMapping(\"/orders/{rfqId}/rollback-priced\")")
                .contains("@RequirePermission(\"market:order:rollback-priced\")")
                .contains("@PostMapping(\"/orders/{rfqId}/discard\")")
                .contains("@RequirePermission(\"market:order:discard\")")
                .contains("@PostMapping(\"/orders/{rfqId}/restore\")")
                .contains("@RequirePermission(\"market:order:restore\")");
    }

    @Test
    void statusManagementDtoAndServiceSkeletonsExist() throws IOException {
        String req = source("dto/OrderStatusChangeReq.java");
        String result = source("dto/OrderStatusChangeResult.java");
        String service = source("service/OrderStatusManagementService.java");

        assertThat(req)
                .contains("class OrderStatusChangeReq")
                .contains("private String reason");
        assertThat(result)
                .contains("class OrderStatusChangeResult")
                .contains("private Integer rfqId")
                .contains("private String fromStatus")
                .contains("private String toStatus")
                .contains("private Integer updatedItemCount")
                .contains("private String message");
        assertThat(service)
                .contains("class OrderStatusManagementService")
                .contains("@Transactional")
                .contains("selectByIdForUpdate")
                .contains("assertCanAccessOrder")
                .contains("selectCount")
                .contains("updateById")
                .contains("auditService.record")
                .contains("Order status change completed")
                .contains("rollbackQuoted")
                .contains("rollbackPriced")
                .contains("discard")
                .contains("restore");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private Fixture fixture(OrderRfq order, List<ItemQuoteVersion> items, Long ticketCount) {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        when(orderMapper.selectByIdForUpdate(order.getId())).thenReturn(order);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
        when(ticketMapper.selectCount(any(QueryWrapper.class))).thenReturn(ticketCount);

        OrderStatusManagementService service = new OrderStatusManagementService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return new Fixture(service, orderMapper, itemMapper, ticketMapper, dataScopeService, auditService, order);
    }

    private OrderRfq order(Integer id, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerName("客户A");
        order.setStatus(status);
        return order;
    }

    private ItemQuoteVersion item(Integer id, String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(10);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setStatus(status);
        item.setQuoteLeadTime("2026-03-20");
        item.setDrawingUrl("/uploads/drawing.pdf");
        item.setTaxIncludedUnitPrice(12.3);
        item.setTaxIncludedTotalPrice(24.6);
        item.setProcessRoute(List.of(Map.of("processName", "表面处理")));
        return item;
    }

    private OrderStatusChangeReq req(String reason) {
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(reason);
        return req;
    }

    private record Fixture(
            OrderStatusManagementService service,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            OrderRfq order) {
    }
}
