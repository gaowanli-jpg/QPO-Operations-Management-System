package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderReleaseAppendResp {
    private boolean success;
    private String status;
    private Long targetSalesOrderId;
    private String targetSalesOrderNo;
    private Integer appendedItemCount;
    private Integer ticketCount;
    private Integer ticketQty;
    private Map<String, Object> drawingReport;
    private String message;
    private String errorMessage;
    private List<SalesOrderReleaseAppendRow> rows = new ArrayList<>();
}
