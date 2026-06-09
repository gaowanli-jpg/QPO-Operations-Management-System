package com.machining.quote.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.ItemQuoteVersion;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialDataResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private MaterialDataResolver() {
    }

    public static String engineeringMaterialName(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        String savedMaterialName = stringValue(item.getEngineeringMaterialName());
        return savedMaterialName.isEmpty() ? stringValue(normalizeMap(item.getMaterialData()).get("name")) : savedMaterialName;
    }

    public static String materialNameFromData(ItemQuoteVersion item) {
        if (item == null || "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return stringValue(normalizeMap(item.getMaterialData()).get("name"));
    }

    public static Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return OBJECT_MAPPER.readValue(text, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

}
