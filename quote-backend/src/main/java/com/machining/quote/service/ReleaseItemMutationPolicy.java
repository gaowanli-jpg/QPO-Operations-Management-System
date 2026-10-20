package com.machining.quote.service;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import org.springframework.stereotype.Component;

@Component
public class ReleaseItemMutationPolicy {
    public Result evaluate(ItemQuoteVersion item, int ticketedQty) {
        if (item == null) {
            return new Result(false, 0, "零件当前不能修改");
        }
        int remainingQty = Math.max(0, safeInt(item.getQty()) - Math.max(0, ticketedQty));
        if (QuoteStatusWorkflow.DISCARDED.equals(item.getStatus())) {
            return new Result(false, remainingQty, "零件已废弃，不能在议价提单中操作");
        }
        return new Result(true, remainingQty, "");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    public record Result(boolean mutable, int remainingQty, String reason) {
    }
}
