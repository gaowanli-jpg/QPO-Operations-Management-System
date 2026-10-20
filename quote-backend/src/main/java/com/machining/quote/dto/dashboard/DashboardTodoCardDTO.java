package com.machining.quote.dto.dashboard;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardTodoCardDTO {
    private String key;
    private String title;
    private Long value = 0L;
    private String unit;
    private String description;
    private String path;
    private Map<String, Object> query = new LinkedHashMap<>();
    private String permission;
    private String tone;
}
