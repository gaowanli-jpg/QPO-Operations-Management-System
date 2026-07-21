package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LockedItemDTO {
    private Integer id;
    private Integer ticketId;
    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private Integer itemId;
    private String allocationType;
    private Integer qty;
    private String materialNo;
    private String partNo;
    private String version;
    private String internalProjectNo;
    private String wmsProductionOrderNo;
    private Object sourcePayload;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
