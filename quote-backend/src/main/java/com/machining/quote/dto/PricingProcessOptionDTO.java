package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingProcessOptionDTO {
    private String optionKey;
    private String sourceType;
    private Long processDetailId;
    private Long processCategoryId;
    private String processCode;
    private String categoryName;
    private String detailName;
    private String processName;
    private String label;
    private String calcType;
    private BigDecimal detailHourlyRate;
    private BigDecimal categoryHourlyRate;
    private BigDecimal effectiveHourlyRate;
    private BigDecimal hourlyRate;
    private Integer feeItemId;
    private String feeCode;
    private String feeName;
    private Long usageCount;
}
