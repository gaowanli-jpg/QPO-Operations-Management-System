package com.machining.apssystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepartmentCapacityResetReq {
    @NotNull(message = "monthlyAvailableHours is required.")
    @DecimalMin(value = "0", message = "monthlyAvailableHours cannot be negative.")
    private BigDecimal monthlyAvailableHours;
}
