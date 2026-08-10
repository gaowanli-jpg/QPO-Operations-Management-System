package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderAutoSnapshotCompletionResult {
    private boolean success;
    private String message;
    private int totalItemCount;
    private int matchedItemCount;
    private int unmatchedItemCount;
    private int skippedItemCount;
    private int updatedItemCount;
    private int materialNoCopiedCount;
    private int materialNoSkippedCount;
    private int materialNoConflictCount;
    private String warningMessage;
    private List<SalesOrderAutoSnapshotCompletionRow> rows = new ArrayList<>();
}
