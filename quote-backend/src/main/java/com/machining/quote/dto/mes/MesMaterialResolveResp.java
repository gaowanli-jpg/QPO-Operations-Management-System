package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesMaterialResolveResp {
    private Object code;
    @JsonDeserialize(using = LenientBooleanDeserializer.class)
    private Boolean success;
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String mesRequestId;
    /** Current response identity name at the root; kept separate to detect conflicts. */
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String bizId;
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String status;
    @JsonDeserialize(using = LenientBooleanDeserializer.class)
    private Boolean callbackRequired;
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String materialNo;
    @JsonAlias("msg")
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String message;
    @JsonDeserialize(using = LenientStringDeserializer.class)
    private String externalTraceId;
    @JsonDeserialize(using = LenientDataDeserializer.class)
    private Data data;

    public Boolean getSuccess() {
        if (code != null) {
            return isSuccessCode(code);
        }
        return success;
    }

    public String getMesRequestId() {
        return firstNonBlank(bizId, mesRequestId, data == null ? null : data.getBizId());
    }

    /**
     * Validate every response location that carries the request identity. Some
     * legacy bridge responses put it at the root, while the current contract
     * uses data.bizId. If both are present, neither may contradict the request.
     */
    public boolean matchesRequestId(String expectedRequestId) {
        String expected = trimToNull(expectedRequestId);
        if (expected == null) {
            return false;
        }
        String rootBizId = trimToNull(bizId);
        String rootRequestId = trimToNull(mesRequestId);
        String dataId = trimToNull(data == null ? null : data.getBizId());
        return (rootBizId != null || rootRequestId != null || dataId != null)
                && (rootBizId == null || expected.equals(rootBizId))
                && (rootRequestId == null || expected.equals(rootRequestId))
                && (dataId == null || expected.equals(dataId));
    }

    /**
     * 只判断「显式声明了但与请求矛盾」，不要求必须声明。
     *
     * <p>MES 对同一物料身份重复提交时返回的「已存在」响应往往不带 bizId，
     * 但响应体带着行内料号；料号本身已足以证明身份，不能因为缺 bizId 就丢掉。
     * 反过来，只要任何一处显式声明了 bizId 且与请求不一致，仍然视为矛盾。</p>
     */
    public boolean contradictsRequestId(String expectedRequestId) {
        String expected = trimToNull(expectedRequestId);
        if (expected == null) {
            return true;
        }
        return contradicts(expected, trimToNull(bizId))
                || contradicts(expected, trimToNull(mesRequestId))
                || contradicts(expected, trimToNull(data == null ? null : data.getBizId()));
    }

    private boolean contradicts(String expected, String declared) {
        return declared != null && !expected.equals(declared);
    }

    /**
     * 响应是否带料号：根字段 {@code materialNo} 或任一行的 {@code data.items[].productCode}。
     *
     * <p>料号是本流程唯一要拿的成果物，因此这里按「任意一行带到就算带到」判断，
     * 供「取到料号即写回」在外层判定处放行，逐行成败仍由调用方按行判断。</p>
     */
    public boolean hasMaterialNo() {
        if (trimToNull(materialNo) != null) {
            return true;
        }
        if (data == null || data.getItems() == null) {
            return false;
        }
        for (Item item : data.getItems()) {
            if (item != null && trimToNull(item.getProductCode()) != null) {
                return true;
            }
        }
        return false;
    }

    public String getStatus() {
        return firstNonBlank(status, firstItem() == null ? null : firstItem().getStatus());
    }

    public String getMaterialNo() {
        return firstNonBlank(materialNo, firstItem() == null ? null : firstItem().getProductCode());
    }

    public String getExternalTraceId() {
        Item item = firstItem();
        if (item == null) {
            return externalTraceId;
        }
        return firstNonBlank(externalTraceId, item.getTaskNo(), item.getTaskId() == null ? null : String.valueOf(item.getTaskId()));
    }

    private Item firstItem() {
        if (data == null || data.getItems() == null || data.getItems().isEmpty()) {
            return null;
        }
        return data.getItems().get(0);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private Boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).compareTo(BigDecimal.ZERO) == 0;
        }
        if (value instanceof CharSequence text) {
            try {
                return new BigDecimal(text.toString().trim()).compareTo(BigDecimal.ZERO) == 0;
            } catch (NumberFormatException ignored) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String bizId;
        /**
         * The data-center/MES bridge has returned the single row as an object in some
         * deployments, even though the batch contract defines an array. Normalize that
         * equivalent one-row response without weakening row identity checks downstream.
         */
        @JsonDeserialize(using = LenientItemsDeserializer.class)
        private List<Item> items;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** 用 String 接收：MES 可能返回数字也可能返回任务号字符串，Long 会直接抛 InvalidFormatException。 */
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String taskId;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String taskNo;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String drawingNo;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String drawingVersion;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String status;
        @JsonAlias("msg")
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String message;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String productId;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String productCode;
        @JsonDeserialize(using = LenientStringDeserializer.class)
        private String productName;
    }

    /** Accept arrays, a single row object, and empty or malformed row containers. */
    public static class LenientItemsDeserializer extends JsonDeserializer<List<Item>> {
        @Override
        public List<Item> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken token = parser.currentToken();
            List<Item> items = new ArrayList<>();
            if (token == JsonToken.START_OBJECT) {
                items.add(parser.readValueAs(Item.class));
                return items;
            }
            if (token != JsonToken.START_ARRAY) {
                parser.skipChildren();
                return items;
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    items.add(parser.readValueAs(Item.class));
                } else {
                    parser.skipChildren();
                }
            }
            return items;
        }
    }

    /** Preserve scalar identifiers while treating object/array values as absent. */
    public static class LenientStringDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING || token == JsonToken.VALUE_NUMBER_INT
                    || token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_TRUE
                    || token == JsonToken.VALUE_FALSE) {
                return parser.getValueAsString();
            }
            parser.skipChildren();
            return null;
        }

        @Override
        public String getNullValue(DeserializationContext context) {
            return null;
        }
    }

    /**
     * MES 失败时 data 常见为 []、""、0 或缺失，直接按对象解析会抛
     * MismatchedInputException，把真正的 msg（如「图号不存在」）埋掉。
     * 非对象一律当 null，让上层能正常读到 message。
     */
    public static class LenientDataDeserializer extends JsonDeserializer<Data> {
        @Override
        public Data deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                return parser.readValueAs(Data.class);
            }
            parser.skipChildren();
            return null;
        }
    }

    /** Accept common boolean encodings used by data-center and MES JSON serializers. */
    public static class LenientBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_TRUE) {
                return Boolean.TRUE;
            }
            if (token == JsonToken.VALUE_FALSE) {
                return Boolean.FALSE;
            }
            if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
                if (parser.getDecimalValue().compareTo(java.math.BigDecimal.ONE) == 0) {
                    return Boolean.TRUE;
                }
                if (parser.getDecimalValue().compareTo(java.math.BigDecimal.ZERO) == 0) {
                    return Boolean.FALSE;
                }
                parser.skipChildren();
                return null;
            }
            if (token == JsonToken.VALUE_STRING) {
                String value = parser.getText().trim();
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
                    return Boolean.FALSE;
                }
                return null;
            }
            parser.skipChildren();
            return null;
        }

        @Override
        public Boolean getNullValue(DeserializationContext context) {
            return null;
        }
    }
}
