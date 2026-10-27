package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class ProductionItemAutoDispatchConfigDTO {
    private String id;
    private boolean enabled;
    private String scheduleMode;
    private LocalTime dailyRunTime;
    private Integer batchSize;
    private Integer batchIntervalMs;
    private Integer maxRounds;
    private Long warnDurationMs;
    private Integer failureBreaker;
    private boolean includeFailed;

    private LocalDateTime lastRunAt;
    private LocalDate lastRunDate;
    private Long lastTaskId;
    private String lastStatus;
    private String lastMessage;
    private Integer lastTotal;
    private Integer lastSuccess;
    private Integer lastFailed;
    private Integer lastSkipped;
    private Integer lastRounds;
    private Long lastDurationMs;

    private LocalDateTime updatedAt;
}
