package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderSyncItemDTO {
    private String saleOrderItemNo;
    private String materialNo;
    private Integer releasedQty;
    private String description;
    private String surfaceTreatment;
    private String remark;
}
