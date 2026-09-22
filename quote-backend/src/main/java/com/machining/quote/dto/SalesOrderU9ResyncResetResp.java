package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderU9ResyncResetResp {
    private Long salesOrderId;
    private String salesOrderNo;
    private String fromStatus;
    private String toStatus;
    private String previousU9OrderId;
    private String previousU9OrderCode;
    private String message;
}
