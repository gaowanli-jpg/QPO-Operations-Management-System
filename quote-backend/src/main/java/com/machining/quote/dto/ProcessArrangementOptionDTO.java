package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessArrangementOptionDTO {
    private String optionKey;
    private String sourceType;
    private String label;
    private String calcType;
    private Long processDetailId;
    private Long processCategoryId;
    private String processCode;
    private String categoryName;
    private String detailName;
    private BigDecimal detailHourlyRate;
    private BigDecimal categoryHourlyRate;
    private BigDecimal effectiveHourlyRate;
    private BigDecimal hourlyRate;
    private Integer feeItemId;
    private String feeCode;
    private String feeName;
    private Boolean enabled;
    private Boolean detailEnabled;
    private Boolean categoryEnabled;
    private Boolean effectiveEnabled;
}
