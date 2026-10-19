package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderChangeDispatchSettingDTO {
    private String dispatchMode;
    private Long autoDispatchEpoch;
    private LocalDateTime autoEnabledAt;
    private LocalDateTime updatedAt;
}
