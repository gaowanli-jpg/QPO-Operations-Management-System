package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderMatchOrderRow {
    private Long salesOrderId;
    private String sourceType;
    private String salesOrderNo;
    private String customerName;
    private String customerOrderNo;
    private String customerProjectNo;
    private String internalProjectNo;
    private String documentTypeCode;
    private String status;
    private Integer totalItemCount;
    private Integer matchedItemCount;
    private Integer unmatchedItemCount;
    private String warningMessage;
    private LocalDateTime updatedAt;
}
