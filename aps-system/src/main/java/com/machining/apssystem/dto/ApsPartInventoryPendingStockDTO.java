package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.machining.apssystem.entity.ApsPartInventoryPendingStock;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApsPartInventoryPendingStockDTO {
    private Long id;

    @JsonProperty("part_inventory_id")
    private Long partInventoryId;

    @JsonProperty("pending_ref_no")
    private String pendingRefNo;

    @JsonProperty("pending_qty")
    private Integer pendingQty;

    @JsonProperty("locked_qty")
    private Integer lockedQty;

    @JsonProperty("available_qty")
    private Integer availableQty;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ApsPartInventoryPendingStockDTO from(ApsPartInventoryPendingStock row) {
        if (row == null) {
            return null;
        }
        ApsPartInventoryPendingStockDTO dto = new ApsPartInventoryPendingStockDTO();
        dto.setId(row.getId());
        dto.setPartInventoryId(row.getPartInventoryId());
        dto.setPendingRefNo(row.getPendingRefNo());
        dto.setPendingQty(row.getPendingQty());
        dto.setLockedQty(row.getLockedQty());
        dto.setAvailableQty(row.getAvailableQty());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }
}
