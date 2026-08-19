package com.machining.quote.dto;

import lombok.Data;

@Data
public class QuoteLeadTimeReplacementSummaryDTO {
    private int importedCount;
    private int matchedCount;
    private int changedCount;
    private int unchangedCount;
    private int emptyValueCount;
    private int unmatchedCount;
    private int errorCount;
    private int updatedItemCount;
}
