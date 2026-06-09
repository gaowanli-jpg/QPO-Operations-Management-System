package com.machining.apssystem.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WorkItemSummaryDTO {
    private Long id;
    private Integer erpTicketId;
    private String rfqNo;
    private String internalProjectNo;
    private String customerName;
    private String partNo;
    private String version;
    private Integer qty;
    private LocalDate deliveryDate;
    private String materialName;
    private String materialType;
    private BigDecimal materialDemandWeightKg;
    private BigDecimal totalWorkHours;
    private String partStockStatus;
    private String materialStockStatus;
    private String assignmentStatus;
    private String status;
}
