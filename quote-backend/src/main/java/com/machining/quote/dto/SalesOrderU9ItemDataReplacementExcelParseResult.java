package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9ItemDataReplacementExcelParseResult {
    private Integer headerRowNumber;
    private List<String> recognizedFields = new ArrayList<>();
    private List<SalesOrderU9ItemDataReplacementExcelRowDTO> rows = new ArrayList<>();

    public boolean hasErrors() {
        return rows.stream().anyMatch(SalesOrderU9ItemDataReplacementExcelRowDTO::hasErrors);
    }
}
