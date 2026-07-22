package com.machining.quote.dto;

import lombok.Data;

@Data
public class AuditLogRetentionConfigReq {
    private Boolean enabled;
    private Integer retentionDays;
    private Boolean archiveBeforeCleanup;
    private String cleanupTime;
}
