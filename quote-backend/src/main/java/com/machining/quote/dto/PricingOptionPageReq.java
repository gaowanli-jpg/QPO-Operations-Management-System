package com.machining.quote.dto;

import lombok.Data;

@Data
public class PricingOptionPageReq {
    private String keyword;
    private Integer page;
    private Integer pageSize;
}
