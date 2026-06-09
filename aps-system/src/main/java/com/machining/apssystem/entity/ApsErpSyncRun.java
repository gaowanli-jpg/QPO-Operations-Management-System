package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_erp_sync_run")
public class ApsErpSyncRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceSystem;
    private String syncMode;
    private Integer requestAfterTicketId;
    private Integer requestLimit;
    private Integer receivedCount;
    private Integer importedCount;
    private Integer skippedCount;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
