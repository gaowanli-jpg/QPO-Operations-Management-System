package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mes_production_batch_item")
public class MesProductionBatchItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Integer allocationId;
    private String lineRequestId;
    private String saleOrderNo;
    private String saleOrderItemNo;
    private String productCode;
    private String itemKey;
    private BigDecimal demandQty;
    private String status;
    private String mesRecordId;
    private String mesPlanNo;
    private String responseStatus;
    private String message;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
