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
    private Double platePrice;
    private Double barPrice;
    private Double tubePrice;
    private String remarks;
    private String sourceType;
    private String mesExternalId;
    private String mesMaterialType;
    private String mesPurchaseType;
    private String mesProductionType;
    private String mesDocNum;
    private String mesStandard;
    private String mesWeight;
    private String mesDensityRaw;
    private LocalDateTime mesSyncedAt;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
