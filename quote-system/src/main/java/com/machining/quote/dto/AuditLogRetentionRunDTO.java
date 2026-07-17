package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogRetentionRunDTO {
    private String status;
    private Integer deletedCount;
    private String message;
    private String archiveFile;
    private LocalDateTime runAt;
}
