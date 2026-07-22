package com.machining.quote.dto;

import lombok.Data;

@Data
public class MesMaterialResolveReq {
    private Integer ticketId;
    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private String partNo;
    private String version;
    private String drawingUrl;
    private String internalProjectNo;
}
