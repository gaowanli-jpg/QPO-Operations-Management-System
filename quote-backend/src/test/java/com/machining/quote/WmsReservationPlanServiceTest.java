package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
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
    void prepareBuildsWmsRequestFromAllocationQuantityAndLinkedSalesOrderValues() {
        Fixture fixture = fixture();
        fixture.allocation(allocation(101, "MAT-A", 7, "remark-a"));
        SalesOrderItem sourceItem = item(1001L, 501L, 10);
        sourceItem.setCustomerOrderNo("CO-LINE-501");
        fixture.link(101, sourceItem);
        fixture.header(header(501L, "SO-501", "CO-HEADER-501", "CP-HEADER-501"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(101));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups()).singleElement().satisfies(group -> {
            assertThat(group.getSaleNo()).isEqualTo("CO-LINE-501");
            assertThat(group.getRequest().getSaleNo()).isEqualTo("CO-LINE-501");
            assertThat(group.getRequest().getItems()).singleElement().satisfies(requestItem -> {
                assertThat(requestItem.getReserveCount()).isEqualByComparingTo("7");
                assertThat(requestItem.getCustomerOrderNo()).isEqualTo("CO-LINE-501");
            });
        });
    }

    @Test
    void prepareResolvesOrderHeaderForEachAllocationAndFailsWhenLinkageIsMissing() {
        Fixture fixture = fixture();
        fixture.allocation(allocation(101, "MAT-A", 2, "remark-a"));
        fixture.link(101, item(1001L, 501L, 10));
        fixture.header(header(501L, "SO-501", "CO-501", "CP-501"));

        fixture.allocation(allocation(102, "MAT-B", 3, "remark-b"));
        fixture.link(102, item(1002L, 502L, 20));

        fixture.allocation(allocation(103, "MAT-C", 4, "remark-c"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(101, 102, 103));

        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getFailures()).extracting(WmsReservationPlanService.RowFailure::getAllocationId)
                .containsExactly(102, 103);
        assertThat(plan.getFailures()).extracting(WmsReservationPlanService.RowFailure::getMessage)
                .anySatisfy(message -> assertThat(message).contains("sales order header"))
                .anySatisfy(message -> assertThat(message).contains("sales order item"));
    }

    @Test
    void prepareRejectsMissingCustomerOrderNumber() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(101, "MAT-A", 2, "remark-a");
        allocation.setCustomerOrderNo(null);
        fixture.allocation(allocation);
        fixture.link(101, item(1001L, 501L, 10));
        fixture.header(header(501L, "SO-501", null, "CP-501"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(101));

        assertThat(plan.getGroups()).isEmpty();
        assertThat(plan.getFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.getAllocationId()).isEqualTo(101);
            assertThat(failure.getTicketId()).isEqualTo(101);
            assertThat(failure.getMessage()).contains("customerOrderNo is required");
        });
    }

    @Test
    void prepareGroupsByOrderAndProjectAndAggregatesSameMaterialOnlyWithinEachGroup() {
        Fixture fixture = fixture();
        addAllocation(fixture, 101, "MAT-A", 2, 501L, 20, "CP-A", "CO-1");
        addAllocation(fixture, 102, "MAT-B", 3, 501L, 10, "CP-A", "CO-1");
        addAllocation(fixture, 103, "MAT-A", 4, 501L, 30, "CP-A", "CO-1");
        addAllocation(fixture, 201, "MAT-A", 5, 502L, 10, "CP-B", "CO-2");
        fixture.header(header(501L, "SO-501", "CO-1", "CP-A"));
        fixture.header(header(502L, "SO-502", "CO-2", "CP-B"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(201, 103, 102, 101));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups()).hasSize(2);
        WmsReservationPlanService.ReservationGroup first = plan.getGroups().get(0);
        assertThat(first.getSaleNo()).isEqualTo("CO-1");
        assertThat(first.getRequest().getItems())
                .extracting(item -> item.getProductCode() + ":" + item.getReserveCount().stripTrailingZeros().toPlainString())
                .containsExactly("MAT-A:6", "MAT-B:3");
        assertThat(first.getAllocations()).extracting(WmsReservationPlanService.AllocationReservation::getAllocationId)
                .containsExactly(102, 101, 103);
        WmsReservationPlanService.ReservationGroup second = plan.getGroups().get(1);
        assertThat(second.getSaleNo()).isEqualTo("CO-2");
        assertThat(second.getRequest().getItems()).singleElement()
                .extracting(item -> item.getProductCode() + ":" + item.getReserveCount().stripTrailingZeros().toPlainString())
                .isEqualTo("MAT-A:5");
    }

    @Test
    void prepareSplitsGroupsByCustomerProjectBeforeAggregatingSameMaterial() {
        Fixture fixture = fixture();
        addAllocation(fixture, 201, "MAT-A", 2, 601L, 10, "CP-A", "CO-1");
        addAllocation(fixture, 202, "MAT-A", 3, 601L, 20, "CP-A", "CO-1");
        addAllocation(fixture, 203, "MAT-A", 4, 601L, 30, "CP-B", "CO-1");
        fixture.header(header(601L, "SO-601", "CO-1", "CP-HEADER"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(203, 202, 201));

        assertThat(plan.getFailures()).isEmpty();
        assertThat(plan.getGroups()).hasSize(2);
        assertThat(plan.getGroups().get(0).getRequest().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getReserveCount()).isEqualByComparingTo("5");
            assertThat(item.getCustomerProjectNo()).isEqualTo("CP-A");
        });
        assertThat(plan.getGroups().get(0).getAllocations())
                .extracting(WmsReservationPlanService.AllocationReservation::getAllocationId)
                .containsExactly(201, 202);
        assertThat(plan.getGroups().get(1).getRequest().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getReserveCount()).isEqualByComparingTo("4");
            assertThat(item.getCustomerProjectNo()).isEqualTo("CP-B");
        });
    }

    @Test
    void prepareOmitsInvalidMaterialAndNonPositiveAllocations() {
        Fixture fixture = fixture();
        addAllocation(fixture, 101, "MAT-A", 2, 501L, 10, "CP-1", "CO-1");
        addAllocation(fixture, 102, " ", 3, 501L, 20, "CP-1", "CO-1");
        addAllocation(fixture, 103, "MAT-C", 0, 501L, 30, "CP-1", "CO-1");
        fixture.header(header(501L, "SO-501", "CO-1", "CP-1"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(101, 102, 103));

        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getGroups().get(0).getRequest().getItems())
                .extracting(com.machining.quote.dto.wms.WmsSaleLadingReserveReq.Item::getProductCode)
                .containsExactly("MAT-A");
        assertThat(plan.getFailures()).extracting(WmsReservationPlanService.RowFailure::getAllocationId)
                .containsExactly(102, 103);
    }

    @Test
    void prepareUsesAllocationSnapshotFieldsForRemarkAndStableOrder() {
        Fixture fixture = fixture();
        fixture.allocation(allocation(101, "MAT-A", 2, " ", "material-101", "desc-101", "P-101"));
        fixture.allocation(allocation(102, "MAT-A", 3, "ticket-remark", "material-102", "desc-102", "P-102"));
        fixture.allocation(allocation(103, "MAT-B", 4, " ", "material-name", "desc-103", "P-103"));
        fixture.allocation(allocation(104, "MAT-C", 5, " ", " ", "description", "P-104"));
        fixture.allocation(allocation(105, "MAT-D", 6, " ", " ", " ", "part-no"));
        fixture.allocation(allocation(106, "MAT-E", 7, " ", " ", " ", " "));
        for (int id = 101; id <= 106; id++) {
            fixture.link(id, item(1000L + id, 501L, id - 90));
        }
        fixture.header(header(501L, "SO-501", "CO-1", "CP-1"));

        WmsReservationPlanService.ReservationPlan plan = fixture.service.prepareAllocations(List.of(106, 105, 104, 103, 102, 101));

        assertThat(plan.getGroups()).hasSize(1);
        assertThat(plan.getGroups().get(0).getRequest().getItems())
                .extracting(item -> item.getProductCode() + ":" + item.getRemark())
                .containsExactly("MAT-A:ticket-remark", "MAT-B:material-name", "MAT-C:description", "MAT-D:part-no");
        assertThat(plan.getFailures()).extracting(WmsReservationPlanService.RowFailure::getAllocationId)
                .containsExactly(106);
    }

    @Test
    void splitUsesStableAllocationOrderAndPreservesAllocationQuantities() {
        Fixture fixture = fixture();
        addAllocation(fixture, 101, "MAT-A", 4, 501L, 10, "CP-1", "CO-1");
        addAllocation(fixture, 102, "MAT-A", 3, 501L, 20, "CP-1", "CO-1");
        fixture.header(header(501L, "SO-501", "CO-1", "CP-1"));
        WmsReservationPlanService.ReservationGroup group = fixture.service.prepareAllocations(List.of(102, 101)).getGroups().get(0);

        List<WmsReservationPlanService.AllocationSplit> splits = fixture.service.split(
                group,
                responseData("CO-1", "QPO-WMS-1", responseItem("MAT-A", "7", "3", "2", "5", "2"))
        );

        assertThat(splits).extracting(WmsReservationPlanService.AllocationSplit::getAllocationId).containsExactly(101, 102);
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
        addAllocation(fixture, 101, "MAT-A", 4, 501L, 10, "CP-1", "CO-1");
        fixture.header(header(501L, "SO-501", "CO-1", "CP-1"));
        WmsReservationPlanService.ReservationGroup group = fixture.service.prepareAllocations(List.of(101)).getGroups().get(0);

        assertThatThrownBy(() -> fixture.service.split(
                group,
                responseData("CO-1", "QPO-WMS-1", responseItem("MAT-A", "5", "3", "1", "4", "1"))
        )).hasMessageContaining("bucket");
    }

    private void addAllocation(Fixture fixture, Integer allocationId, String materialNo, Integer qty,
                               Long salesOrderId, Integer lineNo, String customerProjectNo, String customerOrderNo) {
        OrderDeliveryTicketAllocation allocation = allocation(allocationId, materialNo, qty, "remark-" + allocationId);
        allocation.setCustomerProjectNo(customerProjectNo);
        fixture.allocation(allocation);
        SalesOrderItem sourceItem = item(1000L + allocationId, salesOrderId, lineNo);
        sourceItem.setCustomerProjectNo(customerProjectNo);
        sourceItem.setCustomerOrderNo(customerOrderNo);
        fixture.link(allocationId, sourceItem);
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String materialNo, Integer qty, String remark) {
        return allocation(id, materialNo, qty, remark, "material-" + id, "description-" + id, "P-" + id);
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String materialNo, Integer qty,
                                                     String remark, String materialName,
                                                     String description, String partNo) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(id);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setStatus("ACTIVE");
        allocation.setQty(qty);
        allocation.setMaterialNo(materialNo);
        allocation.setRemark(remark);
        allocation.setMaterialName(materialName);
        allocation.setDescription(description);
        allocation.setPartNo(partNo);
        allocation.setCustomerOrderNo("CO-1");
        allocation.setCustomerProjectNo("CP-1");
        return allocation;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, Integer lineNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        return item;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String customerOrderNo, String customerProjectNo) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setCustomerOrderNo(customerOrderNo);
        header.setCustomerProjectNo(customerProjectNo);
        return header;
    }

    private WmsSaleLadingReserveResp.Data responseData(String saleOrderNo, String externalBizId,
                                                       WmsSaleLadingReserveResp.Item... items) {
        WmsSaleLadingReserveResp.Data data = new WmsSaleLadingReserveResp.Data();
        data.setSaleOrderNo(saleOrderNo);
        data.setExternalBizId(externalBizId);
        data.setItems(List.of(items));
        return data;
    }

    private WmsSaleLadingReserveResp.Item responseItem(String productCode,
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

    private Fixture fixture() {
        return new Fixture();
    }

    private static final class Fixture {
        private final OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        private final SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        private final SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        private final WmsReservationPlanService service = new WmsReservationPlanService();

        private Fixture() {
            ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
            ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
            ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
            ReflectionTestUtils.setField(service, "fingerprintService", new WmsReservationFingerprintService());
        }

        private void allocation(OrderDeliveryTicketAllocation allocation) {
            when(allocationMapper.selectByIdForUpdate(allocation.getId())).thenReturn(allocation);
        }

        private void link(Integer allocationId, SalesOrderItem item) {
            when(salesOrderItemMapper.selectByAllocationIdForUpdate(allocationId.longValue())).thenReturn(item);
        }

        private void header(SalesOrderHeader header) {
            when(salesOrderHeaderMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        }
    }
}
