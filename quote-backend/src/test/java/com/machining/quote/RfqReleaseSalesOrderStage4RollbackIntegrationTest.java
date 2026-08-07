package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-stage4-rollback/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-stage4-rollback/backups/"
})
class RfqReleaseSalesOrderStage4RollbackIntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;
    @MockBean
    private AuditService auditService;

    private Long customerId;
    private Integer rfqId;
    private Integer itemId;
    private String batchKey;

    @AfterEach
    void cleanup() {
        if (batchKey != null) {
            List<SalesOrderHeader> headers = headerMapper.selectList(new QueryWrapper<SalesOrderHeader>()
                    .eq("source_release_batch_key", batchKey));
            headers.forEach(header -> headerMapper.deleteById(header.getId()));
        }
        if (rfqId != null) {
            ticketMapper.delete(new QueryWrapper<OrderDeliveryTicket>().eq("rfq_id", rfqId));
        }
        if (itemId != null) {
            itemMapper.deleteById(itemId);
        }
        if (rfqId != null) {
            orderMapper.deleteById(rfqId);
        }
        if (customerId != null) {
            customerMapper.deleteById(customerId);
        }
    }

    @Test
    void rollsBackHeaderItemsSourcesTicketsAndRfqUpdatesWhenFinalAuditFails() {
        String suffix = String.valueOf(System.nanoTime());
        batchKey = "stage4-rollback-" + suffix;
        String customerName = "阶段4回滚客户-" + suffix;
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode("ROLLBACK-" + suffix);
        customer.setCustomerShortName("回滚-" + suffix);
        customer.setCustomerName(customerName);
        customer.setEnabled(true);
        customerMapper.insert(customer);
        customerId = customer.getId();

        OrderRfq order = new OrderRfq();
        order.setRfqNo("ROLLBACK-RFQ-" + suffix);
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        rfqId = order.getId();

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("ROLLBACK-PART-" + suffix);
        item.setVersion("A");
        item.setDescription("阶段4事务回滚零件");
        item.setQty(2);
        item.setTaxIncludedUnitPrice(10.0);
        item.setTaxIncludedTotalPrice(20.0);
        item.setLeadTime("5");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.insert(item);
        itemId = item.getId();

        DeliveryGenerateReq req = request(order.getId(), item.getId(), batchKey);
        doThrow(new RuntimeException("stage4 forced audit failure"))
                .when(auditService)
                .record(eq("DELIVERY_TICKET_GENERATE"), eq("sales_order_header"), any(), isNull(), any());

        assertThatThrownBy(() -> deliveryService.generateRfqReleaseBatch(req))
                .hasMessageContaining("stage4 forced audit failure");

        assertThat(headerMapper.selectCount(new QueryWrapper<SalesOrderHeader>()
                .eq("source_release_batch_key", batchKey))).isZero();
        assertThat(salesItemMapper.selectCount(new QueryWrapper<SalesOrderItem>()
                .eq("source_type", "RFQ")
                .eq("source_item_id", item.getId()))).isZero();
        assertThat(ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>()
                .eq("rfq_id", order.getId()))).isZero();
        ItemQuoteVersion reloadedItem = itemMapper.selectById(item.getId());
        assertThat(reloadedItem.getSalesOrderDate()).isNull();
        assertThat(reloadedItem.getCustomerOrderNo()).isNull();
        assertThat(reloadedItem.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(orderMapper.selectById(order.getId()).getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    private DeliveryGenerateReq request(int orderId, int itemId, String releaseBatchKey) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(orderId));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 8));
        req.setCustomerOrderTitle("回滚测试抬头");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("ROLLBACK-INTERNAL");
        req.setCustomerOrderNo("ROLLBACK-CUSTOMER-ORDER");
        req.setReleaseBatchKey(releaseBatchKey);
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(itemId);
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId((long) orderId);
        line.setSourceItemId((long) itemId);
        line.setAcceptedQty(2);
        req.setItems(List.of(line));
        return req;
    }
}
