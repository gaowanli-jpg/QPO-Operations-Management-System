package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalesOrderU9SyncPreviewLine {
    private Long salesOrderItemId;
    private Long materialNoSourceTicketId;
    private Integer lineNo;
    private String partNo;
    private String version;
    private String salesMaterialNo;
    private String description;
    private String materialNo;
    private String itemCode;
    private String materialNoStatus;
    private String materialNoSource;
    private Integer qty;
    private BigDecimal finalUnitPrice;
    private LocalDate deliveryDate;
    private Boolean ready;
    private String blockedReason;
}
