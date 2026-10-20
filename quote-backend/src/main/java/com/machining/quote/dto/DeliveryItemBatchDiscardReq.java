package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryItemBatchDiscardReq {
    private List<Integer> ticketIds;
    private List<Integer> allocationIds;
    private String reason;
}
