package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ApsPartInventoryLockDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

    @JsonProperty("part_inventory_id")
    private Long partInventoryId;

    @JsonProperty("part_inventory_lock_id")
    private Long partInventoryLockId;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("part_no")
    private String partNo;

    private String version;

    @JsonProperty("demand_qty")
    private Integer demandQty;

    @JsonProperty("locked_in_stock_qty")
    private Integer lockedInStockQty;

    @JsonProperty("locked_in_production_qty")
    private Integer lockedInProductionQty;

    @JsonProperty("production_required_qty")
    private Integer productionRequiredQty;

    @JsonProperty("lock_type")
    private String lockType;

    @JsonProperty("lock_type_text")
    private String lockTypeText;

    @JsonProperty("pending_stock_id")
    private Long pendingStockId;

    @JsonProperty("pending_ref_no")
    private String pendingRefNo;

    @JsonProperty("locked_qty")
    private Integer lockedQty;

    private String status;

    @JsonProperty("locked_at")
    private LocalDateTime lockedAt;

    @JsonProperty("released_at")
    private LocalDateTime releasedAt;

    @JsonProperty("internal_project_no")
    private String internalProjectNo;

    @JsonProperty("customer_order_no")
    private String customerOrderNo;

    @JsonProperty("customer_project_no")
    private String customerProjectNo;

    @JsonProperty("delivery_date")
    private LocalDate deliveryDate;
}
