package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.wms.WmsReservationFingerprintService;
import com.machining.quote.service.wms.WmsReservationPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WmsReservationPlanServiceTest {
    @Test
    void prepareResolvesSalesOrderBySourceTicketThenDirectFallbackAndSaleNoPriority() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, null, null));
        fixture.sourceTicketItem(101, item(1001L, 501L, 10));
        fixture.header(header(501L, "SO-FALLBACK", "DOC-501"));

        fixture.ticket(ticket(102, "MAT-B", 3, "SALES_ORDER", 1002L));
        fixture.directItem(item(1002L, 502L, 20));
        fixture.header(header(502L, "SO-502", " "));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(101, 102));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups())
                .extracting(WmsReservationPlanService.ReservationGroup::getSaleNo)
                .containsExactly("DOC-501", "SO-502");
    }

    @Test
    void prepareFailsAmbiguousSourceAndMissingSalesOrderHeader() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, "SALES_ORDER", 1002L));
        fixture.sourceTicketItem(101, item(1001L, 501L, 10));
        fixture.directItem(item(1002L, 502L, 20));
        fixture.header(header(501L, "SO-501", null));
        fixture.header(header(502L, "SO-502", null));

        fixture.ticket(ticket(102, "MAT-B", 3, "SALES_ORDER", 1003L));
        fixture.directItem(item(1003L, 503L, 30));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(101, 102));

        assertThat(plan.getGroups()).isEmpty();
        assertThat(plan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getTicketId)
                .containsExactly(101, 102);
        assertThat(plan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getMessage)
                .anySatisfy(message -> assertThat(message).contains("ambiguous"))
                .anySatisfy(message -> assertThat(message).contains("sales order header"));
    }

    @Test
    void prepareGroupsBySalesOrderAndAggregatesDuplicateProductOnlyInsideGroup() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, null, null));
        fixture.ticket(ticket(102, "MAT-B", 3, null, null));
        fixture.ticket(ticket(103, "MAT-A", 4, null, null));
        fixture.ticket(ticket(201, "MAT-A", 5, null, null));
        fixture.sourceTicketItem(101, item(1001L, 501L, 20));
        fixture.sourceTicketItem(102, item(1002L, 501L, 10));
        fixture.sourceTicketItem(103, item(1003L, 501L, 30));
        fixture.sourceTicketItem(201, item(2001L, 502L, 10));
        fixture.header(header(501L, "SO-501", "DOC-501"));
        fixture.header(header(502L, "SO-502", "DOC-502"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(201, 103, 102, 101));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups()).hasSize(2);
        WmsReservationPlanService.ReservationGroup first = plan.getGroups().get(0);
        WmsReservationPlanService.ReservationGroup second = plan.getGroups().get(1);
        assertThat(first.getSaleNo()).isEqualTo("DOC-501");
        assertThat(first.getRequest().getItems())
                .extracting(item -> item.getProductCode() + ":" + item.getReserveCount().stripTrailingZeros().toPlainString())
                .containsExactly("MAT-A:6", "MAT-B:3");
        assertThat(first.getTickets())
                .extracting(WmsReservationPlanService.TicketReservation::getTicketId)
                .containsExactly(102, 101, 103);
        assertThat(second.getSaleNo()).isEqualTo("DOC-502");
        assertThat(second.getRequest().getItems())
                .extracting(item -> item.getProductCode() + ":" + item.getReserveCount().stripTrailingZeros().toPlainString())
                .containsExactly("MAT-A:5");
    }

    @Test
    void prepareKeepsInvalidRowsOutOfRequests() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, null, null));
        fixture.ticket(ticket(102, " ", 3, null, null));
        fixture.ticket(ticket(103, "MAT-C", 0, null, null));
        fixture.sourceTicketItem(101, item(1001L, 501L, 10));
        fixture.sourceTicketItem(102, item(1002L, 501L, 20));
        fixture.sourceTicketItem(103, item(1003L, 501L, 30));
        fixture.header(header(501L, "SO-501", "DOC-501"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(101, 102, 103));

        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getGroups().get(0).getRequest().getItems())
                .extracting(com.machining.quote.dto.wms.WmsSaleLadingReserveReq.Item::getProductCode)
                .containsExactly("MAT-A");
        assertThat(plan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getTicketId)
                .containsExactly(102, 103);
    }

    @Test
    void prepareDerivesRemarkByStableTicketOrderAndFallbacks() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, null, null, " ", "name-101", "desc-101", "P-101"));
        fixture.ticket(ticket(102, "MAT-A", 3, null, null, "ticket-remark", "name-102", "desc-102", "P-102"));
        fixture.ticket(ticket(103, "MAT-B", 4, null, null, " ", "material-name", "desc-103", "P-103"));
        fixture.ticket(ticket(104, "MAT-C", 5, null, null, " ", " ", "description", "P-104"));
        fixture.ticket(ticket(105, "MAT-D", 6, null, null, " ", " ", " ", "part-no"));
        fixture.ticket(ticket(106, "MAT-E", 7, null, null, " ", " ", " ", " "));
        fixture.sourceTicketItem(101, item(1001L, 501L, 20));
        fixture.sourceTicketItem(102, item(1002L, 501L, 10));
        fixture.sourceTicketItem(103, item(1003L, 501L, 30));
        fixture.sourceTicketItem(104, item(1004L, 501L, 40));
        fixture.sourceTicketItem(105, item(1005L, 501L, 50));
        fixture.sourceTicketItem(106, item(1006L, 501L, 60));
        fixture.header(header(501L, "SO-501", "DOC-501"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(106, 105, 104, 103, 102, 101));

        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getGroups().get(0).getRequest().getItems())
                .extracting(item -> item.getProductCode() + ":" + item.getRemark())
                .containsExactly(
                        "MAT-A:ticket-remark",
                        "MAT-B:material-name",
                        "MAT-C:description",
                        "MAT-D:part-no"
                );
        assertThat(plan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getTicketId)
                .containsExactly(106);
    }

    @Test
    void prepareAddsCustomerProjectAndOrderNumbersToRequestItemsAndRejectsConflictingAggregates() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 2, null, null, "remark-101", "", "", "P-101"));
        fixture.ticket(ticket(102, "MAT-A", 3, null, null, "remark-102", "", "", "P-102"));
        SalesOrderItem firstItem = item(1001L, 501L, 10);
        firstItem.setCustomerProjectNo("CP-001");
        firstItem.setCustomerOrderNo("CO-001");
        SalesOrderItem secondItem = item(1002L, 501L, 20);
        secondItem.setCustomerProjectNo("CP-001");
        secondItem.setCustomerOrderNo("CO-001");
        fixture.sourceTicketItem(101, firstItem);
        fixture.sourceTicketItem(102, secondItem);
        fixture.header(header(501L, "SO-501", "DOC-501", "CO-HEADER", "CP-HEADER"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepare(List.of(102, 101));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getGroups().get(0).getRequest().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductCode()).isEqualTo("MAT-A");
            assertThat(item.getReserveCount()).isEqualByComparingTo("5");
            assertThat(item.getCustomerProjectNo()).isEqualTo("CP-001");
            assertThat(item.getCustomerOrderNo()).isEqualTo("CO-001");
        });

        Fixture conflictFixture = fixture();
        conflictFixture.ticket(ticket(201, "MAT-A", 2, null, null));
        conflictFixture.ticket(ticket(202, "MAT-A", 3, null, null));
        SalesOrderItem conflictA = item(2001L, 601L, 10);
        conflictA.setCustomerProjectNo("CP-A");
        conflictA.setCustomerOrderNo("CO-001");
        SalesOrderItem conflictB = item(2002L, 601L, 20);
        conflictB.setCustomerProjectNo("CP-B");
        conflictB.setCustomerOrderNo("CO-001");
        conflictFixture.sourceTicketItem(201, conflictA);
        conflictFixture.sourceTicketItem(202, conflictB);
        conflictFixture.header(header(601L, "SO-601", "DOC-601"));

        WmsReservationPlanService.ReservationPlan conflictPlan = conflictFixture.service.prepare(List.of(201, 202));

        assertThat(conflictPlan.getGroups()).isEmpty();
        assertThat(conflictPlan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getTicketId)
                .containsExactly(201, 202);
        assertThat(conflictPlan.getFailures())
                .extracting(WmsReservationPlanService.RowFailure::getMessage)
                .allSatisfy(message -> assertThat(message).contains("customerProjectNo"));
    }

    @Test
    void prepareGeneratesStableFingerprintFromSortedProductSaleNoQuantityAndRemark() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-B", 2, null, null, "remark-b", "", "", "P-101"));
        fixture.ticket(ticket(102, "MAT-A", 3, null, null, "remark-a", "", "", "P-102"));
        fixture.sourceTicketItem(101, item(1001L, 501L, 20));
        fixture.sourceTicketItem(102, item(1002L, 501L, 10));
        fixture.header(header(501L, "SO-501", "DOC-501"));

        WmsReservationPlanService.ReservationGroup group = fixture.service.prepare(List.of(101, 102)).getGroups().get(0);
        String sameFingerprint = new WmsReservationFingerprintService().fingerprint(group.getSaleNo(), group.getRequest().getItems());

        assertThat(group.getRequest().getItems())
                .extracting(com.machining.quote.dto.wms.WmsSaleLadingReserveReq.Item::getProductCode)
                .containsExactly("MAT-A", "MAT-B");
        assertThat(group.getRequestFingerprint()).isEqualTo(sameFingerprint);
        group.getRequest().getItems().get(0).setRemark("changed");
        String changedFingerprint = new WmsReservationFingerprintService().fingerprint(group.getSaleNo(), group.getRequest().getItems());
        assertThat(changedFingerprint).isNotEqualTo(sameFingerprint);
        group.getRequest().getItems().get(0).setRemark("remark-a");
        group.getRequest().getItems().get(0).setCustomerOrderNo("changed-order");
        String changedCustomerFieldFingerprint = new WmsReservationFingerprintService().fingerprint(group.getSaleNo(), group.getRequest().getItems());
        assertThat(changedCustomerFieldFingerprint).isNotEqualTo(sameFingerprint);
    }

    @Test
    void splitUsesStableLineOrderAndConsumesStockInProductionAndSurplusBuckets() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 4, null, null));
        fixture.ticket(ticket(102, "MAT-A", 3, null, null));
        fixture.sourceTicketItem(101, item(1001L, 501L, 10));
        fixture.sourceTicketItem(102, item(1002L, 501L, 20));
        fixture.header(header(501L, "SO-501", "DOC-501"));
        WmsReservationPlanService.ReservationGroup group = fixture.service.prepare(List.of(102, 101)).getGroups().get(0);

        List<WmsReservationPlanService.TicketAllocationSplit> splits = fixture.service.split(
                group,
                responseData("DOC-501", "QPO-WMS-1", responseItem("MAT-A", "7", "3", "2", "5", "2"))
        );

        assertThat(splits).extracting(WmsReservationPlanService.TicketAllocationSplit::getTicketId)
                .containsExactly(101, 102);
        assertThat(splits).extracting(split -> split.getStockFrozenCount().toPlainString()
                        + ":" + split.getInProductionCount().toPlainString()
                        + ":" + split.getSurplusCount().toPlainString())
                .containsExactly("3:1:0", "0:1:2");
        assertThat(splits).allSatisfy(split -> assertThat(split.getStockFrozenCount()
                .add(split.getInProductionCount())
                .add(split.getSurplusCount())).isEqualByComparingTo(split.getReserveCount()));
    }

    @Test
    void splitRejectsUnconsumedBuckets() {
        Fixture fixture = fixture();
        fixture.ticket(ticket(101, "MAT-A", 4, null, null));
        fixture.sourceTicketItem(101, item(1001L, 501L, 10));
        fixture.header(header(501L, "SO-501", "DOC-501"));
        WmsReservationPlanService.ReservationGroup group = fixture.service.prepare(List.of(101)).getGroups().get(0);

        assertThatThrownBy(() -> fixture.service.split(
                group,
                responseData("DOC-501", "QPO-WMS-1", responseItem("MAT-A", "5", "3", "1", "4", "1"))
        )).hasMessageContaining("bucket");
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private OrderDeliveryTicket ticket(Integer id, String materialNo, Integer qty, String sourceType, Long sourceItemId) {
        return ticket(id, materialNo, qty, sourceType, sourceItemId, "remark-" + id, "material-" + id, "desc-" + id, "P-" + id);
    }

    private OrderDeliveryTicket ticket(
            Integer id,
            String materialNo,
            Integer qty,
            String sourceType,
            Long sourceItemId,
            String remark,
            String materialName,
            String description,
            String partNo) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setMaterialNo(materialNo);
        ticket.setQty(qty);
        ticket.setSourceType(sourceType);
        ticket.setSourceItemId(sourceItemId);
        ticket.setRemark(remark);
        ticket.setMaterialName(materialName);
        ticket.setDescription(description);
        ticket.setPartNo(partNo);
        return ticket;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, Integer lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        return item;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String documentNo) {
        return header(id, salesOrderNo, documentNo, null, null);
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String documentNo, String customerOrderNo, String customerProjectNo) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentNo(documentNo);
        header.setCustomerOrderNo(customerOrderNo);
        header.setCustomerProjectNo(customerProjectNo);
        return header;
    }

    private WmsSaleLadingReserveResp.Data responseData(String saleOrderNo, String externalBizId, WmsSaleLadingReserveResp.Item... items) {
        WmsSaleLadingReserveResp.Data data = new WmsSaleLadingReserveResp.Data();
        data.setSaleOrderNo(saleOrderNo);
        data.setExternalBizId(externalBizId);
        data.setItems(List.of(items));
        return data;
    }

    private WmsSaleLadingReserveResp.Item responseItem(
            String productCode,
            String reserveCount,
            String stockCount,
            String inProductionCount,
            String frozenCount,
            String surplusCount) {
        WmsSaleLadingReserveResp.Item item = new WmsSaleLadingReserveResp.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setStockCount(new BigDecimal(stockCount));
        item.setInProductionCount(new BigDecimal(inProductionCount));
        item.setFrozenCount(new BigDecimal(frozenCount));
        item.setSurplusCount(new BigDecimal(surplusCount));
        return item;
    }

    private static final class Fixture {
        private final OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        private final SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        private final WmsReservationPlanService service = new WmsReservationPlanService();

        private Fixture() {
            ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
            ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
            ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
            ReflectionTestUtils.setField(service, "fingerprintService", new WmsReservationFingerprintService());
        }

        private void ticket(OrderDeliveryTicket ticket) {
            when(ticketMapper.selectByIdForUpdate(ticket.getId())).thenReturn(ticket);
        }

        private void sourceTicketItem(Integer ticketId, SalesOrderItem item) {
            when(salesOrderItemMapper.selectBySourceTicketIdForUpdate(ticketId)).thenReturn(item);
        }

        private void directItem(SalesOrderItem item) {
            when(salesOrderItemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        }

        private void header(SalesOrderHeader header) {
            when(salesOrderHeaderMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        }
    }
}
