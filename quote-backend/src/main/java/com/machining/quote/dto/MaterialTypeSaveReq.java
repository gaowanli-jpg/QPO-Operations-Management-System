package com.machining.quote.dto;

import lombok.Data;

@Data
public class MaterialTypeSaveReq {
    private String typeCode;
    private String typeName;
    private String remarks;
    private Boolean enabled;
    private Integer sortNo;
}
