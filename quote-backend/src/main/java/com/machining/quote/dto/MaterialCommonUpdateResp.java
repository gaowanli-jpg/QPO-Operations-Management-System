package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialCommonUpdateResp {
    private Boolean commonMaterial;
    private Integer requestedCount;
    private Integer matchedCount;
    private Integer updatedCount;
    private List<String> materialCodeSamples;
}
