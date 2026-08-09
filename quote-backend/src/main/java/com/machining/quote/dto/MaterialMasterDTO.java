package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialMasterDTO {
    private Long id;
    private String materialCode;
    private String materialName;
    private Long materialTypeId;
    private String materialTypeCode;
    private String materialTypeName;
    private String materialCategory;
    private Double density;
    private Double plateDensity;
    private Double platePrice;
    private Double barDensity;
    private Double barPrice;
    private Double tubeDensity;
    private Double tubePrice;
    private String remarks;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
