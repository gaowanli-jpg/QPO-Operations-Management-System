package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_master_data_sync_run")
public class MesMasterDataSyncRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String syncType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer fetchedCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer duplicateCount;
    private Integer upsertedCount;
    private Integer disabledCount;
    private String message;
    private Long createdBy;
    private LocalDateTime createdAt;
}
