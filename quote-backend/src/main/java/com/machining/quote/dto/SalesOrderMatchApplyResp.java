package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderMatchApplyResp {
    private boolean success;
    private String message;
    private int totalItemCount;
    private int matchedItemCount;
    private int unmatchedItemCount;
    private int skippedItemCount;
    private int updatedItemCount;
    private String warningMessage;
    private List<SalesOrderMatchApplyRow> rows = new ArrayList<>();
}
