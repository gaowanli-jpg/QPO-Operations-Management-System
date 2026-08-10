package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DeliveredItemDTO {
    private Integer id;
    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private Integer itemId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private String orderType;
    private String remark;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String projectNo;
    private String itemType;
    private String materialName;
    private String engineeringMaterialCode;
    private String engineeringMaterialName;
    private String engineeringMaterialTypeCode;
    private String engineeringMaterialTypeName;
    private String engineeringMaterialCategory;
    private String materialShape;
    private String surfaceTreatment;
    private String drawingUrl;
    private Object processRoute;
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
    private String materialNo;
    private String mesSyncStatus;
    private String mesSyncMessage;
    private LocalDateTime mesSyncedAt;
    private String mesRequestId;
    private String mesExternalTaskId;
    private LocalDateTime mesCallbackReceivedAt;
    private String mesCallbackMessage;
    private String mesDedupKey;
    private String mesRequestRole;
    private Integer mesMasterTicketId;
    private String mesMasterRequestId;
    private String wmsAllocateStatus;
    private String wmsAllocateMessage;
    private LocalDateTime wmsAllocatedAt;
    private LocalDate deliveryDate;
}
