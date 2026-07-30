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
    private List<SalesOrderQpoExcelLineDTO> lines = new ArrayList<>();
}
