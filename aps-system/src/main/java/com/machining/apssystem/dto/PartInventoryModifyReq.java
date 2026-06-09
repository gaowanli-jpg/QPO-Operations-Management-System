package com.machining.apssystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartInventoryModifyReq {
    @NotNull(message = "inventoryId is required.")
    private Long inventoryId;

    @Min(value = 0, message = "inStockQty cannot be negative.")
    private Integer inStockQty;

    private String pendingRefNo;

    @Min(value = 0, message = "pendingQty cannot be negative.")
    private Integer pendingQty;
}
