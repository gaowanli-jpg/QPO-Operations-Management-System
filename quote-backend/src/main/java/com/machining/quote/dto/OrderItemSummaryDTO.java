package com.machining.quote.dto;

import lombok.Data;

@Data
public class OrderItemSummaryDTO {
    private long itemCount;
    private long pricedCount;
    private long quotedCount;
    private long releasedCount;
    private long missingDrawingCount;
}
