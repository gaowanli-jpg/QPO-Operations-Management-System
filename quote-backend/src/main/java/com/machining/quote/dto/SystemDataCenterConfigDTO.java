package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemDataCenterConfigDTO {
    private Long id;
    private String name;
    private Boolean active;
    private Boolean enabled;
    private String baseUrl;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private Boolean clientSecretConfigured;
    private String grantType;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer tokenExpireSkewSeconds;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
