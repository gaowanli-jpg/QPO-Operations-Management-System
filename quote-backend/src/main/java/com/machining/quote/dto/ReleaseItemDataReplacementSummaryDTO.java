package com.machining.quote.dto;

import lombok.Data;

@Data
public class ReleaseItemDataReplacementSummaryDTO {
    private int importedCount;
    private int matchedCount;
    private int changedCount;
    private int quantityOnlyCount;
    private int unchangedCount;
    private int unmatchedCount;
    private int skippedCount;
    private int errorCount;
    private int updatedItemCount;
    private int quantityAppliedCount;
}
