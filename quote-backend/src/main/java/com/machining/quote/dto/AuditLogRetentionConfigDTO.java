package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogRetentionConfigDTO {
    private boolean enabled;
    private Integer retentionDays;
    private boolean archiveBeforeCleanup;
    private String cleanupTime;
    private LocalDateTime lastRunAt;
    private Integer lastRunDeletedCount;
    private String lastRunStatus;
    private String lastRunMessage;
    private String lastArchiveFile;
    private LocalDateTime updatedAt;
}
