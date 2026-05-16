package com.machining.quote.dto;

import lombok.Data;

@Data
public class InventoryModifyReq {
    private Integer inventoryId;
    private Integer inStockQty;
    private String pendingRefNo;
    private Integer pendingQty;
}
