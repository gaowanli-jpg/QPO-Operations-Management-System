package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemDataCenterEndpointConfigDTO {
    private Long id;
    private String endpointCode;
    private String displayName;
    private String targetSystemCode;
    private String method;
    private String path;
    private Boolean enabled;
    private Integer timeoutMs;
    private String requestAdapter;
    private String responseAdapter;
    private Object requestSchemaJson;
    private Object responseSchemaJson;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
