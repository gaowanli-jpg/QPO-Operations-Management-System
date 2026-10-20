package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_material_batch_item")
public class MesMaterialBatchItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Integer ticketId;
    private String lineRequestId;
    private Long mesRequestRowId;
    private String drawingNo;
    private String drawingVersion;
    private String itemKey;
    private String status;
    private String mesTaskId;
    private String mesTaskNo;
    private String productId;
    private String productCode;
    private String productName;
    private String responseStatus;
    private String message;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
