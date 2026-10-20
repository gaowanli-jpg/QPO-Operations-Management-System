package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderU9ItemDataReplacementExcelRowDTO {
    private Integer excelRowNumber;
    private String partNo;
    private String version;
    private Integer lineNo;
    private String normalizedPartNo;
    private String normalizedVersion;
    private Map<String, Object> values = new LinkedHashMap<>();
    private List<SalesOrderU9ItemDataReplacementExcelErrorDTO> errors = new ArrayList<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
