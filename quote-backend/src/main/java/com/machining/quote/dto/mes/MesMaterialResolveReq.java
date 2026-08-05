package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesMaterialResolveReq {
    private String mesRequestId;
    private String customerCode;
    private String customerName;
    private String partNo;
    private String version;
    private String drawingUrl;
    private String internalProjectNo;
}
