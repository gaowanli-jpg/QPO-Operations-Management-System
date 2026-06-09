package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApsMachineDTO {
    private Long id;

    @JsonProperty("department_id")
    private Long departmentId;

    @JsonProperty("department_name")
    private String departmentName;

    @JsonProperty("machine_code")
    private String machineCode;

    @JsonProperty("machine_name")
    private String machineName;

    @JsonProperty("machine_status")
    private String machineStatus;

    private String remark;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
