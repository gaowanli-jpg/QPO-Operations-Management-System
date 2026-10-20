package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MesProductionOrderCreateResp {
    private Object code;
    @JsonAlias("msg")
    private String message;
    private DataEnvelope data;

    public Boolean getSuccess() {
        return code != null && isSuccessCode(code);
    }

    public String getBizId() {
        return data == null ? null : data.getBizId();
    }

    private boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(value).trim());
    }

    @Data
    public static class DataEnvelope {
        private String bizId;
        private List<Item> items;
    }

    @Data
    public static class Item {
        private Object id;
        private String productOrder;
        private String orderType;
        private String productCode;
        private BigDecimal planCount;
        private String saleOrderNo;
        private String saleOrderItemNo;
        private Object status;
    }
}
