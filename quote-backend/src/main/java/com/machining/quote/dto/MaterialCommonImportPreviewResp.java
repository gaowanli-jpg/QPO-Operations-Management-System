package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MaterialCommonImportPreviewResp {
    private Integer uploadedCount;
    private Integer matchedCount;
    private Integer alreadyCommonCount;
    private Integer notFoundCount;
    private Integer duplicateCount;
    private List<Long> matchedMaterialIds = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row {
        private Integer rowNo;
        private String materialCode;
        private Long materialId;
        private String materialName;
        private Boolean commonMaterial;
        private String status;
        private String message;
    }
}
