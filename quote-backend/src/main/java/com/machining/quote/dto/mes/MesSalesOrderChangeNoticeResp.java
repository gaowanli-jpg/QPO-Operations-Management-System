package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class MesSalesOrderChangeNoticeResp {
    private Object code;
    @JsonAlias("msg")
    private String message;
    private DataEnvelope data;

    public boolean successCode() {
        if (code instanceof Number number) return number.intValue() == 0;
        return "0".equals(String.valueOf(code).trim());
    }

    @Data
    public static class DataEnvelope {
        private String bizId;
        private List<ItemResult> items;
    }

    @Data
    public static class ItemResult {
        private String noticeId;
        private String status;
        private String targetExternalId;
        private String message;
    }
}
