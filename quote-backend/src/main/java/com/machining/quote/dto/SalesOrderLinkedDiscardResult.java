package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderLinkedDiscardResult {
    private boolean success;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long salesOrderItemId;
    private Integer lineNo;
    private boolean linkedDiscarded;
    private int deliveredItemCount;
    private int productionItemCount;
    private int lockedItemCount;
    private int skippedCompletedCount;
    private int skippedAlreadyDiscardedCount;
    private String reason;
    private String message;
}
