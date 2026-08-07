package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

@Service
public class ConfirmedMaterialNoReuseService {
    private static final String MATERIAL_CONFIRMED = "\u5df2\u786e\u8ba4";

    public MaterialNoReuseResult copyToRfqItem(ItemQuoteVersion target, ItemQuoteVersion source) {
        if (!isConfirmedMaterialNo(source)) {
            return new MaterialNoReuseResult(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        }
        String existing = normalize(target == null ? null : target.getMaterialNo());
        if (existing != null && !existing.equals(source.getMaterialNo().trim())) {
            throw new MaterialNoConflictException("RFQ 目标零件已有不同物料号，不能覆盖");
        }
        boolean same = existing != null;
        apply(target, source);
        return new MaterialNoReuseResult(same
                ? MaterialNoReuseResult.Status.IDEMPOTENT
                : MaterialNoReuseResult.Status.COPIED);
    }

    public MaterialNoReuseResult copyToSalesOrderItem(SalesOrderItem target, ItemQuoteVersion source) {
        if (!isConfirmedMaterialNo(source)) {
            return new MaterialNoReuseResult(MaterialNoReuseResult.Status.NOT_APPLICABLE);
        }
        String existing = normalize(target == null ? null : target.getMaterialNo());
        if (existing != null && !existing.equals(source.getMaterialNo().trim())) {
            throw new MaterialNoConflictException("销售订单目标零件已有不同物料号，不能覆盖");
        }
        boolean same = existing != null;
        apply(target, source);
        return new MaterialNoReuseResult(same
                ? MaterialNoReuseResult.Status.IDEMPOTENT
                : MaterialNoReuseResult.Status.COPIED);
    }

    public boolean isConfirmedMaterialNo(ItemQuoteVersion source) {
        return source != null
                && !isBlank(source.getMaterialNo())
                && MATERIAL_CONFIRMED.equals(normalize(source.getMaterialNoStatus()));
    }

    private void apply(ItemQuoteVersion target, ItemQuoteVersion source) {
        if (target == null) {
            throw new IllegalArgumentException("目标零件不能为空");
        }
        target.setMaterialNo(source.getMaterialNo().trim());
        target.setMaterialNoStatus(MATERIAL_CONFIRMED);
        copyMetadataIfMissing(target, source);
    }

    private void apply(SalesOrderItem target, ItemQuoteVersion source) {
        if (target == null) {
            throw new IllegalArgumentException("目标零件不能为空");
        }
        target.setMaterialNo(source.getMaterialNo().trim());
        target.setMaterialNoStatus(MATERIAL_CONFIRMED);
        copyMetadataIfMissing(target, source);
    }

    private void copyMetadataIfMissing(ItemQuoteVersion target, ItemQuoteVersion source) {
        if (isBlank(target.getMaterialNoSource()) && !isBlank(source.getMaterialNoSource())) {
            target.setMaterialNoSource(source.getMaterialNoSource());
        }
        if (target.getMaterialNoSourceTicketId() == null && source.getMaterialNoSourceTicketId() != null) {
            target.setMaterialNoSourceTicketId(source.getMaterialNoSourceTicketId());
        }
        if (target.getMaterialNoSyncedAt() == null && source.getMaterialNoSyncedAt() != null) {
            target.setMaterialNoSyncedAt(source.getMaterialNoSyncedAt());
        }
        if (isBlank(target.getMaterialNoMessage()) && !isBlank(source.getMaterialNoMessage())) {
            target.setMaterialNoMessage(source.getMaterialNoMessage());
        }
    }

    private void copyMetadataIfMissing(SalesOrderItem target, ItemQuoteVersion source) {
        if (isBlank(target.getMaterialNoSource()) && !isBlank(source.getMaterialNoSource())) {
            target.setMaterialNoSource(source.getMaterialNoSource());
        }
        if (target.getMaterialNoSourceTicketId() == null && source.getMaterialNoSourceTicketId() != null) {
            target.setMaterialNoSourceTicketId(source.getMaterialNoSourceTicketId());
        }
        if (target.getMaterialNoSyncedAt() == null && source.getMaterialNoSyncedAt() != null) {
            target.setMaterialNoSyncedAt(source.getMaterialNoSyncedAt());
        }
        if (isBlank(target.getMaterialNoMessage()) && !isBlank(source.getMaterialNoMessage())) {
            target.setMaterialNoMessage(source.getMaterialNoMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    public static class MaterialNoConflictException extends RuntimeException {
        public MaterialNoConflictException(String message) {
            super(message);
        }
    }
}
