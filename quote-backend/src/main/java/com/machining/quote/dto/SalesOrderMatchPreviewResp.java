package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderMatchPreviewResp {
    private int totalItemCount;
    private int matchedItemCount;
    private int unmatchedItemCount;
    private String warningMessage;
    private List<SalesOrderMatchPreviewRow> rows = new ArrayList<>();
}
