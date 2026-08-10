package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReleaseItemDataReplacementExcelParseResult {
    private Integer headerRowNumber;
    private List<String> recognizedFields = new ArrayList<>();
    private List<ReleaseItemDataReplacementExcelRowDTO> rows = new ArrayList<>();

    public boolean hasErrors() {
        return rows.stream().anyMatch(ReleaseItemDataReplacementExcelRowDTO::hasErrors);
    }
}
