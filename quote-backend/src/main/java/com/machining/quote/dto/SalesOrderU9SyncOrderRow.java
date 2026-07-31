package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderU9SyncOrderRow {
    private Long salesOrderId;
    private String salesOrderNo;
    private String customerName;
    private String customerCode;
    private String customerOrderNo;
    private String internalProjectNo;
    private String customerProjectNo;
    private String documentTypeCode;
    private String status;
    private String u9SyncStatus;
    private String u9OrderCode;
    private String u9SyncMessage;
    private Integer itemCount;
    private Integer materialConfirmedItemCount;
    private Integer missingMaterialItemCount;
    private Boolean syncReady;
    private String blockedReason;
    private LocalDateTime updatedAt;
}
