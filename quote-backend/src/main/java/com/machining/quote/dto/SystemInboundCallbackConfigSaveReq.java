package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemInboundCallbackConfigSaveReq {
    private Boolean enabled;
    private Boolean tokenEnabled;
    private String publicBaseUrl;
    private String remark;
}
