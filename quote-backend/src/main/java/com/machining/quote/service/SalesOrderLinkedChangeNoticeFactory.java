package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderLinkedChangeNotice;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SalesOrderLinkedChangeNoticeFactory {
    private static final String NOT_OCCURRED = "NOT_OCCURRED";
    private static final String PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";
    private static final Set<String> LOCKED_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");

    public SalesOrderLinkedChangeNotice create(
            SalesOrderHeader order,
            SalesOrderItem item,
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations,
            String triggerScope,
            String eventKey,
            String reason,
            LocalDateTime at,
            Long operatorId) {
        List<OrderDeliveryTicket> safeTickets = tickets == null ? List.of() : tickets;
        List<OrderDeliveryTicketAllocation> safeAllocations = allocations == null ? List.of() : allocations;
        List<OrderDeliveryTicket> wmsTickets = safeTickets.stream()
                .filter(ticket -> submitted(ticket.getWmsAllocateStatus(), ticket.getWmsAllocatedAt(), ticket.getWmsAllocateMessage()))
                .toList();
        List<OrderDeliveryTicketAllocation> productionRows = safeAllocations.stream()
                .filter(row -> PRODUCTION_REQUIRED.equalsIgnoreCase(text(row.getAllocationType())))
                .filter(row -> submitted(row.getMesProductionStatus(), row.getMesProductionSyncedAt())
                        || hasText(row.getMesProductionRequestId()) || hasText(row.getMesProductionExternalId())
                        || hasText(row.getMesProductionMessage()))
                .toList();
        List<OrderDeliveryTicketAllocation> stockRows = safeAllocations.stream()
                .filter(row -> LOCKED_TYPES.contains(text(row.getAllocationType()).toUpperCase(Locale.ROOT)))
                .filter(row -> submitted(row.getStockPickingStatus(), row.getStockPickingSyncedAt())
                        || hasText(row.getStockPickingBizId()) || hasText(row.getStockPickingExternalId())
                        || hasText(row.getStockPickingMessage()))
                .toList();

        SalesOrderLinkedChangeNotice notice = new SalesOrderLinkedChangeNotice();
        notice.setSalesOrderId(order.getId());
        notice.setSalesOrderItemId(item.getId());
        notice.setSalesOrderNo(firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        notice.setLineNo(item.getLineNo());
        notice.setCustomerId(order.getCustomerId());
        notice.setCustomerCode(order.getCustomerCode());
        notice.setCustomerName(firstNonBlank(order.getCustomerName(), order.getCustomerShortName()));
        notice.setCustomerOrderNo(firstNonBlank(item.getCustomerOrderNo(), order.getCustomerOrderNo()));
        notice.setInternalProjectNo(firstNonBlank(item.getInternalProjectNo(), order.getInternalProjectNo()));
        notice.setPartNo(item.getPartNo());
        notice.setVersion(item.getVersion());
        notice.setDescription(item.getDescription());
        notice.setMaterialName(item.getMaterialName());
        notice.setTriggerScope(triggerScope);
        notice.setLinkedDiscardEventKey(eventKey);
        notice.setLinkedDiscardedAt(at);
        notice.setLinkedDiscardedBy(operatorId);
        notice.setLinkedDiscardReason(reason);
        notice.setWmsSubmitted(!wmsTickets.isEmpty());
        notice.setWmsStatus(summarize(wmsTickets.stream().map(OrderDeliveryTicket::getWmsAllocateStatus).toList(), !wmsTickets.isEmpty()));
        notice.setWmsActionAt(latest(wmsTickets.stream().map(OrderDeliveryTicket::getWmsAllocatedAt).toList()));
        notice.setMesProductionSubmitted(!productionRows.isEmpty());
        notice.setMesProductionStatus(summarize(productionRows.stream().map(OrderDeliveryTicketAllocation::getMesProductionStatus).toList(), !productionRows.isEmpty()));
        notice.setMesProductionRequestId(join(productionRows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getMesProductionRequestId(), row.getMesProductionExternalId())).toList()));
        notice.setMesProductionActionAt(latest(productionRows.stream().map(OrderDeliveryTicketAllocation::getMesProductionSyncedAt).toList()));
        notice.setStockPickingSubmitted(!stockRows.isEmpty());
        notice.setStockPickingStatus(summarize(stockRows.stream().map(OrderDeliveryTicketAllocation::getStockPickingStatus).toList(), !stockRows.isEmpty()));
        notice.setStockPickingBizId(join(stockRows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.getStockPickingBizId(), row.getStockPickingExternalId())).toList()));
        notice.setStockPickingActionAt(latest(stockRows.stream().map(OrderDeliveryTicketAllocation::getStockPickingSyncedAt).toList()));
        int impactCount = (wmsTickets.isEmpty() ? 0 : 1)
                + (productionRows.isEmpty() ? 0 : 1)
                + (stockRows.isEmpty() ? 0 : 1);
        notice.setDownstreamImpactCount(impactCount);
        notice.setHandlingStatus("UNHANDLED");
        notice.setCreatedAt(at);
        notice.setUpdatedAt(at);
        return notice;
    }

    private boolean submitted(String status, LocalDateTime actionAt) {
        return submitted(status, actionAt, null);
    }

    private boolean submitted(String status, LocalDateTime actionAt, String evidence) {
        String value = text(status).toUpperCase(Locale.ROOT);
        return actionAt != null || hasText(evidence)
                || (!value.isEmpty() && !"PENDING".equals(value) && !NOT_OCCURRED.equals(value));
    }

    private String summarize(List<String> statuses, boolean submitted) {
        if (!submitted) return NOT_OCCURRED;
        Set<String> submittedStatuses = statuses.stream()
                .map(this::text)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (submittedStatuses.isEmpty()) return "UNKNOWN";
        if (submittedStatuses.size() == 1) return submittedStatuses.iterator().next();
        return "MIXED: " + String.join(", ", submittedStatuses);
    }

    private LocalDateTime latest(List<LocalDateTime> values) {
        return values.stream().filter(value -> value != null).max(Comparator.naturalOrder()).orElse(null);
    }

    private String join(List<String> values) {
        List<String> unique = new ArrayList<>(values.stream().filter(this::hasText).map(String::trim).distinct().toList());
        if (unique.isEmpty()) return null;
        String joined = String.join(", ", unique);
        return joined.length() <= 2000 ? joined : joined.substring(0, 1997) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (hasText(value)) return value.trim();
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
