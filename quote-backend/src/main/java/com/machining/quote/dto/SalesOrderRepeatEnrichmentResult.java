package com.machining.quote.dto;

import lombok.Data;

@Data
public class SalesOrderRepeatEnrichmentResult {
    private int matchedItemCount;
    private int unmatchedItemCount;
    private String warningMessage;
}
