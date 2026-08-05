package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesMaterialCallbackReq {
    private String mesRequestId;
    private String externalTraceId;
    private String status;
    private String materialNo;
    private String message;
}
