package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryTicketBatchReq {
    private List<Integer> ticketIds;
}
