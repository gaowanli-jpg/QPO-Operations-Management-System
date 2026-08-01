package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesMaterialResolveResp {
    private Boolean success;
    private String mesRequestId;
    private String status;
    private Boolean callbackRequired;
    private String materialNo;
    private String message;
    private String externalTraceId;
}
