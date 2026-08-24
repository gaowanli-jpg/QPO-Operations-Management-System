package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangeItemDataReplacementConfirmResp {
    private Boolean success;
    private String message;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long changeRecordId;
    private Integer updatedCount;
    private List<String> changedFields = new ArrayList<>();
    private SalesOrderU9ItemDataReplacementSummaryDTO summary = new SalesOrderU9ItemDataReplacementSummaryDTO();
    private SalesOrderChangePreviewResp preview;
}
