package com.machining.quote.domain;

import java.util.List;
import java.util.Set;

public final class QuoteStatusWorkflow {
    public static final String UNPRICED = "未核价";
    public static final String PRICED = "已核价";
    public static final String QUOTED = "已报价";
    public static final String PARTIALLY_RELEASED = "部分已提单";
    public static final String RELEASED = "已生成提单";
    public static final String ITEM_RELEASED = "已提单";
    public static final String NO_DRAWING = "无图";
    public static final String DISCARDED = "废弃";

    private static final Set<String> LEGACY_UNPRICED = Set.of("待工程部评估", "待核价");

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
        return PRICED.equals(status) || canExportQuoteSheet(status);
    }

    public static boolean canEditQuoteLeadTime(String status) {
        return PRICED.equals(status);
    }

    public static boolean canSaveQuoteSheet(String status) {
        return PRICED.equals(status);
    }

    public static boolean canExportQuoteSheet(String status) {
        return QUOTED.equals(status) || PARTIALLY_RELEASED.equals(status) || RELEASED.equals(status);
    }

    public static boolean canExportPricingSheet(String status) {
        return PRICED.equals(status);
    }

    public static boolean canGenerateDeliveryTicket(String status) {
        return QUOTED.equals(status) || PARTIALLY_RELEASED.equals(status) || RELEASED.equals(status);
    }

    public static boolean canReadReleaseWorkspace(String status) {
        return QUOTED.equals(status) || PARTIALLY_RELEASED.equals(status) || RELEASED.equals(status);
    }

    public static String statusAfterSupplementDrawing(String currentStatus, boolean hadDrawingBefore, boolean hasPrice) {
        if (NO_DRAWING.equals(currentStatus) && !hadDrawingBefore && !hasPrice) {
            return UNPRICED;
        }
        return currentStatus;
    }

    public static boolean isRfqReleaseEligibleItem(String status, boolean hasDrawing, boolean hasPrice) {
        return hasDrawing && hasPrice && isMarketConfirmedItemStatus(status);
    }

    public static boolean isMarketConfirmedItemStatus(String status) {
        return QUOTED.equals(status) || ITEM_RELEASED.equals(status);
    }

    public static List<String> orderManagementStatuses() {
        return List.of(UNPRICED, PRICED, QUOTED, PARTIALLY_RELEASED, RELEASED, DISCARDED);
    }

    public static boolean isReleasedOrderStatus(String status) {
        return PARTIALLY_RELEASED.equals(status) || RELEASED.equals(status);
    }

    public static boolean isSupplementalPricingOrderStatus(String status) {
        return PRICED.equals(status) || QUOTED.equals(status) || isReleasedOrderStatus(status);
    }

    public static boolean isSupplementalQuoteOrderStatus(String status) {
        return QUOTED.equals(status) || isReleasedOrderStatus(status);
    }

    public static boolean isDiscardableOrderStatus(String status) {
        return UNPRICED.equals(status) || PRICED.equals(status) || QUOTED.equals(status);
    }

    public static String nextAfterRollbackQuoted(String currentStatus, boolean hasDeliveryTickets) {
        rejectIfDeliveryTicketsExist(hasDeliveryTickets);
        if (!QUOTED.equals(currentStatus)) {
            throw new IllegalStateException("Only quoted orders can be rolled back to priced");
        }
        return PRICED;
    }

    public static String nextAfterRollbackPriced(String currentStatus, boolean hasDeliveryTickets) {
        rejectIfDeliveryTicketsExist(hasDeliveryTickets);
        if (!PRICED.equals(currentStatus)) {
            throw new IllegalStateException("Only priced orders can be rolled back to unpriced");
        }
        return UNPRICED;
    }

    public static String nextAfterDiscard(String currentStatus, boolean hasDeliveryTickets) {
        rejectIfDeliveryTicketsExist(hasDeliveryTickets);
        if (!isDiscardableOrderStatus(currentStatus)) {
            throw new IllegalStateException("Released or discarded orders cannot be discarded");
        }
        return DISCARDED;
    }

    public static String nextAfterRestore(String currentStatus) {
        if (!DISCARDED.equals(currentStatus)) {
            throw new IllegalStateException("Only discarded orders can be restored");
        }
        return PRICED;
    }

    private static void rejectIfDeliveryTicketsExist(boolean hasDeliveryTickets) {
        if (hasDeliveryTickets) {
            throw new IllegalStateException("Order with delivery tickets cannot be discarded or rolled back");
        }
    }

    public static boolean isLegacyUnpricedStatus(String status) {
        return LEGACY_UNPRICED.contains(status);
    }

    public static String normalizeUnpricedStatus(String status) {
        return isLegacyUnpricedStatus(status) ? UNPRICED : status;
    }

}
