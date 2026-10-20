package com.machining.quote.dto.dashboard;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardSummaryCardDTO {
    private String key;
    private String title;
    private Long value = 0L;
    private String unit;
    private String helper;
    private String trendText;
    private String tone;
    private Map<String, Object> target = new LinkedHashMap<>();
    private String permission;
}
