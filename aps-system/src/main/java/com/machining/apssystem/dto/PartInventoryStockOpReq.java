package com.machining.apssystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartInventoryStockOpReq {
    private Long inventoryId;
    private String customerName;
    private String partNo;
    private String version;
    private String stockType;

    @NotNull(message = "qty is required.")
    @Min(value = 1, message = "qty must be greater than 0.")
    private Integer qty;

    private String pendingRefNo;
}
