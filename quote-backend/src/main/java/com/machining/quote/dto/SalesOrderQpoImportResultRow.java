package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderQpoImportResultRow {
    private boolean success;
    private String status;
    private Long salesOrderId;
    private String salesOrderNo;
    private String customerOrderNo;
    private String internalProjectNo;
    private Integer lineCount;
    private Integer matchedItemCount;
    private Integer unmatchedItemCount;
    private Integer uploadedDrawingCount;
    private Integer matchedDrawingCount;
    private Integer missingDrawingCount;
    private Integer unmatchedDrawingCount;
    private String warningMessage;
    private String errorMessage;
}
