package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u9_master_data_sync_run")
public class U9MasterDataSyncRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String syncType;
    private String u9OrgCode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer fetchedCount;
    private Integer upsertedCount;
    private Integer disabledCount;
    private String message;
    private Long createdBy;
    private LocalDateTime createdAt;
}
