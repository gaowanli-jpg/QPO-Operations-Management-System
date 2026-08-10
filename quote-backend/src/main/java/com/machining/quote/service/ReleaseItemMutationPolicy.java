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
        if (remainingQty <= 0) {
            return new Result(false, 0, "已全部提单，不能修改");
        }
        String status = item.getStatus();
        if (QuoteStatusWorkflow.ITEM_RELEASED.equals(status)
                || QuoteStatusWorkflow.RELEASED.equals(status)
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(status)) {
            return new Result(false, remainingQty, "零件已提单，不能修改");
        }
        return new Result(true, remainingQty, "");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    public record Result(boolean mutable, int remainingQty, String reason) {
    }
}
