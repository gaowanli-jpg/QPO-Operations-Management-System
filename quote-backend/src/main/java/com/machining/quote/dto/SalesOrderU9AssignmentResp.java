package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderU9AssignmentResp {
    private Long salesOrderId;
    private String salesOrderNo;
    private String sellerCode;
    private String sellerName;
    private String saleDepartmentCode;
    private String saleDepartmentName;
    private LocalDateTime updatedAt;
}
