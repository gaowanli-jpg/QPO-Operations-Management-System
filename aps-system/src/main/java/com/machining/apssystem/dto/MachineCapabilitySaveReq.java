package com.machining.apssystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MachineCapabilitySaveReq {
    @NotNull(message = "machineId is required.")
    private Long machineId;

    private Long processId;

    private String processName;

    private String calcType;

    private String remark;
}
