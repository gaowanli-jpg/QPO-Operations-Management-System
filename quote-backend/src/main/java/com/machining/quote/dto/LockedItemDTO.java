package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class LockedItemDTO {
    private Integer id;
    private Long salesOrderItemId;
    private Integer salesOrderItemLineNo;
    private Long allocationId;
    private Integer ticketId;
    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private Integer itemId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String itemType;
    private String allocationType;
    private Integer qty;
    private Integer ticketQty;
    private String materialNo;
    private String orderType;
    private String partNo;
    private String version;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String materialName;
    private String engineeringMaterialCode;
    private String engineeringMaterialName;
    private String engineeringMaterialTypeCode;
    private String engineeringMaterialTypeName;
    private String engineeringMaterialCategory;
    private String materialShape;
    private String surfaceTreatment;
    private String remark;
    private String description;
    private LocalDate deliveryDate;
    private Object processRoute;
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private Object sourcePayload;
    private String mesProductionStatus;
    private String mesProductionRequestId;
    private String mesProductionExternalId;
    private LocalDateTime mesProductionSyncedAt;
    private String mesProductionMessage;
    private String stockPickingStatus;
    private String stockPickingBizId;
    private String stockPickingExternalId;
    private LocalDateTime stockPickingSyncedAt;
    private String stockPickingMessage;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean discarded;
    private LocalDateTime discardedAt;
    private Long discardedBy;
    private String discardReason;
    private LocalDateTime restoredAt;
    private Long restoredBy;
    private String restoreReason;
}
