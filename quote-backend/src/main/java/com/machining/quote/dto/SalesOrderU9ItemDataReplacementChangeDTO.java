package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderU9ItemDataReplacementChangeDTO {
    private String field;
    private String label;
    private Object beforeValue;
    private Object afterValue;
}
