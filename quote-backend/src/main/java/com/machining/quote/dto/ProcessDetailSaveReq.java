package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessDetailSaveReq {
    private Long categoryId;
    private BigDecimal hourlyRate;
    private String detailName;
    private String detailRemarks;
    private Boolean enabled;
    private Integer sortNo;
}
