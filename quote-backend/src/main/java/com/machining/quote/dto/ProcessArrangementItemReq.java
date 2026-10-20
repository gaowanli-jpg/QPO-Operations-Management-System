package com.machining.quote.dto;

import lombok.Data;

@Data
public class ProcessArrangementItemReq {
    private String sourceType;
    private Long processDetailId;
    private Integer feeItemId;
    private Integer sortNo;
}
