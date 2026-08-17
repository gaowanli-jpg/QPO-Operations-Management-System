package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderSyncOrderDTO {
    private String saleOrderNo;
    private LocalDateTime createdAt;
    private LocalDateTime changedAt;
    private String status;
    private String orderType;
    private String internalProjectNo;
    private List<SalesOrderSyncItemDTO> items = new ArrayList<>();
}
