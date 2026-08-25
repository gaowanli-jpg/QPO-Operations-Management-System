package com.machining.quote.dto;

import lombok.Data;

@Data
public class OrderItemPageReq {
    public static final String SCOPE_SUPPLEMENTAL_DRAWING = "SUPPLEMENTAL_DRAWING";

    private Integer rfqId;
    private Integer page;
    private Integer pageSize;
    private String scope;
    private String keyword;
    private String status;
    private String partNo;
    private String version;
    private Integer excludeItemId;
}
