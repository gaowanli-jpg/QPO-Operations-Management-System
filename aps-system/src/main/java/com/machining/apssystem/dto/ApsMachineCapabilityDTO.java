package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApsMachineCapabilityDTO {
    private Long id;

    @JsonProperty("machine_id")
    private Long machineId;

    @JsonProperty("machine_code")
    private String machineCode;

    @JsonProperty("machine_name")
    private String machineName;

    @JsonProperty("department_name")
    private String departmentName;

    @JsonProperty("process_id")
    private Long processId;

    @JsonProperty("process_type")
    private String processType;

    @JsonProperty("process_name")
    private String processName;

    @JsonProperty("calc_type")
    private String calcType;

    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
