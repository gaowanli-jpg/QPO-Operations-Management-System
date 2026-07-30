package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemStorageConfigSaveReq {
    private Long id;
    private String name;
    private String storageType;
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
    private Boolean enabled;
}
