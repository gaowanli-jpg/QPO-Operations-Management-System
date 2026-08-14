package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SalesOrderSyncItemDTO {
    private String saleOrderNo;
    private String saleOrderItemNo;
    private Integer lineNo;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private String materialNo;
    private String materialNoStatus;
    private String salesMaterialNo;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private String orderType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
