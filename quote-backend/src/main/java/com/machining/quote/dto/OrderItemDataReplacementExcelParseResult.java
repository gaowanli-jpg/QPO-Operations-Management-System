package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OrderItemDataReplacementExcelParseResult {
    private Integer headerRowNumber;
    private List<String> recognizedFields = new ArrayList<>();
    private List<OrderItemDataReplacementExcelRowDTO> rows = new ArrayList<>();
}
