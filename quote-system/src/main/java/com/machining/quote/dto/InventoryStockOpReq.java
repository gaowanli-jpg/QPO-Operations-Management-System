package com.machining.quote.dto;

import lombok.Data;

@Data
public class InventoryStockOpReq {
    private Integer inventoryId;
    private String customerName;
    private String partNo;
    private String version;
    private String stockType;
    private Integer qty;
    private String pendingRefNo;
}
