package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OrderItemMaintenanceLineReq {
    private Integer itemId;
    private String partNo;
    private String version;
    private Integer qty;
    private String leadTime;
    private String quoteLeadTime;
    private String internalTargetLeadTime;
    private String description;
    private String materialName;
    private String surfaceTreatment;
    private String remark;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
}
