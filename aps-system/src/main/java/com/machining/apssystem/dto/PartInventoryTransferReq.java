package com.machining.apssystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PartInventoryTransferReq {
    @NotNull(message = "inventoryId is required.")
    private Long inventoryId;

    @NotBlank(message = "pendingRefNo is required.")
    private String pendingRefNo;

    @NotNull(message = "qty is required.")
    @Min(value = 1, message = "qty must be greater than 0.")
    private Integer qty;
}
