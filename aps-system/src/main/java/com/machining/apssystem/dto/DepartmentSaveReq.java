package com.machining.apssystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepartmentSaveReq {
    @NotBlank(message = "departmentName is required.")
    private String departmentName;

    private String departmentType;

    private String departmentStatus;

    @DecimalMin(value = "0", message = "monthlyAvailableHours cannot be negative.")
    private BigDecimal monthlyAvailableHours;

    private String remark;
}
