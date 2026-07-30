package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderQpoExcelParseResult {
    private int sourceRowCount;
    private int orderCount;
    private int lineCount;
    private List<SalesOrderQpoOrderGroupDTO> orders = new ArrayList<>();
}
