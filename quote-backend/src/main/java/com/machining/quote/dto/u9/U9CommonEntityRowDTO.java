package com.machining.quote.dto.u9;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class U9CommonEntityRowDTO {
    @JsonIgnore
    private Map<String, Object> values = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        values.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return values;
    }

    @JsonIgnore
    public Object value(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        if (values.containsKey(field)) {
            return values.get(field);
        }
        return nestedValue(values, field.split("\\."), 0);
    }

    @JsonIgnore
    public String text(String field) {
        Object value = value(field);
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = toText(item);
                if (text != null) {
                    return text;
                }
            }
            return null;
        }
        return toText(value);
    }

    @JsonIgnore
    public Boolean bool(String field) {
        Object value = value(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = toText(value);
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (List.of("true", "1", "yes", "y", "是", "有效").contains(normalized)) {
            return true;
        }
        if (List.of("false", "0", "no", "n", "否", "无效").contains(normalized)) {
            return false;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object nestedValue(Object current, String[] parts, int index) {
        if (current == null || index >= parts.length) {
            return current;
        }
        String key = parts[index];
        if (current instanceof Map<?, ?> map) {
            Object next = map.get(key);
            if (next == null) {
                next = map.get(parts[index]);
            }
            return nestedValue(next, parts, index + 1);
        }
        if (current instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                Object nested = nestedValue(item, parts, index);
                if (nested instanceof Iterable<?> nestedIterable) {
                    for (Object nestedItem : nestedIterable) {
                        result.add(nestedItem);
                    }
                } else if (nested != null) {
                    result.add(nested);
                }
            }
            return result;
        }
        return null;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
