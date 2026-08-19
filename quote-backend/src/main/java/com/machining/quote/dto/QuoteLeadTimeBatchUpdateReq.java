package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuoteLeadTimeBatchUpdateReq {
    private Integer rfqId;
    private List<ItemUpdate> items;

    @Data
    public static class ItemUpdate {
        private Integer itemId;
        private String quoteLeadTime;
    }
}
