package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SalesOrderChangeNoticeQueryReq {
    private Long customerId;
    private String salesOrderNo;
    private Integer lineNo;
    private String changeType;
    private String targetType;
    private String status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String view;
    private Integer page;
    private Integer pageSize;
}
