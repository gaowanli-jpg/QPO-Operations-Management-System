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
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SourceItemMaterialNoSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/rfq-release-stage5/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/rfq-release-stage5/backups/"
})
@Transactional
class RfqReleaseMesMaterialDualWriteStage5IntegrationTest {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private SourceItemMaterialNoSyncService sourceSyncService;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;

    @Test
    void rfqTicketConfirmationUpdatesRfqAndDerivedSalesOrderItem() {
        Fixture fixture = derivedFixture();

        SourceItemMaterialNoSyncService.MaterialNoSyncResult result =
                sourceSyncService.syncConfirmedMaterialNo(
                        fixture.ticket(), "MES-STAGE5-001", "MES immediate success");

        assertConfirmed(itemMapper.selectById(fixture.rfqItem().getId()), "MES-STAGE5-001", fixture.ticket().getId());
        assertConfirmed(salesItemMapper.selectById(fixture.salesItem().getId()), "MES-STAGE5-001", fixture.ticket().getId());
        assertThat(result.rfqUpdatedCount()).isEqualTo(1);
        assertThat(result.salesOrderUpdatedCount()).isEqualTo(1);
        assertThat(result.idempotentCount()).isZero();
    }

    @Test
    void sameConfirmedMaterialNoIsIdempotentOnBothMainTables() {
        Fixture fixture = derivedFixture();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 8, 8, 9, 15);
        confirm(fixture.rfqItem(), "MES-STAGE5-IDEMPOTENT", fixture.ticket().getId(), confirmedAt, "first confirmation");
        confirm(fixture.salesItem(), "MES-STAGE5-IDEMPOTENT", fixture.ticket().getId(), confirmedAt, "first confirmation");

        sourceSyncService.syncConfirmedMaterialNo(
                fixture.ticket(), "MES-STAGE5-IDEMPOTENT", "callback replay must not alter tuple");

