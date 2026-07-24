package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderImportResp {
    private boolean success;
    private String batchNo;
    private String status;
    private int totalOrderCount;
    private int successOrderCount;
    private int failedOrderCount;
    private String errorMessage;
    private List<SalesOrderImportResultRow> rows = new ArrayList<>();
}
