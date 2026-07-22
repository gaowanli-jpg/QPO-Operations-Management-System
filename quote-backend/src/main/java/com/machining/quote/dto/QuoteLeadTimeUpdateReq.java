package com.machining.quote.dto;

import lombok.Data;

@Data
public class QuoteLeadTimeUpdateReq {
    private Integer rfqId;
    private Integer itemId;
    private String quoteLeadTime;
}
