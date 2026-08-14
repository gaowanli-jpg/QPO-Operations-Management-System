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
    private String materialName;
    private String engineeringMaterialCode;
    private String engineeringMaterialName;
    private String engineeringMaterialTypeCode;
    private String engineeringMaterialTypeName;
    private String engineeringMaterialCategory;
    private String materialShape;
    private String surfaceTreatment;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private Integer shippedQty;
    private Integer unshippedQty;
    private Integer releasedQty;
    private Integer remainingQty;
    private String orderType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
