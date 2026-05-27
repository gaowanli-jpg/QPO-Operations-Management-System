package com.machining.quote.dto;

import lombok.Data;

@Data
public class InventorySaveReq {
    private String customerName;
    private String partNo;
    private String version;
    private Integer inStockQty;
    private Integer pendingQty;
    private String pendingRefNo;
}
