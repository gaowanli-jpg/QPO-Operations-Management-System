package com.machining.quote.service;

import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class SalesOrderChangeNoticeTargetResolver {
    private static final String PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";
    private static final Set<String> LOCKED_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "SUCCESS", "SUBMITTING", "PROCESSING", "RESULT_UNKNOWN", "RESPONSE_RECEIVED", "APPLY_FAILED");

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private MesProductionBatchItemMapper productionBatchItemMapper;
    @Autowired
    private MesIntegrationBatchMapper integrationBatchMapper;

    public List<Target> resolve(SalesOrderItem item) {
        if (item == null || item.getId() == null) {
            return List.of();
        }
        return resolve(List.of(item)).getOrDefault(item.getId(), List.of());
    }

    public Map<Long, List<Target>> resolve(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Integer, OrderDeliveryTicketAllocation> allocationsById = new LinkedHashMap<>();
        List<Integer> ids = items.stream()
                .map(SalesOrderItem::getAllocationId)
                .filter(Objects::nonNull)
                .map(Math::toIntExact)
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
            List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectBatchIds(ids);
            if (allocations != null) {
                allocations.stream().filter(Objects::nonNull)
                        .forEach(row -> allocationsById.put(row.getId(), row));
            }
        }
        List<Long> salesOrderIds = items.stream()
                .map(SalesOrderItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> salesOrderItemIds = items.stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, List<OrderDeliveryTicketAllocation>> sourceAllocationsByItem = new LinkedHashMap<>();
        if (!salesOrderIds.isEmpty() && !salesOrderItemIds.isEmpty()) {
            List<OrderDeliveryTicketAllocation> sourceAllocations = allocationMapper
                    .selectBySalesOrderItemIds(salesOrderIds, salesOrderItemIds);
            for (OrderDeliveryTicketAllocation allocation : sourceAllocations == null
                    ? List.<OrderDeliveryTicketAllocation>of() : sourceAllocations) {
                if (allocation.getSourceOrderId() != null && allocation.getSourceItemId() != null) {
                    sourceAllocationsByItem.computeIfAbsent(
                            allocationKey(allocation.getSourceOrderId().longValue(), allocation.getSourceItemId()),
                            ignored -> new ArrayList<>()).add(allocation);
                }
            }
        }
        Map<Long, List<Target>> result = new LinkedHashMap<>();
        List<OrderDeliveryTicketAllocation> allAllocations = new ArrayList<>();
        Map<Long, List<OrderDeliveryTicketAllocation>> allocationsByItem = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            Map<Integer, OrderDeliveryTicketAllocation> allocationsByItemId = new LinkedHashMap<>();
            if (item.getAllocationId() != null) {
                OrderDeliveryTicketAllocation row = allocationsById.get(Math.toIntExact(item.getAllocationId()));
                if (row != null) allocationsByItemId.put(row.getId(), row);
            }
            if (item.getAllocationId() == null) {
                for (OrderDeliveryTicketAllocation row : sourceAllocationsByItem.getOrDefault(
                        allocationKey(item.getSalesOrderId(), item.getId()), List.of())) {
                    if (row != null && row.getId() != null) allocationsByItemId.put(row.getId(), row);
                }
            }
            List<OrderDeliveryTicketAllocation> allocations = new ArrayList<>(allocationsByItemId.values());
            allocationsByItem.put(item.getId(), allocations);
            allAllocations.addAll(allocations);
        }
        Map<Integer, List<Target>> targetsByAllocationId = new LinkedHashMap<>();
        for (Target target : resolveAllocations(allAllocations)) {
            if (target.allocation().getId() != null) {
                targetsByAllocationId.computeIfAbsent(target.allocation().getId(), ignored -> new ArrayList<>())
                        .add(target);
            }
        }
        for (Map.Entry<Long, List<OrderDeliveryTicketAllocation>> entry : allocationsByItem.entrySet()) {
            List<Target> targets = new ArrayList<>();
            for (OrderDeliveryTicketAllocation allocation : entry.getValue()) {
                if (allocation.getId() != null) {
                    targets.addAll(targetsByAllocationId.getOrDefault(allocation.getId(), List.of()));
                }
            }
            targets.sort(Comparator.comparing(Target::targetType).thenComparing(Target::targetKey));
            result.put(entry.getKey(), targets);
        }
        return result;
    }

    public List<Target> resolveAllocations(List<OrderDeliveryTicketAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) return List.of();
        List<OrderDeliveryTicketAllocation> production = allocations.stream()
                .filter(row -> PRODUCTION_REQUIRED.equalsIgnoreCase(text(row.getAllocationType())))
                .filter(row -> active(row.getMesProductionStatus()) || hasText(row.getMesProductionExternalId()))
                .toList();
        List<OrderDeliveryTicketAllocation> stock = allocations.stream()
                .filter(row -> LOCKED_TYPES.contains(text(row.getAllocationType()).toUpperCase()))
                .filter(row -> active(row.getStockPickingStatus()) || hasText(row.getStockPickingExternalId()))
                .toList();
        Map<Integer, MesProductionBatchItem> batchItems = loadBatchItems(production);
        Map<Long, MesIntegrationBatch> batches = loadBatches(batchItems.values());
        List<Target> targets = new ArrayList<>();
        for (OrderDeliveryTicketAllocation row : production) {
            String externalId = trimToNull(row.getMesProductionExternalId());
            MesProductionBatchItem batchItem = batchItems.get(row.getId());
            String sourceBizId = batchItem == null ? trimToNull(row.getMesProductionRequestId())
                    : firstNonBlank(batches.get(batchItem.getBatchId()) == null ? null
                    : batches.get(batchItem.getBatchId()).getBizId(), row.getMesProductionRequestId());
            targets.add(target(row, "PRODUCTION", externalId, sourceBizId));
        }
        for (OrderDeliveryTicketAllocation row : stock) {
            String externalId = trimToNull(row.getStockPickingExternalId());
            targets.add(target(row, "STOCK_PICKING", externalId, row.getStockPickingBizId()));
        }
        targets.sort(Comparator.comparing(Target::targetType).thenComparing(Target::targetKey));
        return targets;
    }

    private Target target(OrderDeliveryTicketAllocation allocation, String type, String externalId, String sourceBizId) {
        String targetKey = externalId == null ? "ALLOCATION:" + allocation.getId() : externalId;
        return new Target(allocation, type, targetKey, externalId, trimToNull(sourceBizId));
    }

    private Map<Integer, MesProductionBatchItem> loadBatchItems(List<OrderDeliveryTicketAllocation> allocations) {
        List<Integer> ids = allocations.stream().map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        List<MesProductionBatchItem> rows = productionBatchItemMapper.selectLatestByAllocationIds(ids);
        Map<Integer, MesProductionBatchItem> result = new LinkedHashMap<>();
        if (rows != null) rows.forEach(row -> result.put(row.getAllocationId(), row));
        return result;
    }

    private Map<Long, MesIntegrationBatch> loadBatches(Iterable<MesProductionBatchItem> items) {
        List<Long> ids = new ArrayList<>();
        for (MesProductionBatchItem item : items) if (item.getBatchId() != null) ids.add(item.getBatchId());
        if (ids.isEmpty()) return Map.of();
        List<MesIntegrationBatch> rows = integrationBatchMapper.selectBatchIds(ids.stream().distinct().toList());
        Map<Long, MesIntegrationBatch> result = new LinkedHashMap<>();
        if (rows != null) rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }

    private boolean active(String status) {
        return status != null && ACTIVE_STATUSES.contains(status.trim().toUpperCase());
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
    private String trimToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return null;
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    private String allocationKey(Long salesOrderId, Long salesOrderItemId) {
        return salesOrderId + ":" + salesOrderItemId;
    }

    public record Target(OrderDeliveryTicketAllocation allocation,
                         String targetType,
                         String targetKey,
                         String targetExternalId,
                         String sourceBizId) {
    }
}
