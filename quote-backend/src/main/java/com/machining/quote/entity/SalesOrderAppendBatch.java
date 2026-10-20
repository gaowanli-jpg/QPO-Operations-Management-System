package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "sales_order_append_batch", autoResultMap = true)
public class SalesOrderAppendBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String appendBatchKey;
    private Long targetSalesOrderId;
    private String requestHash;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object sourceRfqIds;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object ticketIds;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object salesOrderItemIds;

    private Integer itemCount;
    private Long createdBy;
    private LocalDateTime createdAt;
}
