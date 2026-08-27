package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class MesMasterDataSyncScheduleConfigReq {
    private Boolean enabled;
    private String scheduleMode;
    private LocalTime dailyRunTime;
    private Long startupDelayMs;
    private Long fixedDelayMs;
}
