package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderSyncItemRow {
    private Long salesOrderId;
    private Integer lineNo;
    private String materialNo;
    private Integer qty;
    private String materialName;
    private String description;
    private String surfaceTreatment;
    private String remark;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
}
