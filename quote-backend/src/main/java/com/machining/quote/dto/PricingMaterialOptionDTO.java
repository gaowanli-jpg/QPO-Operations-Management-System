package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

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
    private List<MaterialPriceSpecDTO> priceSpecs;
    private String remarks;
}
