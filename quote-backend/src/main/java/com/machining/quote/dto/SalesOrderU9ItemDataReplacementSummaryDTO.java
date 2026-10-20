package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderU9ItemDataReplacementSummaryDTO {
    private int importedCount;
    private int matchedCount;
    private int changedCount;
    private int unchangedCount;
    private int unmatchedCount;
    private int skippedCount;
    private int errorCount;
}
