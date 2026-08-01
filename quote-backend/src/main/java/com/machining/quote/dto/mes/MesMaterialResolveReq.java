package com.machining.quote.dto.mes;

import lombok.Data;

@Data
public class MesMaterialResolveReq {
    private String mesRequestId;
    private Integer ticketId;
    private Integer rfqId;
    private String sourceType;
    private Long sourceOrderId;
    private Long sourceItemId;
    private String orderNo;
    private String rfqNo;
    private String customerName;
    private String partNo;
    private String version;
    private String drawingUrl;
    private String internalProjectNo;
}
