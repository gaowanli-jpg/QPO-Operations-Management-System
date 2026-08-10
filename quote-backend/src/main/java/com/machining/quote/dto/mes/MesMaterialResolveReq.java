package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MesMaterialResolveReq {
    @JsonProperty("bizId")
    @JsonAlias("mesRequestId")
    private String mesRequestId;
    private String customerCode;
    private String customerName;
    private String internalProjectNo;
    private java.util.List<Item> items;

    @Data
    public static class Item {
        private String drawingNo;
        private String drawingVersion;
        private java.util.List<String> drawingUrls;
        private String remark;
    }
}
