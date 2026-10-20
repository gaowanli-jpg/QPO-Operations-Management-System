package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class MesMaterialCallbackReq {
    @JsonAlias("bizId")
    private String mesRequestId;
    private String externalTraceId;
    private String status;
    @JsonAlias("productCode")
    private String materialNo;
    @JsonAlias("msg")
    private String message;
    private List<Item> items;

    @Data
    public static class Item {
        private String taskNo;
        private String drawingNo;
        private String drawingVersion;
        private String status;
        private String productCode;
        private String productName;
        @JsonAlias("msg")
        private String message;
    }
}
