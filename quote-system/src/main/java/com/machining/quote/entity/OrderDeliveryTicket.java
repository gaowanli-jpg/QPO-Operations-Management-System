package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName(value = "order_delivery_ticket", autoResultMap = true)
public class OrderDeliveryTicket {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private Integer itemId;
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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object materialData;
    private Double materialGrossWeight;
    private Double partNetWeight;
}
