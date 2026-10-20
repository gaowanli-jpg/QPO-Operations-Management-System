package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemIntegrationConfigSaveReq {
    private Long id;
    private String systemCode;
    private String name;
    private Boolean enabled;
    private String authType;
    private String baseUrl;
    private String materialPath;
    private String allocationPath;
    private String authToken;
    private Integer timeoutMs;
    private String authLoginPath;
    private String salesOrderCreatePath;
    private String userCode;
    private String entCode;
    private String orgCode;
    private String clientId;
    private String clientSecret;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private String remark;
}
