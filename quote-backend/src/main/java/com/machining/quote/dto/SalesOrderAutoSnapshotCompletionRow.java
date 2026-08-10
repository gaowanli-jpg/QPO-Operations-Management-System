package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderAutoSnapshotCompletionRow {
    private Long salesOrderId;
    private Long salesOrderItemId;
    private String salesOrderNo;
    private String partNo;
    private String version;
    private Boolean matched;
    private Boolean updated;
    private Boolean skipped;
    private Integer sourceRfqId;
    private Integer sourceItemId;
    private String matchStatus;
    private String matchMessage;
    private String materialNoReuseStatus;
    private String materialNoReuseMessage;
    private String assemblyRelationStatus;
}
