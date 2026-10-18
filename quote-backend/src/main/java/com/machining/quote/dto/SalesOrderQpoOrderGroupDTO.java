package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderQpoOrderGroupDTO {
    private String groupKey;
    private String customerOrderNo;
    private String internalProjectNo;
    private LocalDate salesOrderDate;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String manufacturingProcessType;
    private String reservedField1;
    private String reservedField2;
    private String reservedField3;
    private List<SalesOrderQpoExcelLineDTO> lines = new ArrayList<>();
}
