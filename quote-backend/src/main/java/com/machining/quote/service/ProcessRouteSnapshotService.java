package com.machining.quote.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProcessRouteSnapshotService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ROUTE_LIST_TYPE = new TypeReference<>() {
    };

    public Object copySnapshot(Object routeObject) {
        if (routeObject == null) {
            return null;
        }
        return normalizeRows(routeObject).stream()
                .map(this::copyMap)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public String formatSnapshot(Object routeObject) {
        List<Map<String, Object>> rows = normalizeRows(routeObject);
        if (rows.isEmpty()) {
            return null;
        }
        String text = rows.stream()
                .map(this::formatRow)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" / "));
        return text.isBlank() ? null : text;
    }

    public int countSnapshotRows(Object routeObject) {
        return normalizeRows(routeObject).size();
    }

    private List<Map<String, Object>> normalizeRows(Object routeObject) {
        if (routeObject == null) {
            return List.of();
        }
        if (routeObject instanceof String text) {
            if (text.isBlank()) {
                return List.of();
            }
            try {
                return OBJECT_MAPPER.readValue(text, ROUTE_LIST_TYPE).stream()
                        .map(this::copyMap)
                        .toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (routeObject instanceof Collection<?> collection) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : collection) {
                Map<String, Object> row = normalizeMap(item);
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
            return rows;
        }
        Map<String, Object> row = normalizeMap(routeObject);
        return row.isEmpty() ? List.of() : List.of(row);
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), copyValue(mapValue)));
        return result;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copyValue(value)));
        return result;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, mapValue) -> result.put(String.valueOf(key), copyValue(mapValue)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::copyValue).collect(Collectors.toCollection(ArrayList::new));
        }
        return value;
    }

    private String formatRow(Map<String, Object> row) {
        String routeType = text(row.get("routeType"));
        String sourceType = text(row.get("sourceType"));
        if ("FEE".equalsIgnoreCase(routeType) || "extra".equalsIgnoreCase(sourceType)) {
            String name = firstText(row.get("processName"), row.get("detailName"), row.get("name"));
            return appendMoney(name, number(row.get("lineCost")));
        }

        String processCode = text(row.get("processCode"));
        String categoryName = text(row.get("categoryName"));
        String detailName = firstText(row.get("detailName"), row.get("processName"), row.get("name"));
        String identity = List.of(processCode, categoryName, detailName).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" / "));
        if (identity.isBlank()) {
            identity = detailName;
        }
        Double hours = firstNumber(row.get("unitWorkHours"), row.get("inputVal"));
        Double hourlyRate = firstNumber(row.get("hourlyRate"), row.get("unitRate"));
        Double lineCost = number(row.get("lineCost"));
        List<String> parts = new ArrayList<>();
        if (hours != null) {
            parts.add(trimNumber(hours) + "h");
        }
        if (hourlyRate != null) {
            parts.add("¥" + trimNumber(hourlyRate));
        }
        if (lineCost != null) {
            parts.add("¥" + trimNumber(lineCost));
        }
        return parts.isEmpty() ? identity : identity + "（" + String.join(" × ", parts) + "）";
    }

    private String appendMoney(String name, Double lineCost) {
        String label = name == null || name.isBlank() ? "费用" : name;
        return lineCost == null ? label : label + "（¥" + trimNumber(lineCost) + "）";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Double firstNumber(Object... values) {
        for (Object value : values) {
            Double number = number(value);
            if (number != null) {
                return number;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double number(Object value) {
        if (value instanceof Number numeric) {
            return numeric.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String trimNumber(Double value) {
        if (value == null) {
            return "";
        }
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }
}
