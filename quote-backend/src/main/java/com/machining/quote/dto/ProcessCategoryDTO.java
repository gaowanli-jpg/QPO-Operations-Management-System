package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProcessCategoryDTO {
    private Long id;
    private String processCode;
    private String categoryName;
    private BigDecimal hourlyRate;
    private String remarks;
    private String sourceType;
    private String mesExternalId;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime mesSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
