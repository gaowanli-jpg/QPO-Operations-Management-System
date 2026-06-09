package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ApsPartProductionRequirementDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("part_no")
    private String partNo;

    private String version;

    @JsonProperty("required_qty")
    private Integer requiredQty;

    private String status;
    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("internal_project_no")
    private String internalProjectNo;

    @JsonProperty("customer_order_no")
    private String customerOrderNo;

    @JsonProperty("customer_project_no")
    private String customerProjectNo;

    @JsonProperty("delivery_date")
    private LocalDate deliveryDate;
}
