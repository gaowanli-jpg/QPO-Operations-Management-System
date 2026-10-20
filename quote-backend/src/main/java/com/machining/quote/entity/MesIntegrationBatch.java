package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_integration_batch")
public class MesIntegrationBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchType;
    private String bizId;
    private Long systemTaskId;
    private String groupKeyHash;
    private String requestFingerprint;
    private Integer itemCount;
    private String status;
    private Integer attemptNo;
    private LocalDateTime submittedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime completedAt;
    private String responseCode;
    private String responseMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
