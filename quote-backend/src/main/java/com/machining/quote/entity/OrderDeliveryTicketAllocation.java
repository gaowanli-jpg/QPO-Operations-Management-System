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
    private String allocationType;
    private Integer qty;
    private String materialNo;
    private String orderType;
    private String itemType;
    private String partNo;
    private String version;
    private String rfqNo;
    private String customerName;
    private String internalProjectNo;
    private String wmsProductionOrderNo;
    private String customerOrderNo;
    private String customerProjectNo;
    private String materialName;
    private String engineeringMaterialName;
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
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
