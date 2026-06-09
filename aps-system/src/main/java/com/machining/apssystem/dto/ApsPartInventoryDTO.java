package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.machining.apssystem.entity.ApsPartInventory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApsPartInventoryDTO {
    private Long id;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("part_no")
    private String partNo;

    private String version;

    @JsonProperty("in_stock_qty")
    private Integer inStockQty;

    @JsonProperty("in_production_qty")
    private Integer inProductionQty;

    @JsonProperty("locked_in_stock_qty")
    private Integer lockedInStockQty;

    @JsonProperty("locked_in_production_qty")
    private Integer lockedInProductionQty;

    @JsonProperty("available_in_stock_qty")
    private Integer availableInStockQty;

    @JsonProperty("available_in_production_qty")
    private Integer availableInProductionQty;

    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private List<ApsPartInventoryPendingStockDTO> pendingRows = new ArrayList<>();

    public static ApsPartInventoryDTO from(ApsPartInventory inventory) {
        if (inventory == null) {
            return null;
        }
        ApsPartInventoryDTO dto = new ApsPartInventoryDTO();
        dto.setId(inventory.getId());
        dto.setCustomerName(inventory.getCustomerName());
        dto.setPartNo(inventory.getPartNo());
        dto.setVersion(inventory.getVersion());
        dto.setInStockQty(inventory.getInStockQty());
        dto.setInProductionQty(inventory.getInProductionQty());
        dto.setLockedInStockQty(inventory.getLockedInStockQty());
        dto.setLockedInProductionQty(inventory.getLockedInProductionQty());
        dto.setAvailableInStockQty(inventory.getAvailableInStockQty());
        dto.setAvailableInProductionQty(inventory.getAvailableInProductionQty());
        dto.setRemark(inventory.getRemark());
        dto.setCreatedAt(inventory.getCreatedAt());
        dto.setUpdatedAt(inventory.getUpdatedAt());
        return dto;
    }
}
