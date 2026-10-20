package com.machining.quote.dto.mes;

import lombok.Data;

import java.util.Map;

@Data
public class MesMaterialCallbackResp {
    private Integer code;
    private String msg;
    private Map<String, Object> data;
    private Boolean success;
    private String mesRequestId;
    private String materialNo;
}
