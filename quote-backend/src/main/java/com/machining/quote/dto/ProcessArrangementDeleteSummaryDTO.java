package com.machining.quote.dto;

import lombok.Data;

@Data
public class ProcessArrangementDeleteSummaryDTO {
    private Long rootNodeId;
    private String rootNodeName;
    private Integer removedNodeCount;
    private Integer removedItemCount;
}
