package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProcessDetailDTO {
    private Long id;
    private Long categoryId;
    private String processCode;
    private String categoryName;
    private BigDecimal hourlyRate;
    private BigDecimal detailHourlyRate;
    private BigDecimal categoryHourlyRate;
    private BigDecimal effectiveHourlyRate;
    private String detailName;
    private String detailRemarks;
    private Boolean enabled;
    private Boolean detailEnabled;
    private Boolean categoryEnabled;
    private Boolean effectiveEnabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
