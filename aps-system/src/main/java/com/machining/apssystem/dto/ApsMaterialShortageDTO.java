package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApsMaterialShortageDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

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

    @JsonProperty("shortage_weight_kg")
    private BigDecimal shortageWeightKg;

    @JsonProperty("source_task")
    private String sourceTask;

    private String status;
    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
