package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderQpoImportResp {
    private boolean success;
    private String status;
    private String batchNo;
    private Integer totalOrderCount;
    private Integer successOrderCount;
    private Integer failedOrderCount;
    private Integer totalLineCount;
    private String errorMessage;
    private Map<String, Object> drawingReport;
    private List<SalesOrderQpoImportResultRow> rows = new ArrayList<>();
}
