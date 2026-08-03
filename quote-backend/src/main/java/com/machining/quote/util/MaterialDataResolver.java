package com.machining.quote.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;

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

    public static String effectiveSalesOrderMaterialName(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        String engineeringMaterialName = stringValue(item.getEngineeringMaterialName());
        if (!engineeringMaterialName.isEmpty()) {
            return engineeringMaterialName;
        }
        String materialDataName = stringValue(normalizeMap(item.getMaterialData()).get("name"));
        if (!materialDataName.isEmpty()) {
            return materialDataName;
        }
        return stringValue(item.getMaterialName());
    }

    public static String materialShape(Object materialData) {
        return stringValue(normalizeMap(materialData).get("shape"));
    }

    public static String materialShape(ItemQuoteVersion item) {
        if (item == null || "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        String savedMaterialShape = stringValue(item.getMaterialShape());
        return savedMaterialShape.isEmpty() ? materialShape(item.getMaterialData()) : savedMaterialShape;
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
