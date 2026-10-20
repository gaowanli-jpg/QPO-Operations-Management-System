package com.machining.quote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartSearchItemRowDTO {
    private Long id;
    private String sourceType;
    private String partNo;
    private String version;
    private BigDecimal untaxedUnitPrice;
    private BigDecimal taxIncludedUnitPrice;
    private String drawingUrl;
    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private String itemStatus;
}
