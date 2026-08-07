package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMesRequestMapper;
import com.machining.quote.service.mes.MesMaterialDedupService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMaterialDedupStage5Test {

    @Test
    void createsMasterWithoutScanningHistoricalSuccessfulDeliveryTickets() {
        MesMaterialDedupService service = new MesMaterialDedupService();
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderDeliveryTicketMesRequestMapper requestMapper = mock(OrderDeliveryTicketMesRequestMapper.class);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "mesRequestMapper", requestMapper);
        when(requestMapper.selectOne(any())).thenReturn(null);

        MesMaterialDedupService.DedupResolveResult result = service.resolveBeforeMes(ticket(), 9501L);

        assertThat(result.getType()).isEqualTo(MesMaterialDedupService.RESULT_MASTER_CREATED);
        assertThat(result.getDedupKey()).isEqualTo("P-STAGE5|A");
        verify(ticketMapper, never()).selectList(any());
    }

    private OrderDeliveryTicket ticket() {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(501);
        ticket.setPartNo("P-STAGE5");
        ticket.setVersion("A");
        ticket.setMesSyncStatus("PENDING");
        return ticket;
    }
}
