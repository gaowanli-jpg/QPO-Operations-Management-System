package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ReleaseItemDataReplacementConfirmDTO {
    private boolean success;
    private ReleaseItemDataReplacementSummaryDTO summary = new ReleaseItemDataReplacementSummaryDTO();
    private List<Map<String, Object>> affectedRows = new ArrayList<>();
    private List<ReleaseItemDataReplacementAcceptedQtyDTO> acceptedQtyUpdates = new ArrayList<>();
}
