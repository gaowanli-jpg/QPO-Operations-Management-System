package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9SyncPreviewLine {
    private Long salesOrderItemId;
    private Integer lineNo;
    private String partNo;
    private String version;
    private LocalDate salesOrderDate;
    private String customerOrderTitle;
    private String customerOrderNo;
    private String customerProjectNo;
    private String internalProjectNo;
    private String salesMaterialNo;
    private LocalDate customerDeliveryDate;
    private LocalDate internalDeliveryDate;
    private String description;
    private String materialName;
    private String engineeringMaterialCode;
    private String engineeringMaterialTypeCode;
    private String engineeringMaterialTypeName;
    private String engineeringMaterialCategory;
    private String surfaceTreatment;
    private String materialNo;
    private String materialNoStatus;
    private Integer qty;
    private Integer shippedQty;
    private Integer unshippedQty;
    private BigDecimal taxIncludedUnitPrice;
    private BigDecimal taxIncludedTotalPrice;
    private BigDecimal untaxedUnitPrice;
    private BigDecimal untaxedTotalPrice;
    private LocalDate deliveryDate;
    private String remark;
    private Boolean ready;
    private String blockedReason;
    private List<String> blockingErrors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
