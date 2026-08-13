package com.machining.quote.dto;

import lombok.Data;

@Data
public class MesMasterDataSyncScheduleConfigReq {
    private Boolean enabled;
    private Long startupDelayMs;
    private Long fixedDelayMs;
}
