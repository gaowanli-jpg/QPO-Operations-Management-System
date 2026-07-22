package com.machining.quote.dto;

import lombok.Data;

@Data
public class WmsAllocationReq {
    private Integer ticketId;
    private Integer rfqId;
    private String rfqNo;
    private String customerName;
    private String materialNo;
    private String partNo;
    private String version;
    private String internalProjectNo;
    private Integer demandQty;
}
