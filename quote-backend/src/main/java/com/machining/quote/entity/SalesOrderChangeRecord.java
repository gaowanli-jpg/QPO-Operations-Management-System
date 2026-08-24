package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_change_record")
public class SalesOrderChangeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long salesOrderId;
    private String salesOrderNo;
    private String changeType;
    private String reason;
    private Integer updatedCount;
    private String changedFieldsJson;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
}
