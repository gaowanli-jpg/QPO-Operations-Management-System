package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
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
    private String customerOrderNo;
    private String customerProjectNo;
    private String materialName;
    private String engineeringMaterialName;
    private String surfaceTreatment;
    private String description;
    private LocalDate deliveryDate;
    private Object processRoute;
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private String wmsProductionOrderNo;
    private Object sourcePayload;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
