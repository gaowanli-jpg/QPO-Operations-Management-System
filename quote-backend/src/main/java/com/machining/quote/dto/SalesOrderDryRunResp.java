package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderDryRunResp {
    private boolean success;
    private String batchNo;
    private int totalOrderCount;
    private int totalLineCount;
    private List<U9SalesOrderHeadDTO> orders = new ArrayList<>();
}
