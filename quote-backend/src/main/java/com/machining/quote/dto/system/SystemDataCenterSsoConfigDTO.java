package com.machining.quote.dto.system;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemDataCenterSsoConfigDTO {
    private Long id;
    private String configName;
    private Boolean active;
    private Boolean enabled;
    private String appCode;
    private String sharedSecret;
    private Boolean sharedSecretConfigured;
    private Integer timestampSkewSeconds;
    private Integer nonceTtlMinutes;
    private String defaultRedirect;
    private String allowedRedirectPrefixes;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
