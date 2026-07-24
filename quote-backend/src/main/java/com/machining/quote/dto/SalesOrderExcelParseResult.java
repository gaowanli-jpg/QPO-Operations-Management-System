package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderExcelParseResult {
    private int sourceRowCount;
    private int orderCount;
    private int lineCount;
    private List<U9SalesOrderHeadDTO> orders = new ArrayList<>();
}
