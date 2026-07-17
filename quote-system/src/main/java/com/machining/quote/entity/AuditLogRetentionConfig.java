package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log_retention_config")
public class AuditLogRetentionConfig {
    @TableId(type = IdType.INPUT)
    private Integer id;

    private Boolean enabled;
    private Integer retentionDays;
    private Boolean archiveBeforeCleanup;
    private String cleanupTime;
    private LocalDateTime lastRunAt;
    private Integer lastRunDeletedCount;
    private String lastRunStatus;
    private String lastRunMessage;
    private String lastArchiveFile;
    private LocalDateTime updatedAt;
}
