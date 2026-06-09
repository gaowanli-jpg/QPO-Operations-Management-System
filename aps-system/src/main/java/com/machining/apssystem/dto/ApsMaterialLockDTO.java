package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApsMaterialLockDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

    @JsonProperty("material_inventory_id")
    private Long materialInventoryId;

    @JsonProperty("internal_project_no")
    private String internalProjectNo;

    @JsonProperty("customer_order_no")
    private String customerOrderNo;

    @JsonProperty("customer_project_no")
    private String customerProjectNo;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("part_no")
    private String partNo;

    private String version;

    @JsonProperty("material_name")
    private String materialName;

    @JsonProperty("material_type")
    private String materialType;

    @JsonProperty("demand_weight_kg")
    private BigDecimal demandWeightKg;

    @JsonProperty("locked_weight_kg")
    private BigDecimal lockedWeightKg;

    @JsonProperty("shortage_weight_kg")
    private BigDecimal shortageWeightKg;

    private String status;

    @JsonProperty("warehouse_status")
    private String warehouseStatus;

    @JsonProperty("locked_at")
    private LocalDateTime lockedAt;

    @JsonProperty("confirmed_at")
    private LocalDateTime confirmedAt;

    @JsonProperty("released_at")
    private LocalDateTime releasedAt;
}
