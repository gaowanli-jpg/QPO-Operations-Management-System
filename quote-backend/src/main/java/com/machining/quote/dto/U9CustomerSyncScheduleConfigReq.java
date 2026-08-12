package com.machining.quote.dto;

import lombok.Data;

@Data
public class U9CustomerSyncScheduleConfigReq {
    private Boolean enabled;
    private Long startupDelayMs;
    private Long fixedDelayMs;
}
