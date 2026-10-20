package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemInboundCallbackTokenResp {
    private String callbackCode;
    private String token;
    private String tokenMask;
    private String message;
}
