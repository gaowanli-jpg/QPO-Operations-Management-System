package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class OrderItemDataReplacementExcelRowDTO {
    private Integer excelRowNumber;
    private Integer itemId;
    private String partNo;
    private String version;
    private String normalizedPartNo;
    private String normalizedVersion;
    private Map<String, Object> values = new LinkedHashMap<>();
    private List<OrderItemDataReplacementExcelErrorDTO> errors = new ArrayList<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
