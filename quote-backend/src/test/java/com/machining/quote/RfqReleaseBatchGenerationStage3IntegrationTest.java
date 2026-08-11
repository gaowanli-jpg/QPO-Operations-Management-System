package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-stage3/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-stage3/backups/"
})
@Transactional
class RfqReleaseBatchGenerationStage3IntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;

    @Test
    void generatesOneAtomicTicketBatchForMultipleRfqsAndDoesNotDuplicateCompletedRetry() {
        String customerName = "阶段3同客户-" + System.nanoTime();
        CustomerMaster customer = customer(customerName);
        OrderRfq firstOrder = order(customer, "STAGE3-A");
        OrderRfq secondOrder = order(customer, "STAGE3-B");
        ItemQuoteVersion firstItem = item(firstOrder, "STAGE3-PART-A", 2);
        ItemQuoteVersion secondItem = item(secondOrder, "STAGE3-PART-B", 1);
        DeliveryGenerateReq req = request(firstOrder, firstItem, secondOrder, secondItem);

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSourceRfqIds()).containsExactly(firstOrder.getId(), secondOrder.getId());
        assertThat(result.getTicketCount()).isEqualTo(2);
        assertThat(result.getTicketIds()).hasSize(2);
        assertThat(ticketCount(firstOrder.getId(), secondOrder.getId())).isEqualTo(2);

        DeliveryBatchResultDTO replay = deliveryService.generateRfqReleaseBatch(req);
        assertThat(replay.getTicketIds()).containsExactlyElementsOf(result.getTicketIds());
        assertThat(ticketCount(firstOrder.getId(), secondOrder.getId())).isEqualTo(2);
    }

    private long ticketCount(Integer firstRfqId, Integer secondRfqId) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.in("rfq_id", List.of(firstRfqId, secondRfqId));
        return ticketMapper.selectCount(query);
    }

    private OrderRfq order(CustomerMaster customer, String suffix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(suffix + "-" + System.nanoTime());
        order.setCustomerCode(customer.getCustomerCode());
        order.setCustomerShortName(customer.getCustomerShortName());
        order.setCustomerName(customer.getCustomerName());
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        return order;
    }

    private CustomerMaster customer(String customerName) {
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode("STAGE3-" + System.nanoTime());
        customer.setCustomerShortName(customerName);
        customer.setCustomerName(customerName);
        customer.setEnabled(true);
        customerMapper.insert(customer);
        return customer;
    }

    private ItemQuoteVersion item(OrderRfq order, String partNo, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("阶段3批次测试零件");
        item.setQty(qty);
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(10.0 * qty);
        item.setLeadTime("2026-08-20");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.insert(item);
        return item;
    }

    private DeliveryGenerateReq request(
            OrderRfq firstOrder,
            ItemQuoteVersion firstItem,
            OrderRfq secondOrder,
            ItemQuoteVersion secondItem) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(firstOrder.getId(), secondOrder.getId()));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 7));
        req.setCustomerOrderTitle("阶段3客户抬头");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("STAGE3-INTERNAL");
        req.setCustomerOrderNo("STAGE3-CUSTOMER-ORDER");
        req.setCustomerProjectNo("STAGE3-CUSTOMER-PROJECT");
        req.setArrangeType("计划");
        req.setReleaseBatchKey("stage3-" + System.nanoTime());
        req.setItems(List.of(
                line(firstOrder.getId(), firstItem.getId(), firstItem.getQty()),
                line(secondOrder.getId(), secondItem.getId(), secondItem.getQty())));
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
}
