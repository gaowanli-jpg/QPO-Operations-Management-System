package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemStorageConfigDTO {
    private Long id;
    private String name;
    private String storageType;
    private Boolean active;
    private Boolean enabled;
    private String rootDir;
    private String endpoint;
    private String bucket;
    private String accessKey;
    private String accessSecret;
    private String region;
    private Boolean pathStyle;
    private Boolean publicReadEnabled;
    private String publicBaseUrl;
    private String remark;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
