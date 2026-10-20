package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderReleaseAppendRow {
    private Long salesOrderItemId;
    private Integer sourceRowNumber;
    private Integer lineNo;
    private String partNo;
    private String version;
    private Integer qty;
    private String status;
    private String warningMessage;
}
