package com.machining.quote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JsonSnapshotCopyService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public boolean isMissingSnapshot(Object value) {
        return isMissing(value, true);
    }

    public Object deepCopySnapshot(Object value) {
        return copyValue(value, true);
    }

    private boolean isMissing(Object value, boolean parseTopLevelString) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            String cleaned = text.trim();
            if (cleaned.isEmpty()) {
                return true;
            }
            if (!parseTopLevelString) {
                return false;
            }
            Object parsed = parseJson(cleaned);
            return parsed == null || isMissing(parsed, false);
        }
        if (value instanceof JsonNode node) {
            if (node.isMissingNode() || node.isNull()) {
                return true;
            }
            if (node.isContainerNode()) {
                return node.isEmpty();
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return Array.getLength(value) == 0;
        }
        Object pgObjectValue = pgObjectValue(value);
        if (pgObjectValue != null) {
            return isMissing(pgObjectValue, true);
        }
        return false;
    }

    private Object copyValue(Object value, boolean parseTopLevelString) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            if (!parseTopLevelString) {
                return text;
            }
            String cleaned = text.trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            return parseJson(cleaned);
        }
        if (value instanceof JsonNode node) {
            return copyJsonNode(node);
        }
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, mapValue) -> result.put(String.valueOf(key), copyValue(mapValue, false)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> result = new ArrayList<>();
            for (Object item : collection) {
                result.add(copyValue(item, false));
            }
            return result;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            ArrayList<Object> result = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(copyValue(Array.get(value, i), false));
            }
            return result;
        }
        Object pgObjectValue = pgObjectValue(value);
        if (pgObjectValue != null) {
            return copyValue(pgObjectValue, true);
        }
        if (isScalar(value)) {
            return value;
        }
        return copyPojo(value);
    }

    private Object parseJson(String text) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(text, Object.class);
            return copyValue(parsed, false);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private Object copyJsonNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), copyJsonNode(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayList<Object> result = new ArrayList<>();
            node.forEach(item -> result.add(copyJsonNode(item)));
            return result;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            if (node.canConvertToInt()) {
                return node.intValue();
            }
            if (node.canConvertToLong()) {
                return node.longValue();
            }
            return node.bigIntegerValue();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBinary()) {
            try {
                return node.binaryValue();
            } catch (Exception ignored) {
                return null;
            }
        }
        return node.asText(null);
    }

    private Object copyPojo(Object value) {
        try {
            Object converted = OBJECT_MAPPER.convertValue(value, Object.class);
            if (converted == value) {
                return value;
            }
            return copyValue(converted, false);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Object pgObjectValue(Object value) {
        if (value == null || !"org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod("getValue");
            return method.invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean isScalar(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof BigDecimal
                || value instanceof BigInteger;
    }
}
