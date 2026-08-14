package com.machining.quote.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SalesOrderSyncPageData {
    private List<Map<String, Object>> records = new ArrayList<>();
    private int pageSize;
    private boolean hasMore;
    private String nextCursor;
}
