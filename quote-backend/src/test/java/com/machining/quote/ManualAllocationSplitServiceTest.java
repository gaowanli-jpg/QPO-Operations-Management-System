package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.AllocationSplitService;
import com.machining.quote.service.SalesOrderAllocationLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualAllocationSplitServiceTest {

    @Test
    void manualSplitCreatesLockRowsAndKeepsProductionRemainder() {
        Fixture fixture = fixture(10);

        AllocationSplitService.SplitResult result = fixture.service.applyManualSplit(77L, 10, 3, 2);

        assertThat(fixture.parent.getQty()).isEqualTo(5);
        assertThat(fixture.parent.getAllocationSplitSource()).isEqualTo("MANUAL");
        assertThat(result.createdAllocations()).hasSize(2);
        assertThat(result.createdAllocations()).extracting(OrderDeliveryTicketAllocation::getAllocationType)
                .containsExactlyInAnyOrder("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");
        assertThat(result.createdAllocations()).extracting(OrderDeliveryTicketAllocation::getQty)
                .containsExactlyInAnyOrder(3, 2);
        verify(fixture.link).syncAllocationSplitsForAllocation(eq(10), any());
    }

    @Test
    void zeroZeroConsumesManualSplitOpportunityWithoutCreatingLockRows() {
        Fixture fixture = fixture(10);

        AllocationSplitService.SplitResult result = fixture.service.applyManualSplit(77L, 10, 0, 0);

        assertThat(fixture.parent.getQty()).isEqualTo(10);
        assertThat(fixture.parent.getAllocationSplitSource()).isEqualTo("MANUAL");
        assertThat(result.createdAllocations()).isEmpty();
    }

    @Test
    void manualSplitRejectsExcessQuantityAndActiveWmsReservation() {
        Fixture excessive = fixture(5);
        assertThatThrownBy(() -> excessive.service.applyManualSplit(77L, 10, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);

        Fixture active = fixture(5);
        when(active.wmsMapper.selectActiveByAllocationId(10)).thenReturn(new com.machining.quote.entity.WmsReservationTicket());
        assertThatThrownBy(() -> active.service.applyManualSplit(77L, 10, 1, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    private Fixture fixture(int qty) {
        Fixture fixture = new Fixture();
        fixture.parent = new OrderDeliveryTicketAllocation();
        fixture.parent.setId(10);
        fixture.parent.setAllocationType("PRODUCTION_REQUIRED");
        fixture.parent.setStatus("ACTIVE");
        fixture.parent.setQty(qty);
        fixture.parent.setMaterialNo("MAT-10");

        fixture.item = new SalesOrderItem();
        fixture.item.setId(100L);
        fixture.item.setSalesOrderId(77L);

        when(fixture.link.lockSalesOrderForAllocation(10)).thenReturn(fixture.item);
        when(fixture.allocationMapper.selectByIdForUpdate(10)).thenReturn(fixture.parent);
        when(fixture.allocationMapper.selectBySplitParentAllocationId(10)).thenReturn(new ArrayList<>());
        when(fixture.wmsMapper.selectActiveByAllocationId(10)).thenReturn(null);
        doAnswer(invocation -> {
            OrderDeliveryTicketAllocation inserted = invocation.getArgument(0);
            inserted.setId("IN_STOCK_LOCK".equals(inserted.getAllocationType()) ? 11 : 12);
            return 1;
        }).when(fixture.allocationMapper).insert(any(OrderDeliveryTicketAllocation.class));

        fixture.service = new AllocationSplitService();
        ReflectionTestUtils.setField(fixture.service, "allocationMapper", fixture.allocationMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderItemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(fixture.service, "wmsReservationTicketMapper", fixture.wmsMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderAllocationLinkService", fixture.link);
        return fixture;
    }

    private static class Fixture {
        private final OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final WmsReservationTicketMapper wmsMapper = mock(WmsReservationTicketMapper.class);
        private final SalesOrderAllocationLinkService link = mock(SalesOrderAllocationLinkService.class);
        private AllocationSplitService service;
        private OrderDeliveryTicketAllocation parent;
        private SalesOrderItem item;
    }
}
