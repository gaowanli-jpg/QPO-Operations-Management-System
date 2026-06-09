package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApsDepartmentAssignmentDTO {
    private Long id;

    @JsonProperty("work_item_id")
    private Long workItemId;

    @JsonProperty("department_id")
    private Long departmentId;

    @JsonProperty("department_name")
    private String departmentName;

    @JsonProperty("required_hours")
    private BigDecimal requiredHours;

    private String status;

    @JsonProperty("assigned_by")
    private String assignedBy;

    @JsonProperty("assigned_at")
    private LocalDateTime assignedAt;

    @JsonProperty("released_at")
    private LocalDateTime releasedAt;

    private String remark;

    @JsonProperty("capacity_counted")
    private Boolean capacityCounted;
}
