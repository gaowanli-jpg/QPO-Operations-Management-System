package com.machining.quote.dto.dashboard;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardChartItemDTO {
    private String name;
    private Long value = 0L;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerShortName;
    private String targetPath;
    private Map<String, Object> query = new LinkedHashMap<>();
}
