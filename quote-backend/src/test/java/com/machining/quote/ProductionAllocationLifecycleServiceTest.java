package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.service.mes.ProductionAllocationLifecycleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionAllocationLifecycleServiceTest {
    @Test
    void materialSuccessCreatesOneFullProductionAllocationAndIsIdempotent() {
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        ProductionAllocationLifecycleService service = new ProductionAllocationLifecycleService();
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);

        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(41);
        ticket.setQty(6);
        ticket.setRfqId(9);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(88L);
        ticket.setPartNo("P-41");
        ticket.setVersion("B");
        ticket.setMaterialNo(null);
        ticket.setMaterialName("Aluminum");
        ticket.setCustomerOrderNo("CO-41");
        ticket.setCustomerProjectNo("CP-41");
        ticket.setProcessRoute(List.of(Map.of("processCode", "CUT")));
        ticket.setMaterialData(Map.of("shape", "plate"));
        when(ticketMapper.selectByIdForUpdate(41)).thenReturn(ticket);

        List<OrderDeliveryTicketAllocation> allocations = new ArrayList<>();
        when(allocationMapper.selectByTicketIdForUpdate(41)).thenAnswer(invocation -> List.copyOf(allocations));
        when(allocationMapper.insert(any(OrderDeliveryTicketAllocation.class))).thenAnswer(invocation -> {
            OrderDeliveryTicketAllocation allocation = invocation.getArgument(0);
            allocation.setId(501);
            allocations.add(allocation);
            return 1;
        });

        service.recordMaterialSuccess(41, "MAT-41", "material resolved", "REQ-41", "TASK-41", false);
        service.recordMaterialSuccess(41, "MAT-41", "callback replay", "REQ-41", "TASK-41", true);

        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(allocationMapper, times(1)).insert(allocationCaptor.capture());
        OrderDeliveryTicketAllocation allocation = allocationCaptor.getValue();
        assertThat(allocation.getTicketId()).isEqualTo(41);
        assertThat(allocation.getAllocationType()).isEqualTo("PRODUCTION_REQUIRED");
        assertThat(allocation.getAllocationSource()).isEqualTo("MES_MATERIAL");
        assertThat(allocation.getQty()).isEqualTo(6);
        assertThat(allocation.getTicketQty()).isEqualTo(6);
        assertThat(allocation.getMaterialNo()).isEqualTo("MAT-41");
        assertThat(allocation.getCustomerOrderNo()).isEqualTo("CO-41");
        assertThat(allocation.getCustomerProjectNo()).isEqualTo("CP-41");
        assertThat(allocation.getStatus()).isEqualTo("ACTIVE");
        assertThat(allocation.getMesProductionStatus()).isEqualTo("PENDING");
        assertThat(allocations).hasSize(1);
    }
}
