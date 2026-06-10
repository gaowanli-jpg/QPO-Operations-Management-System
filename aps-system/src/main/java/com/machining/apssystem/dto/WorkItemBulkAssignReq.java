package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WorkItemBulkAssignReq {
    @NotEmpty(message = "ids must not be empty.")
    private List<Long> ids;

    @NotNull(message = "departmentId is required.")
    private Long departmentId;

    private String assignedBy;

    private String remark;
}
