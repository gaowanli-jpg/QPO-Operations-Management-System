package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemInboundCallbackConfigDTO {
    private Long id;
    private String callbackCode;
    private String sourceSystemCode;
    private String name;
    private Boolean enabled;
    private Boolean tokenEnabled;
    private Boolean tokenConfigured;
    private String tokenMask;
    private String publicBaseUrl;
    private String callbackPath;
    private String callbackUrl;
    private String configSource;
    private String lastValidateStatus;
    private String lastValidateMessage;
    private LocalDateTime lastValidateAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
