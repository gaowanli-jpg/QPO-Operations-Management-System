package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialMasterSaveReq {
    private String materialCode;
    private String materialName;
    private Long materialTypeId;
    private String materialCategory;
    private String categoryName;
    private Double density;
    private List<MaterialPriceSpecDTO> priceSpecs;
    private String remarks;
    private Boolean commonMaterial;
    private Boolean enabled;
    private Integer sortNo;
}
