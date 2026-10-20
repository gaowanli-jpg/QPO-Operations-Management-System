package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessCategorySaveReq {
    private String processCode;
    private String categoryName;
    private BigDecimal hourlyRate;
    private String remarks;
    private String sourceType;
    private String mesExternalId;
    private Boolean enabled;
    private Integer sortNo;
}
