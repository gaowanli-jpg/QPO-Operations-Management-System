package com.machining.quote.domain;

import java.util.Set;

public final class QuoteStatusWorkflow {
    public static final String UNPRICED = "未核价";
    public static final String PRICED = "已核价";
    public static final String QUOTED = "已报价";
    public static final String PARTIALLY_RELEASED = "部分已提单";
    public static final String RELEASED = "已生成提单";
    public static final String ITEM_RELEASED = "已提单";
    public static final String NO_DRAWING = "无图";

    private static final Set<String> LEGACY_UNPRICED = Set.of("待工程部评估", "待核价");
    private static final Set<String> LEGACY_ITEM_RELEASED = Set.of("已部分提单", "已生成提单");

    private QuoteStatusWorkflow() {
    }

    public static String nextAfterEngineerComplete(String currentStatus) {
        if (!UNPRICED.equals(currentStatus) && !isLegacyUnpricedStatus(currentStatus)) {
            throw new IllegalStateException("Only unpriced orders can be completed by engineering");
        }
        return PRICED;
    }

    public static String nextAfterMarketSaveQuote(String currentStatus) {
        if (!PRICED.equals(currentStatus)) {
            throw new IllegalStateException("Only priced orders can be saved as quoted");
        }
        return QUOTED;
    }

    public static boolean canReadQuoteProcess(String status) {
        return PRICED.equals(status) || QUOTED.equals(status);
    }

    public static boolean canEditQuoteLeadTime(String status) {
        return PRICED.equals(status);
    }

    public static boolean canSaveQuoteSheet(String status) {
        return PRICED.equals(status);
    }

    public static boolean canExportQuoteSheet(String status) {
        return QUOTED.equals(status);
    }

    public static boolean canGenerateDeliveryTicket(String status) {
        return QUOTED.equals(status) || PARTIALLY_RELEASED.equals(status);
    }

    public static boolean isLegacyUnpricedStatus(String status) {
        return LEGACY_UNPRICED.contains(status);
    }

    public static String normalizeUnpricedStatus(String status) {
        return isLegacyUnpricedStatus(status) ? UNPRICED : status;
    }

    public static boolean isLegacyItemReleasedStatus(String status) {
        return LEGACY_ITEM_RELEASED.contains(status);
    }
}
