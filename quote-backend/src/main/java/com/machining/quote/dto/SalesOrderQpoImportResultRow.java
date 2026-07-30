package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderQpoImportResultRow {
    private boolean success;
    private String status;
    private Integer rfqId;
    private String rfqNo;
    private String customerOrderNo;
    private String internalProjectNo;
    private Integer lineCount;
    private String warningMessage;
    private String errorMessage;
}
