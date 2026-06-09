package com.machining.apssystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApsSyncResultDTO {
    private boolean success = true;
    private Long syncRunId;
    private Integer startAfterTicketId;
    private Integer nextAfterTicketId;
    private int receivedCount;
    private int importedCount;
    private int skippedCount;
    private int productionRequirementCount;
    private int materialShortageCount;
    private String message = "";
    private List<WorkItemSummaryDTO> importedItems = new ArrayList<>();
}
