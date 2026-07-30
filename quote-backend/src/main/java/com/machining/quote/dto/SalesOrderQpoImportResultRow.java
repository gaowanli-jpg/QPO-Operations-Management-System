package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderQpoImportResultRow {
    private boolean success;
    private String status;
    private Long salesOrderId;
    private String salesOrderNo;
    private Integer rfqId;
    private String rfqNo;
    private String customerOrderNo;
    private String internalProjectNo;
    private Integer lineCount;
    private Integer matchedItemCount;
    private Integer unmatchedItemCount;
    private String warningMessage;
    private String errorMessage;
}
