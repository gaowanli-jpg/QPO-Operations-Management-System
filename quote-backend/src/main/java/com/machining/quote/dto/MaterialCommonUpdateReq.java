package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialCommonUpdateReq {
    private List<Long> materialIds;
    private Boolean commonMaterial;
}
