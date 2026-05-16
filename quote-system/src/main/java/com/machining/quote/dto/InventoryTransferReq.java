package com.machining.quote.dto;

import lombok.Data;

@Data
public class InventoryTransferReq {
    private Integer inventoryId;
    private String pendingRefNo;
    private Integer qty;
}
