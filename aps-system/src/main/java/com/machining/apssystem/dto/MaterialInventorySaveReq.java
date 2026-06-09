package com.machining.apssystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialInventorySaveReq {
    @NotBlank(message = "materialName is required.")
    private String materialName;

    private String materialType;

    @DecimalMin(value = "0", message = "stockWeightKg cannot be negative.")
    private BigDecimal stockWeightKg;

    @DecimalMin(value = "0", message = "safetyStockWeightKg cannot be negative.")
    private BigDecimal safetyStockWeightKg;

    private String unit;

    private String remark;
}
