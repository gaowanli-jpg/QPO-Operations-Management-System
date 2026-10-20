package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderU9SyncResultRow {
    private boolean success;
    private String status;
    private Long salesOrderId;
    private String salesOrderNo;
    private String u9OrderId;
    private String u9OrderCode;
    private String errorMessage;
}
