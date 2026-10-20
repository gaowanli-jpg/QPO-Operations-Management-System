package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9ItemUpdateResp {
    private Boolean success;
    private String message;
    private Long salesOrderId;
    private String salesOrderNo;
    private Integer updatedCount;
    private List<String> changedFields = new ArrayList<>();
    private SalesOrderU9SyncPreviewResp preview;
}
