package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "order_delivery_ticket_allocation", autoResultMap = true)
public class OrderDeliveryTicketAllocation {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer ticketId;
    private Integer rfqId;
    private Integer itemId;
    private String allocationType;
    private Integer qty;
    private String materialNo;
    private String partNo;
    private String version;
    private String customerName;
    private String internalProjectNo;
    private String wmsProductionOrderNo;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object sourcePayload;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
