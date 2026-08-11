package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LockedItemQueryReq {
    private Integer page;
    private Integer pageSize;
    private String customerCode;
    private String customerName;
    private String rfqNo;
    private String partNo;
    private String version;
    private String materialNo;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String itemType;
    private String allocationType;
    private String mesProductionStatus;
    private String wmsProductionOrderNo;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
