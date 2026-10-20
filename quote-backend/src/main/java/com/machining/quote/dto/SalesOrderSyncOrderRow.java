package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderSyncOrderRow {
    private Long salesOrderId;
    private String saleOrderNo;
    private LocalDateTime createdAt;
    private LocalDateTime changedAt;
    private String status;
    private String orderType;
    private String customerCode;
    private String supplierCode;
    private String internalProjectNo;
}
