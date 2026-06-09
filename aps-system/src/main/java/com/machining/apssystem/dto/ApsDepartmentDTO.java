package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.machining.apssystem.entity.ApsDepartment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ApsDepartmentDTO {
    private Long id;

    @JsonProperty("department_name")
    private String departmentName;

    @JsonProperty("department_type")
    private String departmentType;

    @JsonProperty("department_status")
    private String departmentStatus;

    @JsonProperty("monthly_available_hours")
    private BigDecimal monthlyAvailableHours;

    @JsonProperty("occupied_hours")
    private BigDecimal occupiedHours;

    @JsonProperty("remaining_hours")
    private BigDecimal remainingHours;

    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ApsDepartmentDTO from(ApsDepartment department) {
        ApsDepartmentDTO dto = new ApsDepartmentDTO();
        dto.setId(department.getId());
        dto.setDepartmentName(department.getDepartmentName());
        dto.setDepartmentType(department.getDepartmentType());
        dto.setDepartmentStatus(department.getDepartmentStatus());
        dto.setMonthlyAvailableHours(department.getMonthlyAvailableHours());
        dto.setOccupiedHours(department.getOccupiedHours());
        dto.setRemainingHours(department.getRemainingHours());
        dto.setRemark(department.getRemark());
        dto.setCreatedAt(department.getCreatedAt());
        dto.setUpdatedAt(department.getUpdatedAt());
        return dto;
    }
}
