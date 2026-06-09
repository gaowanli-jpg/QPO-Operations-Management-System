package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepartmentAssignmentReq {
    @NotNull(message = "departmentId is required.")
    private Long departmentId;

    private String assignedBy;

    private String remark;
}
