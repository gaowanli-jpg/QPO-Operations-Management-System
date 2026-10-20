package com.machining.quote.dto;

import lombok.Data;

@Data
public class SystemDataCenterEndpointConfigSaveReq {
    private String endpointCode;
    private String method;
    private String path;
    private Boolean enabled;
    private Integer timeoutMs;
    private Object requestSchemaJson;
    private Object responseSchemaJson;
    private String remark;
}
