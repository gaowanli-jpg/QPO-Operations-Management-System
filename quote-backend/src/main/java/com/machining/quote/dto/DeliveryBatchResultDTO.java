package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryBatchResultDTO {
    private boolean success;
    private String message;
    private Long salesOrderId;
    private String salesOrderNo;
    private String documentNo;
    private List<Integer> sourceRfqIds;
    private int ticketCount;
    private int confirmedMaterialCount;
    private int pendingMaterialCount;
    private List<Integer> ticketIds;
}
