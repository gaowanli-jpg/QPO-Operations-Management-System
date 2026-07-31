package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9SyncResp {
    private boolean success;
    private String status;
    private String message;
    private Integer totalOrderCount;
    private Integer successOrderCount;
    private Integer failedOrderCount;
    private List<SalesOrderU9SyncResultRow> rows = new ArrayList<>();
}
