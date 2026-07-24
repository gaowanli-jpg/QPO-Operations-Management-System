package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOrderExcelRow {
    private String orderBy;
    private String documentType;
    private String customerOrderNo;
    private String businessDate;
    private String itemCode;
    private String customerItemNo;
    private String customerItemName;
    private BigDecimal qty;
    private String tu;
    private String pu;
    private BigDecimal unitPrice;
    private String requireDate;
}
