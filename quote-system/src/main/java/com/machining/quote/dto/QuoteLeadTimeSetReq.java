package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuoteLeadTimeSetReq {
    private Integer rfqId;
    private List<Integer> itemIds;
    private String quoteLeadTime;
}
