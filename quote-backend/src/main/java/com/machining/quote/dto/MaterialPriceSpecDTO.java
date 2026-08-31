package com.machining.quote.dto;

import lombok.Data;

@Data
public class MaterialPriceSpecDTO {
    private Long id;
    private Long materialId;
    private String specCode;
    private String specName;
    private Double unifiedPrice;
    private Double platePrice;
    private Double barPrice;
    private Double tubePrice;
    private Double effectivePlatePrice;
    private Double effectiveBarPrice;
    private Double effectiveTubePrice;
    private Integer sortNo;
}
