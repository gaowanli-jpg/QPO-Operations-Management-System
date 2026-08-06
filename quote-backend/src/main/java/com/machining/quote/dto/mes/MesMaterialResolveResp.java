package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class MesMaterialResolveResp {
    private Integer code;
    private Boolean success;
    @JsonAlias("bizId")
    private String mesRequestId;
    private String status;
    private Boolean callbackRequired;
    private String materialNo;
    @JsonAlias("msg")
    private String message;
    private String externalTraceId;
    private Data data;

    public Boolean getSuccess() {
        if (code != null) {
            return code == 0;
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
    public static class Data {
        private String bizId;
        private List<Item> items;
    }

    @lombok.Data
    public static class Item {
        private Long taskId;
        private String taskNo;
        private String drawingNo;
        private String drawingVersion;
        private String status;
        private Long productId;
        private String productCode;
        private String productName;
    }
}
