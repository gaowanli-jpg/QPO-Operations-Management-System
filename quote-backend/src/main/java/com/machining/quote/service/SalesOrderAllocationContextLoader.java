package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderAllocationContextLoader {
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;

    public Map<Integer, Context> load(List<OrderDeliveryTicketAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return Map.of();
        }
        List<Long> allocationIds = allocations.stream()
                .map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull)
                .map(Integer::longValue)
                .distinct()
                .toList();
        List<SalesOrderItem> linkedItems = allocationIds.isEmpty()
                ? List.of()
                : itemMapper.selectByAllocationIds(allocationIds);
        Map<Long, SalesOrderItem> linkedByAllocationId = new LinkedHashMap<>();
        if (linkedItems != null) {
            for (SalesOrderItem item : linkedItems) {
                if (item != null && item.getAllocationId() != null) {
                    linkedByAllocationId.put(item.getAllocationId(), item);
                }
            }
        }

        List<OrderDeliveryTicketAllocation> unlinked = allocations.stream()
                .filter(row -> row != null && row.getId() != null)
                .filter(row -> !linkedByAllocationId.containsKey(row.getId().longValue()))
                .toList();
        Map<Integer, List<SalesOrderItem>> byTicket = loadByTicket(unlinked);
        Map<Long, SalesOrderItem> byDirectItem = loadByDirectSource(unlinked);

        Map<Integer, SalesOrderItem> itemsByAllocationId = new LinkedHashMap<>();
        Map<Integer, Boolean> linkedFlags = new LinkedHashMap<>();
        Map<Integer, String> errors = new LinkedHashMap<>();
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (allocation == null || allocation.getId() == null) {
                continue;
            }
            Long allocationId = allocation.getId().longValue();
            SalesOrderItem linkedItem = linkedByAllocationId.get(allocationId);
            if (linkedItem != null) {
                itemsByAllocationId.put(allocation.getId(), linkedItem);
                linkedFlags.put(allocation.getId(), true);
                if (!Objects.equals(linkedItem.getAllocationId(), allocationId)) {
                    errors.put(allocation.getId(), "sales order item allocation link does not match allocation");
                } else if (isDirectSalesOrder(allocation)
                        && allocation.getSourceOrderId() != null
                        && !Objects.equals(allocation.getSourceOrderId(), linkedItem.getSalesOrderId())) {
                    errors.put(allocation.getId(), "linked sales order item belongs to another sales order");
                }
                continue;
            }

            SalesOrderItem ticketItem = allocation.getTicketId() == null
                    ? null
                    : onlyItem(byTicket.get(allocation.getTicketId()), allocation.getId(), errors);
            SalesOrderItem directItem = isDirectSalesOrder(allocation) && allocation.getSourceItemId() != null
                    ? byDirectItem.get(allocation.getSourceItemId())
                    : null;
            if (ticketItem != null && directItem != null && !Objects.equals(ticketItem.getId(), directItem.getId())) {
                errors.put(allocation.getId(), "sales order item resolution is ambiguous for allocation");
                continue;
            }
            SalesOrderItem fallbackItem = ticketItem != null ? ticketItem : directItem;
            if (fallbackItem != null && isDirectSalesOrder(allocation)
                    && allocation.getSourceOrderId() != null
                    && !Objects.equals(allocation.getSourceOrderId(), fallbackItem.getSalesOrderId())) {
                errors.put(allocation.getId(), "sales order item belongs to another source order");
                continue;
            }
            if (fallbackItem != null) {
                itemsByAllocationId.put(allocation.getId(), fallbackItem);
                linkedFlags.put(allocation.getId(), false);
            } else {
                linkedFlags.put(allocation.getId(), false);
                errors.putIfAbsent(allocation.getId(), "sales order item is not linked to allocation");
            }
        }

        List<Long> salesOrderIds = itemsByAllocationId.values().stream()
                .map(SalesOrderItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SalesOrderHeader> headersById = new LinkedHashMap<>();
        if (!salesOrderIds.isEmpty()) {
            List<SalesOrderHeader> headers = headerMapper.selectBatchIds(salesOrderIds);
            if (headers != null) {
                for (SalesOrderHeader header : headers) {
                    if (header != null && header.getId() != null) {
                        headersById.put(header.getId(), header);
                    }
                }
            }
        }

        Map<Integer, Context> result = new LinkedHashMap<>();
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (allocation == null || allocation.getId() == null) {
                continue;
            }
            SalesOrderItem item = itemsByAllocationId.get(allocation.getId());
            SalesOrderHeader header = item == null || item.getSalesOrderId() == null
                    ? null
                    : headersById.get(item.getSalesOrderId());
            String error = errors.get(allocation.getId());
            if (item != null && (item.getSalesOrderId() == null || item.getLineNo() == null || item.getLineNo() <= 0)) {
                error = "sales order item is missing its order or line number";
            } else if (item != null && header == null) {
                error = "sales order header is missing for linked item";
            }
            result.put(allocation.getId(), new Context(item, header,
                    Boolean.TRUE.equals(linkedFlags.get(allocation.getId())), error));
        }
        return result;
    }

    private Map<Integer, List<SalesOrderItem>> loadByTicket(List<OrderDeliveryTicketAllocation> allocations) {
        List<Integer> ticketIds = allocations.stream()
                .map(OrderDeliveryTicketAllocation::getTicketId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<SalesOrderItem>> result = new LinkedHashMap<>();
        List<SalesOrderItem> items = itemMapper.selectBySourceTicketIds(ticketIds);
        if (items != null) {
            for (SalesOrderItem item : items) {
                if (item != null && item.getSourceTicketId() != null) {
                    result.computeIfAbsent(item.getSourceTicketId(), ignored -> new ArrayList<>()).add(item);
                }
            }
        }
        return result;
    }

    private Map<Long, SalesOrderItem> loadByDirectSource(List<OrderDeliveryTicketAllocation> allocations) {
        List<Long> itemIds = allocations.stream()
                .filter(this::isDirectSalesOrder)
                .map(OrderDeliveryTicketAllocation::getSourceItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SalesOrderItem> result = new LinkedHashMap<>();
        List<SalesOrderItem> items = itemMapper.selectBatchIds(itemIds);
        if (items != null) {
            for (SalesOrderItem item : items) {
                if (item != null && item.getId() != null) {
                    result.put(item.getId(), item);
                }
            }
        }
        return result;
    }

    private SalesOrderItem onlyItem(List<SalesOrderItem> items, Integer allocationId, Map<Integer, String> errors) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        if (items.size() > 1) {
            errors.put(allocationId, "multiple sales order items reference the same source ticket");
            return null;
        }
        return items.get(0);
    }

    private boolean isDirectSalesOrder(OrderDeliveryTicketAllocation allocation) {
        return "SALES_ORDER".equalsIgnoreCase(allocation.getSourceType());
    }

    public record Context(SalesOrderItem item, SalesOrderHeader header, boolean allocationLinked, String error) {
    }
}
