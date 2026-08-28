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
    private String mesDensityRaw;
    private Double platePrice;
    private Double barPrice;
    private Double tubePrice;
    private String remarks;
}
