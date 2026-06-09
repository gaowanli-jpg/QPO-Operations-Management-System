package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepartmentCapacityAdjustReq {
    @NotNull(message = "deltaHours is required.")
    private BigDecimal deltaHours;
}
