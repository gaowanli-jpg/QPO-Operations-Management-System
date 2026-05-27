package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName(value = "order_process_sheet", autoResultMap = true)
public class OrderProcessSheet {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private Integer itemId;
    private String partNo;
    private String version;
    private String description;
    private Integer qty;
    private String materialName;
    private String substituteMaterial;
    private String surfaceTreatment;
    private String heatTreatment;
    private LocalDate deliveryDate;
    private String remark;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;
}
