package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class QuoteLeadTimeReplacementConfirmDTO {
    private boolean success = true;
    private String message;
    private QuoteLeadTimeReplacementSummaryDTO summary = new QuoteLeadTimeReplacementSummaryDTO();
    private List<Map<String, Object>> affectedRows = new ArrayList<>();
}
