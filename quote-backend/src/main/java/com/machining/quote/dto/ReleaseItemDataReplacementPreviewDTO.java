package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReleaseItemDataReplacementPreviewDTO {
    private boolean success;
    private boolean canConfirm;
    private String previewFingerprint;
    private List<String> recognizedFields = new ArrayList<>();
    private ReleaseItemDataReplacementSummaryDTO summary = new ReleaseItemDataReplacementSummaryDTO();
    private List<ReleaseItemDataReplacementRowDTO> rows = new ArrayList<>();
}
