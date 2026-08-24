package com.machining.quote;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.MarketController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemOrderFieldUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfqReleaseOrderFieldsStage2Test {
    @Test
    void endpointUsesReleaseCreatePermissionAndDedicatedOrderFieldsPath() throws Exception {
        Method endpoint = MarketController.class.getDeclaredMethod(
                "updateReleaseItemOrderFields", Integer.class, ReleaseItemOrderFieldUpdateReq.class);
        PostMapping mapping = endpoint.getAnnotation(PostMapping.class);
        RequirePermission permission = endpoint.getAnnotation(RequirePermission.class);

        assertThat(mapping.value()).containsExactly("/release/items/{itemId}/order-fields");
        assertThat(permission.value()).containsExactly("market:release:create");
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesAllSixFieldsAtomicallyAndAuditsOnlyTheRfqItem() {
        TestContext context = context();
        ItemQuoteVersion item = item(201, 101, 10);
        OrderDeliveryTicket historicalTicket = ticket(9001, 4);
        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(context.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(context.itemMapper.selectById(201)).thenReturn(item);
        when(context.ticketMapper.selectList(any())).thenReturn(List.of(historicalTicket));

        ReleaseItemOrderFieldUpdateReq req = validRequest();
        Map<String, Object> result = context.service.updateReleaseItemOrderFields(201, req);

        assertThat(result).containsEntry("success", true);
        Map<String, Object> row = (Map<String, Object>) result.get("data");
        assertThat(row)
                .containsEntry("salesOrderDate", LocalDate.of(2026, 8, 7))
                .containsEntry("customerOrderNo", "CO-001")
                .containsEntry("customerProjectNo", "CP-001")
                .containsEntry("customerOrderTitle", "Customer title")
                .containsEntry("orderType", "正式订单")
                .containsEntry("internalProjectNo", "IP-001");
        assertThat(item.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(item.getCustomerOrderNo()).isEqualTo("CO-001");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(item.getCustomerOrderTitle()).isEqualTo("Customer title");
        assertThat(item.getOrderType()).isEqualTo("正式订单");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-001");
        verify(context.itemMapper).updateById(item);
        verify(context.auditService).record(
                eq("RELEASE_ITEM_ORDER_FIELDS_UPDATE"),
                eq("item_quote_version"),
                eq(201),
                any(Map.class),
                any(Map.class));
        verify(context.ticketMapper, never()).updateById(any());
        assertThat(historicalTicket.getQty()).isEqualTo(4);
    }

    @Test
    void blankValuesClearItemOverridesWithoutCopyingOrderDefaults() {
        TestContext context = context();
        ItemQuoteVersion item = item(201, 101, 10);
        item.setSalesOrderDate(LocalDate.of(2025, 1, 1));
        item.setCustomerOrderNo("OLD-CO");
        item.setCustomerProjectNo("OLD-CP");
        item.setCustomerOrderTitle("OLD-TITLE");
        item.setOrderType("预测订单");
        item.setInternalProjectNo("OLD-IP");
        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(context.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(context.itemMapper.selectById(201)).thenReturn(item);
        when(context.ticketMapper.selectList(any())).thenReturn(List.of());

        ReleaseItemOrderFieldUpdateReq clearRequest = new ReleaseItemOrderFieldUpdateReq();
        clearRequest.setRfqId(101);
        context.service.updateReleaseItemOrderFields(201, clearRequest);

        assertThat(item.getSalesOrderDate()).isNull();
        assertThat(item.getCustomerOrderNo()).isNull();
        assertThat(item.getCustomerProjectNo()).isNull();
        assertThat(item.getCustomerOrderTitle()).isNull();
        assertThat(item.getOrderType()).isNull();
        assertThat(item.getInternalProjectNo()).isNull();
    }

    @Test
    void partialAndFullReleaseRemainEditableWithoutChangingTicketSnapshot() {
        TestContext partial = context();
        ItemQuoteVersion partialItem = item(201, 101, 10);
        when(partial.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(partial.itemMapper.selectByIdForUpdate(201)).thenReturn(partialItem);
        when(partial.itemMapper.selectById(201)).thenReturn(partialItem);
        when(partial.ticketMapper.selectList(any())).thenReturn(List.of(ticket(9001, 4)));

        partial.service.updateReleaseItemOrderFields(201, validRequest());
        verify(partial.itemMapper).updateById(partialItem);

        TestContext full = context();
        ItemQuoteVersion fullItem = item(202, 101, 10);
        when(full.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(full.itemMapper.selectByIdForUpdate(202)).thenReturn(fullItem);
        when(full.itemMapper.selectById(202)).thenReturn(fullItem);
        OrderDeliveryTicket fullTicket = ticket(9002, 10);
        fullTicket.setItemId(202);
        when(full.ticketMapper.selectList(any())).thenReturn(List.of(fullTicket));

        full.service.updateReleaseItemOrderFields(202, validRequest());
        verify(full.itemMapper).updateById(fullItem);
        verify(full.ticketMapper, never()).updateById(any());
        assertThat(fullTicket.getQty()).isEqualTo(10);
    }

    @Test
    void validatesOrderTypeAndAllDatabaseFieldLengthsBeforeMutatingItem() {
        TestContext invalidType = context();
        ItemQuoteVersion item = item(201, 101, 10);
        when(invalidType.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(invalidType.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(invalidType.ticketMapper.selectList(any())).thenReturn(List.of());
        ReleaseItemOrderFieldUpdateReq invalidTypeRequest = validRequest();
        invalidTypeRequest.setOrderType("普通订单");

        assertThatThrownBy(() -> invalidType.service.updateReleaseItemOrderFields(201, invalidTypeRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("订单类型");
        verify(invalidType.itemMapper, never()).updateById(any());

        TestContext invalidLength = context();
        ItemQuoteVersion lengthItem = item(201, 101, 10);
        when(invalidLength.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        when(invalidLength.itemMapper.selectByIdForUpdate(201)).thenReturn(lengthItem);
        when(invalidLength.ticketMapper.selectList(any())).thenReturn(List.of());
        ReleaseItemOrderFieldUpdateReq invalidLengthRequest = validRequest();
        invalidLengthRequest.setCustomerOrderNo("X".repeat(121));

        assertThatThrownBy(() -> invalidLength.service.updateReleaseItemOrderFields(201, invalidLengthRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("客户订单号");
        verify(invalidLength.itemMapper, never()).updateById(any());
    }

    @Test
    void itemIdUpdateCannotCrossRfqOrDataScope() {
        TestContext wrongRfq = context();
        when(wrongRfq.orderMapper.selectByIdForUpdate(101)).thenReturn(order(101));
        ItemQuoteVersion otherItem = item(202, 202, 10);
        when(wrongRfq.itemMapper.selectByIdForUpdate(202)).thenReturn(otherItem);

        ReleaseItemOrderFieldUpdateReq request = validRequest();
        request.setRfqId(101);
        assertThatThrownBy(() -> wrongRfq.service.updateReleaseItemOrderFields(202, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("selected RFQ");
        verify(wrongRfq.itemMapper, never()).updateById(any());

        TestContext denied = context();
        OrderRfq deniedOrder = order(101);
        when(denied.orderMapper.selectByIdForUpdate(101)).thenReturn(deniedOrder);
        doThrow(new RuntimeException("No data permission for order 101"))
                .when(denied.dataScopeService).assertCanAccessOrder(deniedOrder);

        assertThatThrownBy(() -> denied.service.updateReleaseItemOrderFields(201, validRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
        verify(denied.itemMapper, never()).selectByIdForUpdate(any());
    }

    private TestContext context() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        InternalTargetLeadTimeService service = new InternalTargetLeadTimeService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "orderItemPageService", mock(OrderItemPageService.class));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "businessFileService", mock(BusinessFileService.class));
        return new TestContext(service, orderMapper, itemMapper, ticketMapper, auditService, dataScopeService);
    }

    private ReleaseItemOrderFieldUpdateReq validRequest() {
        ReleaseItemOrderFieldUpdateReq request = new ReleaseItemOrderFieldUpdateReq();
        request.setRfqId(101);
        request.setSalesOrderDate(LocalDate.of(2026, 8, 7));
        request.setCustomerOrderNo("  CO-001  ");
        request.setCustomerProjectNo(" CP-001 ");
        request.setCustomerOrderTitle(" Customer title ");
        request.setOrderType("正式订单");
        request.setInternalProjectNo(" IP-001 ");
        return request;
    }

    private OrderRfq order(Integer id) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setDescription("release item");
        item.setQty(qty);
        item.setTaxIncludedUnitPrice(10.0);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private OrderDeliveryTicket ticket(Integer id, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(101);
        ticket.setItemId(201);
        ticket.setQty(qty);
        ticket.setStartDate(LocalDate.of(2025, 1, 1));
        return ticket;
    }

    private record TestContext(
            InternalTargetLeadTimeService service,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            AuditService auditService,
            DataScopeService dataScopeService) {
    }
}
