package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SalesOrderSyncOrderDTO {
    private String saleOrderNo;
    private LocalDateTime createdAt;
    private LocalDateTime changedAt;
    private String status;
    private String orderType;
    private String customerCode;
    private String supplierCode;
    private List<SalesOrderSyncItemDTO> items = new ArrayList<>();
}
