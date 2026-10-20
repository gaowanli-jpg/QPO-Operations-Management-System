package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderU9ItemDataReplacementPreviewDTO {
    private boolean success;
    private boolean canConfirm;
    private String previewFingerprint;
    private List<String> recognizedFields = new ArrayList<>();
    private SalesOrderU9ItemDataReplacementSummaryDTO summary = new SalesOrderU9ItemDataReplacementSummaryDTO();
    private List<SalesOrderU9ItemDataReplacementRowDTO> rows = new ArrayList<>();
}
