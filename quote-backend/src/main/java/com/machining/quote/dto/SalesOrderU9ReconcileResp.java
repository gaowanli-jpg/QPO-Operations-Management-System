package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9ReconcileResp {
    private Long salesOrderId;
    private String salesOrderNo;
    private String code;
    private String message;
    private String u9SyncStatus;
    private String u9OrderId;
    private String u9OrderCode;
    private List<String> warnings = new ArrayList<>();
}
