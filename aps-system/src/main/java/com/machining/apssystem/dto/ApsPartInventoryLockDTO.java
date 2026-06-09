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

    private String status;

    @JsonProperty("locked_at")
    private LocalDateTime lockedAt;

    @JsonProperty("released_at")
    private LocalDateTime releasedAt;

    @JsonProperty("rfq_no")
    private String rfqNo;

    @JsonProperty("internal_project_no")
    private String internalProjectNo;

    @JsonProperty("delivery_date")
    private LocalDate deliveryDate;
}
