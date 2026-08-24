package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_change_record_item")
public class SalesOrderChangeRecordItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long changeRecordId;
    private Long salesOrderItemId;
    private Integer lineNo;
    private String partNo;
    private String version;
    private String changedFieldsJson;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDateTime createdAt;
}
