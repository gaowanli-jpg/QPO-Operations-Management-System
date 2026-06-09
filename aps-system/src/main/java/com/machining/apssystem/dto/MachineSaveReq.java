package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MachineSaveReq {
    @NotNull(message = "departmentId is required.")
    private Long departmentId;

    private String machineCode;

    @NotBlank(message = "machineName is required.")
    private String machineName;

    private String machineStatus;

    private String remark;
}
