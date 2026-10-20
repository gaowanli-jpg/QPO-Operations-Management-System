package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_integration_reconcile_run")
public class MesIntegrationReconcileRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String reconcileType;
    private String status;
    private Integer matchedCount;
    private Integer pendingCount;
    private Integer failedCount;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
