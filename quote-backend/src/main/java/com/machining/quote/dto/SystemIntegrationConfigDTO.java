package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SystemIntegrationConfigDTO {
    private Long id;
    private String systemCode;
    private String name;
    private Boolean active;
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
    private Map<String, Object> configJson;
    private String remark;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
