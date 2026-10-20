package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

@Service
public class ConfirmedMaterialNoReuseService {
    public MaterialNoReuseResult copyToRfqItem(ItemQuoteVersion target, ItemQuoteVersion source) {
        return notApplicable();
    }

    public MaterialNoReuseResult copyToSalesOrderItem(SalesOrderItem target, ItemQuoteVersion source) {
        return notApplicable();
    }

    private MaterialNoReuseResult notApplicable() {
        return new MaterialNoReuseResult(MaterialNoReuseResult.Status.NOT_APPLICABLE);
    }

    public static class MaterialNoConflictException extends RuntimeException {
        public MaterialNoConflictException(String message) {
            super(message);
        }
    }
}
