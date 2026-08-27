package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SalesOrderSyncItemDTO {
    private String saleOrderItemNo;
    private String materialNo;
    private Integer releasedQty;
    private String description;
    private String surfaceTreatment;
    private String remark;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
}
