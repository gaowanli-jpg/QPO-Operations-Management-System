package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderSyncItemRow {
    private Long salesOrderId;
    private Integer lineNo;
    private String materialNo;
    private Integer releasedQty;
    private String description;
    private String surfaceTreatment;
    private String remark;
}
