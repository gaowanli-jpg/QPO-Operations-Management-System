package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "order_delivery_ticket_allocation", autoResultMap = true)
public class OrderDeliveryTicketAllocation {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer ticketId;
    private Integer rfqId;
    private Integer itemId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String allocationSource;
    private String allocationType;
    private Long wmsReservationTicketId;
    private Integer qty;
    private Integer ticketQty;
    private String materialNo;
    private String orderType;
    private String itemType;
    private String partNo;
    private String version;
    private String rfqNo;
    private String customerName;
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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
    @TableField(typeHandler = JsonbTypeHandler.class)
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
    private String discardSource;
    private Long discardSourceSalesOrderItemId;
}
