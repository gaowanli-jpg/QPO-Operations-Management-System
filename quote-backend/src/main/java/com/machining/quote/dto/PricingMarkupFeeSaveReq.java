package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingMarkupFeeSaveReq {
    private String pricingScope;
    private String feeCategory;
    private String feeName;
    private BigDecimal ratePercent;
    private Integer sortNo;
    private Boolean enabled;
}
