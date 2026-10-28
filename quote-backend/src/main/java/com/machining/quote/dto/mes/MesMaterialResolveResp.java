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
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesMaterialResolveResp {
    private Object code;
    private Boolean success;
    @JsonAlias("bizId")
    private String mesRequestId;
    private String status;
    private Boolean callbackRequired;
    private String materialNo;
    @JsonAlias("msg")
    private String message;
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
        return firstNonBlank(mesRequestId, data == null ? null : data.getBizId());
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

    private boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(value).trim());
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

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String bizId;
        private List<Item> items;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** 用 String 接收：MES 可能返回数字也可能返回任务号字符串，Long 会直接抛 InvalidFormatException。 */
        private String taskId;
        private String taskNo;
        private String drawingNo;
        private String drawingVersion;
        private String status;
        private String productId;
        private String productCode;
        private String productName;
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
}
