package com.machining.quote.dto;

import lombok.Data;

@Data
public class OrderStatusChangeResult {
    private Integer rfqId;
    private String rfqNo;
    private String fromStatus;
    private String toStatus;
    private Integer updatedItemCount;
    private String message;
}
