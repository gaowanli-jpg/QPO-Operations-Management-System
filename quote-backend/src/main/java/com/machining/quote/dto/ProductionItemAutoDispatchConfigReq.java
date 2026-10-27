package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class ProductionItemAutoDispatchConfigReq {
    private Boolean enabled;
    private String scheduleMode;
    private LocalTime dailyRunTime;
    private Integer batchSize;
    private Integer batchIntervalMs;
    private Integer maxRounds;
    private Long warnDurationMs;
    private Integer failureBreaker;
}
