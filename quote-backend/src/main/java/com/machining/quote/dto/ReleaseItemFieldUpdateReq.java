package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemFieldUpdateReq {
    private ReleaseSourceType sourceType;
    private Integer rfqId;
    private Long sourceOrderId;
    private String remark;
    private String customerRequirement;
}
