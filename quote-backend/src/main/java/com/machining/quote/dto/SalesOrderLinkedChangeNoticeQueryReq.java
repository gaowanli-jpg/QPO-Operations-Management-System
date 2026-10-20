package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SalesOrderLinkedChangeNoticeQueryReq {
    private Long customerId;
    private String customerCode;
    private String customerOrderNo;
    private String internalProjectNo;
    private String salesOrderNo;
    private String partNo;
    private String version;
    private String triggerScope;
    private String handlingStatus;
    private String wmsStatus;
    private String mesProductionStatus;
    private String stockPickingStatus;
    private Boolean hasDownstreamImpact;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String keyword;
    private Integer page;
    private Integer pageSize;
}
