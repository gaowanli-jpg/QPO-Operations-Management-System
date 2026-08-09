package com.machining.quote.dto;

import lombok.Data;

@Data
public class MaterialMasterSaveReq {
    private String materialCode;
    private String materialName;
    private Long materialTypeId;
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
}
