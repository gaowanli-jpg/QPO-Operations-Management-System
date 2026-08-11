package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.RfqReleaseBatchValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RfqReleaseBatchValidationServiceTest {
    private RfqReleaseBatchValidationService service;
    private OrderRfqMapper orderMapper;
    private DataScopeService dataScopeService;

    @BeforeEach
    void setUp() {
        service = new RfqReleaseBatchValidationService();
        orderMapper = mock(OrderRfqMapper.class);
        dataScopeService = mock(DataScopeService.class);
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
    }

    @Test
    void acceptsMultipleQuotedRfqsForOneCustomerAndHashesNormalizedRequest() {
        OrderRfq first = order(101, "客户A", QuoteStatusWorkflow.QUOTED);
        OrderRfq second = order(102, "客户A", QuoteStatusWorkflow.PARTIALLY_RELEASED);
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(first, second));
        when(orderMapper.selectBatchIds(List.of(102, 101))).thenReturn(List.of(first, second));

        RfqReleaseBatchValidationService.BatchContext context = service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 2), line(102, 2001, 1))));
        RfqReleaseBatchValidationService.BatchContext reordered = service.validate(request(
                List.of(102, 101),
                List.of(line(102, 2001, 1), line(101, 1001, 2))));

        assertThat(context.customerName()).isEqualTo("客户A");
        assertThat(context.rfqIds()).containsExactly(101, 102);
        assertThat(context.requestHash()).hasSize(64).isEqualTo(reordered.requestHash());
    }

    @Test
    void rejectsCrossCustomerAndNonQuotedBatches() {
        OrderRfq first = order(101, "客户A", QuoteStatusWorkflow.QUOTED);
        OrderRfq second = order(102, "客户B", QuoteStatusWorkflow.QUOTED);
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 1), line(102, 2001, 1)))))
                .hasMessageContaining("同一客户");

        second.setCustomerName("客户A");
        second.setStatus(QuoteStatusWorkflow.PRICED);
        assertThatThrownBy(() -> service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 1), line(102, 2001, 1)))))
                .hasMessageContaining("保存报价单");
    }

    @Test
    void hidesUnauthorizedRfqIdentityAndRejectsItemOwnershipMismatch() {
        OrderRfq first = order(101, "客户A", QuoteStatusWorkflow.QUOTED);
        OrderRfq second = order(102, "客户A", QuoteStatusWorkflow.QUOTED);
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("No data permission for order 102"))
                .when(dataScopeService).assertCanAccessOrder(second);

        assertThatThrownBy(() -> service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 1), line(102, 2001, 1)))))
                .hasMessage("议价提单批次包含无权访问的询价单")
                .hasMessageNotContaining("102");

        ReflectionTestUtils.setField(service, "dataScopeService", null);
        RfqReleaseBatchValidationService.BatchContext context = service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 1), line(102, 2001, 1))));
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(1001);
        item.setRfqId(102);
        assertThatThrownBy(() -> service.validateLockedItem(line(101, 1001, 1), item, context))
                .hasMessageContaining("不属于来源询价单");
    }

    @Test
    void rejectsRfqWithoutAnySelectedItem() {
        OrderRfq first = order(101, "客户A", QuoteStatusWorkflow.QUOTED);
        OrderRfq second = order(102, "客户A", QuoteStatusWorkflow.QUOTED);
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.validate(request(
                List.of(101, 102),
                List.of(line(101, 1001, 1)))))
                .hasMessage("每个询价单至少选择一个提单零件");
    }

    private DeliveryGenerateReq request(
            List<Integer> rfqIds,
            List<DeliveryGenerateReq.DeliveryItemReq> items) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(rfqIds);
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 7));
        req.setCustomerOrderTitle("客户正式订单");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("INTERNAL-001");
        req.setCustomerOrderNo("CUSTOMER-001");
        req.setCustomerProjectNo("CUSTOMER-PROJECT-001");
        req.setArrangeType("计划");
        req.setReleaseBatchKey("batch-stage3-001");
        req.setItems(items);
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

    private OrderRfq order(int id, String customerName, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerCode("C-" + customerName);
        order.setCustomerShortName(customerName);
        order.setCustomerName(customerName);
        order.setStatus(status);
        return order;
    }
}
