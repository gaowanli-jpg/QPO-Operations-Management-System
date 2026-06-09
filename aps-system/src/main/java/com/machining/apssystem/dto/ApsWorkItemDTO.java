package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApsWorkItemDTO {
    private Long id;

    @JsonProperty("erp_ticket_id")
    private Integer erpTicketId;

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
    private String description;
    private Integer qty;
    private String unit;

    @JsonProperty("delivery_date")
    private LocalDate deliveryDate;

    @JsonProperty("drawing_url")
    private String drawingUrl;

    @JsonProperty("drawing_view_url")
    private String drawingViewUrl;

    @JsonProperty("material_name")
    private String materialName;

    @JsonProperty("material_type")
    private String materialType;

    @JsonProperty("material_demand_weight_kg")
    private BigDecimal materialDemandWeightKg;

    @JsonProperty("total_work_hours")
    private BigDecimal totalWorkHours;

    @JsonProperty("part_stock_status")
    private String partStockStatus;

    @JsonProperty("material_stock_status")
    private String materialStockStatus;

    @JsonProperty("assignment_status")
    private String assignmentStatus;

    private String status;

    @JsonProperty("assigned_department_id")
    private Long assignedDepartmentId;

    @JsonProperty("assigned_department_name")
    private String assignedDepartmentName;

    @JsonProperty("assigned_at")
    private LocalDateTime assignedAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
