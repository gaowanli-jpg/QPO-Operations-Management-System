package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(value = "externalId", ignoreUnknown = true)
public class MesProductionDispatchResp {
    private Object code;
    private Boolean success;
    private String requestId;
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

    /** Validate both the legacy root requestId and the contract's data.bizId. */
    public boolean matchesRequestId(String expectedRequestId) {
        String expected = trimToNull(expectedRequestId);
        if (expected == null) {
            return false;
        }
        String rootId = trimToNull(requestId);
        String dataId = trimToNull(data == null ? null : data.getBizId());
        return (rootId != null || dataId != null)
                && (rootId == null || expected.equals(rootId))
                && (dataId == null || expected.equals(dataId));
    }

    public String getExternalId() {
        Item item = firstItem();
        return item == null || item.getId() == null ? null : trimToNull(String.valueOf(item.getId()));
    }

    private Item firstItem() {
        if (data == null || data.getItems() == null || data.getItems().isEmpty()) {
            return null;
        }
        return data.getItems().get(0);
    }

    private boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).compareTo(BigDecimal.ZERO) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (value instanceof CharSequence text) {
            try {
                return new BigDecimal(text.toString().trim()).compareTo(BigDecimal.ZERO) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataEnvelope {
        private String bizId;
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Item> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Object id;
        private String planNo;
        private String saleOrderNo;
        private String saleOrderItemNo;
        private String productCode;
        private Object status;
    }
}
