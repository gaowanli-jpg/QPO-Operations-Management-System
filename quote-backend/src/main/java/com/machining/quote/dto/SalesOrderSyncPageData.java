package com.machining.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SalesOrderSyncPageData {
    private List<SalesOrderSyncOrderDTO> records = new ArrayList<>();
    private int pageSize;
    private boolean hasMore;
    private String nextCursor;
}
