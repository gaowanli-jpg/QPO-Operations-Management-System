package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderLinkedDiscardBatchResult {
    private boolean success;
    private Long salesOrderId;
    private String salesOrderNo;
    private String fromStatus;
    private String toStatus;
    private boolean linkedDiscarded;
    private int totalItemCount;
    private int processedItemCount;
    private int deliveredItemCount;
    private int productionItemCount;
    private int lockedItemCount;
    private int skippedCompletedCount;
    private int skippedAlreadyDiscardedCount;
    private int skippedAlreadyLinkedCount;
    private String reason;
    private String message;
}
