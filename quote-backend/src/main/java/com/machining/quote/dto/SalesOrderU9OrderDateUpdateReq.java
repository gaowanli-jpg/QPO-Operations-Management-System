package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SalesOrderU9OrderDateUpdateReq {
    private LocalDate salesOrderDate;
}
