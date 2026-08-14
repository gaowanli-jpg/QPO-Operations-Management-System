package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderSyncPageData {
    private List<SalesOrderSyncOrderDTO> records = new ArrayList<>();
    private int pageSize;
    private boolean hasMore;
    private String nextCursor;
}
