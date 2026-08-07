package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SalesOrderQpoImportDefaultsDTO {
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
}
