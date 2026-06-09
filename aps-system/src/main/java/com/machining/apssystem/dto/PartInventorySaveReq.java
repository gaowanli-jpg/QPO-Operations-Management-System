package com.machining.apssystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PartInventorySaveReq {
    @NotBlank(message = "customerName is required.")
    private String customerName;

    @NotBlank(message = "partNo is required.")
    private String partNo;

    private String version;

    @Min(value = 0, message = "inStockQty cannot be negative.")
    private Integer inStockQty;

    @Min(value = 0, message = "inProductionQty cannot be negative.")
    private Integer inProductionQty;

    private String remark;
}
