package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_source_rfq")
public class SalesOrderSourceRfq {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;
    private Integer rfqId;
    private LocalDateTime createdAt;
}
