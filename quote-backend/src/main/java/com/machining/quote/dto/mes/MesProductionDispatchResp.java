package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class MesProductionDispatchResp {
    private Object code;
    private Boolean success;
    private String requestId;
    private String externalId;
    @JsonAlias("msg")
    private String message;
    private DataEnvelope data;

    public Boolean getSuccess() {
        if (code != null) {
            return isSuccessCode(code);
        }
        return success;
    }

    public String getRequestId() {
        return firstNonBlank(requestId, data == null ? null : data.getBizId());
    }

    public String getExternalId() {
        Item item = firstItem();
        if (item == null) {
            return externalId;
        }
        return firstNonBlank(externalId, item.getPlanNo(), item.getId() == null ? null : String.valueOf(item.getId()));
    }

    private Item firstItem() {
        if (data == null || data.getItems() == null || data.getItems().isEmpty()) {
            return null;
        }
        return data.getItems().get(0);
    }

    private boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(value).trim());
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

    @Data
    public static class DataEnvelope {
        private String bizId;
        private List<Item> items;
    }

    @Data
    public static class Item {
        private Long id;
        private String planNo;
        private String saleOrderNo;
        private String saleOrderItemNo;
        private String productCode;
        private Object status;
    }
}
