package com.machining.quote.dto;

import com.machining.quote.entity.ItemQuoteVersion;
import java.util.List;
import lombok.Data;

@Data
public class OrderItemPageDTO {
    private List<ItemQuoteVersion> records;
    private long total;
    private int page;
    private int pageSize;
    private OrderItemSummaryDTO summary;

    public static OrderItemPageDTO of(
            List<ItemQuoteVersion> records,
            long total,
            int page,
            int pageSize,
            OrderItemSummaryDTO summary) {
        OrderItemPageDTO result = new OrderItemPageDTO();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setSummary(summary);
        return result;
    }
}
