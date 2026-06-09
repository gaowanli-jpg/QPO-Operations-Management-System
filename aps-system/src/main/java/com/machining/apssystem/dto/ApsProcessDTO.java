package com.machining.apssystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.machining.apssystem.entity.ApsProcess;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApsProcessDTO {
    private Long id;

    @JsonProperty("process_name")
    private String processName;

    @JsonProperty("process_type")
    private String processType;

    @JsonProperty("calc_type")
    private String calcType;

    @JsonProperty("erp_source_id")
    private Integer erpSourceId;

    @JsonProperty("sort_no")
    private Integer sortNo;

    @JsonProperty("synced_at")
    private LocalDateTime syncedAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ApsProcessDTO from(ApsProcess process) {
        ApsProcessDTO dto = new ApsProcessDTO();
        dto.setId(process.getId());
        dto.setProcessName(process.getProcessName());
        dto.setProcessType(process.getProcessType());
        dto.setCalcType(process.getCalcType());
        dto.setErpSourceId(process.getErpSourceId());
        dto.setSortNo(process.getSortNo());
        dto.setSyncedAt(process.getSyncedAt());
        dto.setCreatedAt(process.getCreatedAt());
        dto.setUpdatedAt(process.getUpdatedAt());
        return dto;
    }
}
