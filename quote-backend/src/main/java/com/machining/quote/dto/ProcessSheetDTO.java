package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProcessSheetDTO {
    private Integer id;
    private Integer rfqId;
    private Integer itemId;
    private String internalProjectNo;
    private String itemType;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private String materialName;
    private String engineeringMaterialName;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private String substituteMaterial;
    private String surfaceTreatment;
    private String heatTreatment;
    private LocalDate deliveryDate;
    private String remark;
    private Object processRoute;
}
