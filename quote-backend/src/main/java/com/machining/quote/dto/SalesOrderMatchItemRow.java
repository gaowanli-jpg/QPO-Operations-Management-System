package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOrderMatchItemRow {
    private Long salesOrderId;
    private Long salesOrderItemId;
    private Integer lineNo;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private String materialName;
    private String surfaceTreatment;
    private String materialNo;
    private BigDecimal taxIncludedUnitPrice;
    private String currentMatchStatus;
    private String matchMessage;
    private Integer matchedRfqId;
    private Integer matchedItemId;
    private String assemblyRelationStatus;
    private String status;
}
