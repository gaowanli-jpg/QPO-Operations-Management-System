package com.machining.apssystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApsPmcTaskDetailDTO {
    private ApsWorkItemDTO task;
    private List<ApsWorkItemProcessDTO> processRows;
    private ApsPartInventoryLockDTO partLock;
    private ApsMaterialLockDTO materialLock;
    private List<ApsMaterialShortageDTO> materialShortages;
    private List<ApsDepartmentAssignmentDTO> assignments;
}
