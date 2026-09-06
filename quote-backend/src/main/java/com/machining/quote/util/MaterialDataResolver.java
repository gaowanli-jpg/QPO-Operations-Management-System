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
        String savedMaterialName = stringValue(item.getEngineeringMaterialName());
        if (!savedMaterialName.isEmpty()) {
            return savedMaterialName;
        }
        if (!"ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            String materialDataName = stringValue(normalizeMap(item.getMaterialData()).get("name"));
            if (!materialDataName.isEmpty()) {
                return materialDataName;
            }
        }
        return stringValue(item.getMaterialName());
    }

    public static String materialNameFromData(ItemQuoteVersion item) {
        if (item == null || "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return stringValue(normalizeMap(item.getMaterialData()).get("name"));
    }

    public static String materialCode(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        String savedMaterialCode = stringValue(item.getEngineeringMaterialCode());
        if (!savedMaterialCode.isEmpty()) {
            return savedMaterialCode;
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return savedMaterialCode.isEmpty() ? materialCode(item.getMaterialData()) : savedMaterialCode;
    }

    public static String materialTypeCode(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        String savedTypeCode = stringValue(item.getEngineeringMaterialTypeCode());
        if (!savedTypeCode.isEmpty()) {
            return savedTypeCode;
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return savedTypeCode.isEmpty() ? materialTypeCode(item.getMaterialData()) : savedTypeCode;
    }

    public static String materialTypeName(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        String savedTypeName = stringValue(item.getEngineeringMaterialTypeName());
        if (!savedTypeName.isEmpty()) {
            return savedTypeName;
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return savedTypeName.isEmpty() ? materialTypeName(item.getMaterialData()) : savedTypeName;
    }

    public static String materialCategory(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        String savedCategory = stringValue(item.getEngineeringMaterialCategory());
        if (!savedCategory.isEmpty()) {
            return savedCategory;
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return savedCategory.isEmpty() ? materialCategory(item.getMaterialData()) : savedCategory;
    }

    public static String materialCode(Object materialData) {
        return stringValue(normalizeMap(materialData).get("materialCode"));
    }

    public static String materialTypeCode(Object materialData) {
        return stringValue(normalizeMap(materialData).get("materialTypeCode"));
    }

    public static String materialTypeName(Object materialData) {
        return stringValue(normalizeMap(materialData).get("materialTypeName"));
    }

    public static String materialCategory(Object materialData) {
        return stringValue(normalizeMap(materialData).get("materialCategory"));
    }

    public static String materialPriceSpecCode(ItemQuoteVersion item) {
        if (item == null || "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return materialPriceSpecCode(item.getMaterialData());
    }

    public static String materialPriceSpecCode(Object materialData) {
        return stringValue(normalizeMap(materialData).get("priceSpecCode"));
    }

    public static String materialPriceSpecName(ItemQuoteVersion item) {
        if (item == null || "ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
        return materialPriceSpecName(item.getMaterialData());
    }

    public static String materialPriceSpecName(Object materialData) {
        return stringValue(normalizeMap(materialData).get("priceSpecName"));
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
        if (item == null) {
            return "";
        }
        String savedMaterialShape = stringValue(item.getMaterialShape());
        if (!savedMaterialShape.isEmpty()) {
            return savedMaterialShape;
        }
        if ("ASSEMBLY".equalsIgnoreCase(item.getQuoteMode())) {
            return "";
        }
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
