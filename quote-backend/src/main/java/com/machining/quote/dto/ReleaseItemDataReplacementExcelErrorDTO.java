package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemDataReplacementExcelErrorDTO {
    private Integer excelRowNumber;
    private Integer columnNumber;
    private String field;
    private String label;
    private String message;
}
