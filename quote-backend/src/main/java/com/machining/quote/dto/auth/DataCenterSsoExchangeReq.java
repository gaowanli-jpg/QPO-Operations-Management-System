package com.machining.quote.dto.auth;

import lombok.Data;

@Data
public class DataCenterSsoExchangeReq {
    private String appCode;
    private String username;
    private String displayName;
    private Long timestamp;
    private String nonce;
    private String redirect;
    private String signature;
}
