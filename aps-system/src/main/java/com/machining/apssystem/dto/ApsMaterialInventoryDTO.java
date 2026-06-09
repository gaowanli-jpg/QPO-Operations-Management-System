package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.machining.apssystem.entity.ApsMaterialInventory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApsMaterialInventoryDTO {
    private Long id;

    @JsonProperty("material_name")
    private String materialName;

    @JsonProperty("material_type")
    private String materialType;

    @JsonProperty("stock_weight_kg")
    private BigDecimal stockWeightKg;

    @JsonProperty("locked_weight_kg")
    private BigDecimal lockedWeightKg;

    @JsonProperty("available_weight_kg")
    private BigDecimal availableWeightKg;

    @JsonProperty("safety_stock_weight_kg")
    private BigDecimal safetyStockWeightKg;

    private String unit;
    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ApsMaterialInventoryDTO from(ApsMaterialInventory inventory) {
        if (inventory == null) {
            return null;
        }
        ApsMaterialInventoryDTO dto = new ApsMaterialInventoryDTO();
        dto.setId(inventory.getId());
        dto.setMaterialName(inventory.getMaterialName());
        dto.setMaterialType(inventory.getMaterialType());
        dto.setStockWeightKg(inventory.getStockWeightKg());
        dto.setLockedWeightKg(inventory.getLockedWeightKg());
        dto.setAvailableWeightKg(inventory.getAvailableWeightKg());
        dto.setSafetyStockWeightKg(inventory.getSafetyStockWeightKg());
        dto.setUnit(inventory.getUnit());
        dto.setRemark(inventory.getRemark());
        dto.setCreatedAt(inventory.getCreatedAt());
        dto.setUpdatedAt(inventory.getUpdatedAt());
        return dto;
    }
}
