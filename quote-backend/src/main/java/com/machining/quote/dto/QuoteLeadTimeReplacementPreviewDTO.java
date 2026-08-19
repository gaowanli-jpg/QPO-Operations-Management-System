package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuoteLeadTimeReplacementPreviewDTO {
    private boolean success = true;
    private boolean canConfirm;
    private String previewFingerprint;
    private List<String> recognizedFields = new ArrayList<>();
    private QuoteLeadTimeReplacementSummaryDTO summary = new QuoteLeadTimeReplacementSummaryDTO();
    private List<QuoteLeadTimeReplacementRowDTO> rows = new ArrayList<>();
}
