package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SalesOrderSyncItemDTO {
    private Long allocationId;
    private String allocationType;
    private String saleOrderItemNo;
    private String materialNo;
    private Integer releasedQty;
    private String materialName;
    private String description;
    private String surfaceTreatment;
    private String remark;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
}
