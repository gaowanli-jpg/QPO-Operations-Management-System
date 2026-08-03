package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemDataCenterConfigSaveReq {
    private Long id;
    private String name;
    private Boolean enabled;
    private String baseUrl;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String grantType;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer tokenExpireSkewSeconds;
    private String remark;
}
