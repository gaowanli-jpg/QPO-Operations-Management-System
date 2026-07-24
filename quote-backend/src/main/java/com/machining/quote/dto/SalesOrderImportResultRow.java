package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderImportResultRow {
    private String otherId;
    private String customerOrderNo;
    private String u9Id;
    private String u9Code;
    private boolean success;
    private String status;
    private String errorMessage;
}
