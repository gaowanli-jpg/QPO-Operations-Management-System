package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class U9CustomerSyncScheduleConfigDTO {
    private boolean enabled;
    private Long startupDelayMs;
    private Long fixedDelayMs;
    private LocalDateTime lastScheduledRunAt;
    private Long lastScheduledSyncRunId;
    private String lastScheduledStatus;
    private String lastScheduledMessage;
    private LocalDateTime updatedAt;
    private boolean fallback;
}
