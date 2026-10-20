package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_import_batch")
public class SalesOrderImportBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private String sourceFileName;
    private String importMode;
    private String status;
    private Integer totalOrderCount;
    private Integer successOrderCount;
    private Integer failedOrderCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