        ItemQuoteVersion rfqItem = itemMapper.selectById(fixture.rfqItem().getId());
        SalesOrderItem salesItem = salesItemMapper.selectById(fixture.salesItem().getId());
        assertThat(rfqItem.getMaterialNoSyncedAt()).isEqualTo(confirmedAt);
        assertThat(rfqItem.getMaterialNoMessage()).isEqualTo("first confirmation");
        assertThat(salesItem.getMaterialNoSyncedAt()).isEqualTo(confirmedAt);
        assertThat(salesItem.getMaterialNoMessage()).isEqualTo("first confirmation");
    }

    @Test
    void conflictingDerivedSalesOrderMaterialRejectsBothMainTableWrites() {
        Fixture fixture = derivedFixture();
        confirm(
                fixture.salesItem(),
                "MES-STAGE5-OLD",
                fixture.ticket().getId(),
                LocalDateTime.of(2026, 8, 8, 10, 30),
                "existing confirmation");

        assertThatThrownBy(() -> sourceSyncService.syncConfirmedMaterialNo(
                fixture.ticket(), "MES-STAGE5-NEW", "conflict"))
                .isInstanceOf(SourceItemMaterialNoSyncService.MaterialNoConflictException.class);

        assertThat(itemMapper.selectById(fixture.rfqItem().getId()).getMaterialNo()).isNull();
        assertThat(salesItemMapper.selectById(fixture.salesItem().getId()).getMaterialNo())
                .isEqualTo("MES-STAGE5-OLD");
    }

    private Fixture derivedFixture() {
        String suffix = String.valueOf(System.nanoTime());
        String customerName = "阶段5双写客户-" + suffix;
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode("STAGE5-" + suffix);
        customer.setCustomerShortName("阶段5客户");
        customer.setCustomerName(customerName);
        customer.setEnabled(true);
        customerMapper.insert(customer);

        OrderRfq order = new OrderRfq();
        order.setRfqNo("STAGE5-RFQ-" + suffix);
        order.setCustomerCode(customer.getCustomerCode());
        order.setCustomerShortName(customer.getCustomerShortName());
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setSalesOrderLineNo(1);
        item.setPartNo("STAGE5-PART-" + suffix);
        item.setVersion("A");
        item.setDescription("阶段5 MES 双向回写零件");
        item.setQty(2);
        item.setTaxIncludedUnitPrice(12.0);
        item.setTaxIncludedTotalPrice(24.0);
        item.setUntaxedUnitPrice(10.0);
        item.setUntaxedTotalPrice(20.0);
        item.setLeadTime("5");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setDrawingUrl("/stage5/" + item.getPartNo() + ".pdf");
        itemMapper.insert(item);

        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setRfqIds(List.of(order.getId()));
        req.setDocumentTypeCode("SO01");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 8));
        req.setCustomerOrderTitle("阶段5客户抬头");
        req.setOrderType("正式订单");
        req.setInternalProjectNo("STAGE5-INTERNAL");
        req.setCustomerOrderNo("STAGE5-CUSTOMER-ORDER");
        req.setReleaseBatchKey("stage5-dual-write-" + suffix);
        DeliveryGenerateReq.DeliveryItemReq line = new DeliveryGenerateReq.DeliveryItemReq();
        line.setItemId(item.getId());
        line.setSourceType(ReleaseSourceType.RFQ);
        line.setSourceOrderId(order.getId().longValue());
        line.setSourceItemId(item.getId().longValue());
        line.setAcceptedQty(item.getQty());
        req.setItems(List.of(line));

        DeliveryBatchResultDTO result = deliveryService.generateRfqReleaseBatch(req);
        OrderDeliveryTicket ticket = ticketMapper.selectById(result.getTicketIds().get(0));
        SalesOrderItem salesItem = salesItemMapper.selectOne(new QueryWrapper<SalesOrderItem>()
                .eq("source_ticket_id", ticket.getId()));
        return new Fixture(itemMapper.selectById(item.getId()), ticket, salesItem);
    }

    private void confirm(
            ItemQuoteVersion item,
            String materialNo,
            Integer ticketId,
            LocalDateTime confirmedAt,
            String message) {
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(SourceItemMaterialNoSyncService.MATERIAL_CONFIRMED);
        item.setMaterialNoSource(SourceItemMaterialNoSyncService.MATERIAL_SOURCE_MES_TICKET);
        item.setMaterialNoSourceTicketId(ticketId.longValue());
        item.setMaterialNoSyncedAt(confirmedAt);
        item.setMaterialNoMessage(message);
        itemMapper.updateById(item);
    }

    private void confirm(
            SalesOrderItem item,
            String materialNo,
            Integer ticketId,
            LocalDateTime confirmedAt,
            String message) {
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(SourceItemMaterialNoSyncService.MATERIAL_CONFIRMED);
        item.setMaterialNoSource(SourceItemMaterialNoSyncService.MATERIAL_SOURCE_MES_TICKET);
        item.setMaterialNoSourceTicketId(ticketId.longValue());
        item.setMaterialNoSyncedAt(confirmedAt);
        item.setMaterialNoMessage(message);
        salesItemMapper.updateById(item);
    }

    private void assertConfirmed(ItemQuoteVersion item, String materialNo, Integer ticketId) {
        assertThat(item.getMaterialNo()).isEqualTo(materialNo);
        assertThat(item.getMaterialNoStatus()).isEqualTo(SourceItemMaterialNoSyncService.MATERIAL_CONFIRMED);
        assertThat(item.getMaterialNoSource()).isEqualTo(SourceItemMaterialNoSyncService.MATERIAL_SOURCE_MES_TICKET);
        assertThat(item.getMaterialNoSourceTicketId()).isEqualTo(ticketId.longValue());
        assertThat(item.getMaterialNoSyncedAt()).isNotNull();
    }

    private void assertConfirmed(SalesOrderItem item, String materialNo, Integer ticketId) {
        assertThat(item.getMaterialNo()).isEqualTo(materialNo);
        assertThat(item.getMaterialNoStatus()).isEqualTo(SourceItemMaterialNoSyncService.MATERIAL_CONFIRMED);
        assertThat(item.getMaterialNoSource()).isEqualTo(SourceItemMaterialNoSyncService.MATERIAL_SOURCE_MES_TICKET);
        assertThat(item.getMaterialNoSourceTicketId()).isEqualTo(ticketId.longValue());
        assertThat(item.getMaterialNoSyncedAt()).isNotNull();
    }

    private record Fixture(
            ItemQuoteVersion rfqItem,
            OrderDeliveryTicket ticket,
            SalesOrderItem salesItem) {
    }
}
