package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MesMaterialResolveReq {
    @JsonProperty("bizId")
    private String bizId;
    private String customerCode;
    private String customerName;
    private String internalProjectNo;
    private List<Item> items;

    @JsonIgnore
    public String getMesRequestId() {
        return bizId;
    }

    @JsonIgnore
    public void setMesRequestId(String mesRequestId) {
        this.bizId = mesRequestId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String drawingNo;
        private String drawingVersion;
        @Deprecated
        @JsonIgnore
        private List<String> drawingUrls;
        private String remark;
        private String customerRequirements;
    }
}
