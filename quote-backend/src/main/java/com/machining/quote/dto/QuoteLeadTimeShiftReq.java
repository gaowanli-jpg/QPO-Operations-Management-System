package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuoteLeadTimeShiftReq {
    private Integer rfqId;
    private List<Integer> itemIds;
    private Integer days;
    private String scope;
}
