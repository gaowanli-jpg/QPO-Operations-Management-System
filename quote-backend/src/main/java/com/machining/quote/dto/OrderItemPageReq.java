package com.machining.quote.dto;

import lombok.Data;

@Data
public class OrderItemPageReq {
    private Integer rfqId;
    private Integer page;
    private Integer pageSize;
    private String keyword;
    private String status;
    private String partNo;
    private String version;
    private Integer excludeItemId;
}
