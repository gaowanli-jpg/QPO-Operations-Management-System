package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

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
}
