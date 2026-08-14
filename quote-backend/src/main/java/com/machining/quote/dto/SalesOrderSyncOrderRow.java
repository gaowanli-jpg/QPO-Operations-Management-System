package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SalesOrderSyncOrderRow {
    private Long salesOrderId;
    private String saleOrderNo;
    private String documentNo;
    private String customerCode;
    private String customerName;
    private String customerShortName;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String internalProjectNo;
    private String orderType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime changedAt;
}
