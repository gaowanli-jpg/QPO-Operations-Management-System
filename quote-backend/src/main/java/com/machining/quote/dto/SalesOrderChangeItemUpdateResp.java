package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderChangeItemUpdateResp {
    private Boolean success;
    private String message;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long changeRecordId;
    private Integer updatedCount;
    private List<String> changedFields = new ArrayList<>();
    private SalesOrderChangePreviewResp preview;
}
