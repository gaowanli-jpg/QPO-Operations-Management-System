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
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryAutoMesMaterialSyncService;
import com.machining.quote.service.DeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-auto-mes/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-auto-mes/backups/"
})
@Transactional
class RfqReleaseAutoMesMaterialSyncTest {
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
    @MockBean
    private DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService;

    @Test
    void firstRfqReleaseSubmitsGeneratedTicketIdsAndReplayDoesNotSubmitAgain() {
        Fixture fixture = fixture("AUTO-MES-PRIMARY", 2);

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(fixture.request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isIdempotentReplay()).isFalse();
        assertThat(result.getTicketIds()).hasSize(1);
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.RFQ),
                eq(result.getSalesOrderId()),
                eq(result.getTicketIds()),
                eq("RFQ_RELEASE"));

        clearInvocations(autoMesMaterialSyncService);
        DeliveryBatchResultDTO replay = deliveryService.generateRfqReleaseBatch(fixture.request());

        assertThat(replay.isIdempotentReplay()).isTrue();
        assertThat(replay.getTicketIds()).containsExactlyElementsOf(result.getTicketIds());
        verifyNoInteractions(autoMesMaterialSyncService);
    }

    @Test
    void autoMesSubmitFailureDoesNotRollbackRfqReleaseTickets() {
        Fixture fixture = fixture("AUTO-MES-FAILURE", 3);
        doThrow(new RuntimeException("auto mes submit failed"))
                .when(autoMesMaterialSyncService)
                .submitAfterCommit(any(), anyLong(), anyList(), anyString());

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(fixture.request());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTicketIds()).hasSize(1);
        assertThat(ticketMapper.selectById(result.getTicketIds().get(0))).isNotNull();
        assertThat(ticketCount(fixture.order().getId())).isEqualTo(1);
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.RFQ),
                eq(result.getSalesOrderId()),
                eq(result.getTicketIds()),
                eq("RFQ_RELEASE"));
    }

    @Test
    void legacyGenerateTicketsRfqBranchAlsoSubmitsGeneratedTicketIds() {
        Fixture fixture = fixture("AUTO-MES-LEGACY", 4);
        fixture.request().setReleaseBatchKey(null);

        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(fixture.request());

        List<Integer> ticketIds = tickets.stream().map(OrderDeliveryTicket::getId).toList();
        assertThat(ticketIds).hasSize(1);
        ArgumentCaptor<Long> sourceOrderCaptor = ArgumentCaptor.forClass(Long.class);
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.RFQ),
                sourceOrderCaptor.capture(),
                eq(ticketIds),
                eq("RFQ_RELEASE"));
        assertThat(sourceOrderCaptor.getValue()).isPositive();
    }

    @Test
    void replayThroughLegacyGenerateTicketsDoesNotSubmitAgain() {
        Fixture fixture = fixture("AUTO-MES-LEGACY-REPLAY", 5);
        deliveryService.generateTickets(fixture.request());
        clearInvocations(autoMesMaterialSyncService);

        List<OrderDeliveryTicket> replayTickets = deliveryService.generateTickets(fixture.request());

        assertThat(replayTickets).hasSize(1);
        verify(autoMesMaterialSyncService, never()).submitAfterCommit(any(), anyLong(), anyList(), anyString());
    }

    private long ticketCount(Integer rfqId) {
        return ticketMapper.selectCount(new QueryWrapper<OrderDeliveryTicket>().eq("rfq_id", rfqId));
    }

    private Fixture fixture(String prefix, int qty) {
        String suffix = prefix + "-" + System.nanoTime();
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode("CUST-" + suffix);
        customer.setCustomerShortName("自动MES客户");
        customer.setCustomerName("自动MES客户-" + suffix);
        customer.setEnabled(true);
        customerMapper.insert(customer);

        OrderRfq order = new OrderRfq();
        order.setRfqNo("RFQ-" + suffix);
        order.setCustomerCode(customer.getCustomerCode());
        order.setCustomerShortName(customer.getCustomerShortName());
        order.setCustomerName(customer.getCustomerName());
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("PART-" + suffix);
        item.setVersion("A");
        item.setDescription("自动MES触发测试零件");
        item.setQty(qty);
        item.setTaxIncludedUnitPrice(12.0);
        item.setTaxIncludedTotalPrice(12.0 * qty);
        item.setUntaxedUnitPrice(10.0);
        item.setUntaxedTotalPrice(10.0 * qty);
        item.setLeadTime("5");
        item.setDrawingUrl("/uploads/test/PART-" + suffix + ".pdf");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        itemMapper.insert(item);

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 17));
        req.setCustomerOrderTitle("自动MES客户抬头");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("AUTO-MES-INTERNAL");
        req.setCustomerOrderNo("AUTO-MES-CUSTOMER-ORDER");
        req.setCustomerProjectNo("AUTO-MES-CUSTOMER-PROJECT");
        req.setArrangeType("计划");
        req.setReleaseBatchKey("auto-mes-" + suffix);
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId(order.getId().longValue());
        line.setSourceItemId(item.getId().longValue());
        line.setAcceptedQty(qty);
        req.setItems(List.of(line));
        return new Fixture(order, item, req);
    }

    private record Fixture(OrderRfq order, ItemQuoteVersion item, DeliveryGenerateReq request) {
    }
}
