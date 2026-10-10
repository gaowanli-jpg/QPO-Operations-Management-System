package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.service.SalesOrderWmsAllocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderWmsAllocationServiceTest {
    @Test
    void selectsSalesOrderProductionAllocationsAndFiltersInvalidRows() {
        SalesOrderHeaderMapper orderMapper = mock(SalesOrderHeaderMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        SalesOrderWmsAllocationService service = new SalesOrderWmsAllocationService();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);

        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(77L);
        when(orderMapper.selectById(77L)).thenReturn(order);
        when(allocationMapper.selectSalesOrderProductionAllocations(77L)).thenReturn(List.of(
                allocation(101, "MAT-101", 2, "PENDING", null, false),
                allocation(105, "MAT-105", 3, "FAILED", null, false),
                allocation(102, "MAT-102", 4, "PENDING", 700L, false),
                allocation(103, null, 5, "PENDING", null, false),
                allocation(104, "MAT-104", 6, "PENDING", null, true),
                allocation(106, "MAT-106", 7, "RESULT_UNKNOWN", null, false),
                allocation(107, "MAT-107", 0, "PENDING", null, false)));

        assertThat(service.resolveAllocationIds(77L)).containsExactly(101, 105, 102, 103, 104, 106, 107);
        assertThat(service.resolveEligibleAllocationIds(77L)).containsExactly(101, 105);
        assertThat(service.resolveSkippedReasons(77L)).containsExactly(
                "Allocation 102: WMS reservation has already been applied",
                "Allocation 103: Material number is not confirmed",
                "Allocation 104: Allocation is discarded",
                "Allocation 106: MES production has started or completed",
                "Allocation 107: Allocation quantity is not positive");
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, String materialNo, Integer qty,
                                                     String mesStatus, Long wmsReservationId, boolean discarded) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(id);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setStatus("ACTIVE");
        allocation.setQty(qty);
        allocation.setMaterialNo(materialNo);
        allocation.setMesProductionStatus(mesStatus);
        allocation.setWmsReservationTicketId(wmsReservationId);
        allocation.setDiscarded(discarded);
        return allocation;
    }
}
