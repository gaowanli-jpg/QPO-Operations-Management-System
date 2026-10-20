package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SalesOrderU9OrderDateUpdateResp {
    private Boolean success;
    private String message;
    private Long salesOrderId;
    private String salesOrderNo;
    private LocalDate salesOrderDate;
    private LocalDateTime updatedAt;
    private SalesOrderU9SyncPreviewResp preview;
}
