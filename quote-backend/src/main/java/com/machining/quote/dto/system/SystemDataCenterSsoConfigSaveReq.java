package com.machining.quote.dto.system;

import lombok.Data;

@Data
public class SystemDataCenterSsoConfigSaveReq {
    private Long id;
    private String configName;
    private Boolean enabled;
    private String appCode;
    private String sharedSecret;
    private Integer timestampSkewSeconds;
    private Integer nonceTtlMinutes;
    private String defaultRedirect;
    private String allowedRedirectPrefixes;
    private String remark;
}
