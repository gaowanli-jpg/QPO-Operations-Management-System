package com.machining.quote.dto;

import lombok.Data;

@Data
public class MesMaterialResolveResp {
    private Boolean success;
    private String materialNo;
    private String message;
    private String externalTraceId;
}
