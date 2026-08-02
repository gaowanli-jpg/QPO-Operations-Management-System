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
@TableName(value = "order_delivery_ticket", autoResultMap = true)
public class OrderDeliveryTicket {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private Integer itemId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String orderType;
    private String internalProjectNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String projectNo;
    private String itemType;
    private String partNo;
    private String description;
    private Integer qty;
    private String materialName;
    private String engineeringMaterialName;
    private String surfaceTreatment;
    private String version;
    private LocalDate startDate;
    private LocalDate deliveryDate;
    private String remark;
    private String arrangeType;
    private String drawingUrl;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;
    @TableField(typeHandler = JsonbTypeHandler.class)
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
}
