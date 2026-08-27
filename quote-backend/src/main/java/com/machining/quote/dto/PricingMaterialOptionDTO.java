package com.machining.quote.dto;

import lombok.Data;

@Data
public class PricingMaterialOptionDTO {
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
}
