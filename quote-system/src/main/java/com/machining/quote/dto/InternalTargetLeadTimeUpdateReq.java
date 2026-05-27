package com.machining.quote.dto;

import lombok.Data;

@Data
public class InternalTargetLeadTimeUpdateReq {
    private Integer rfqId;
    private Integer itemId;
    private String internalTargetLeadTime;
}
