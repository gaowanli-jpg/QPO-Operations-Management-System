package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ReleaseItemDataReplacementExcelRowDTO {
    private Integer excelRowNumber;
    private String partNo;
    private String version;
    private String normalizedPartNo;
    private String normalizedVersion;
    private Map<String, Object> values = new LinkedHashMap<>();
    private List<ReleaseItemDataReplacementExcelErrorDTO> errors = new ArrayList<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
