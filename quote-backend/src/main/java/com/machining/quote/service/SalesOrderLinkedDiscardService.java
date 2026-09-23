package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.SalesOrderLinkedDiscardBatchResult;
import com.machining.quote.dto.SalesOrderLinkedDiscardRelation;
import com.machining.quote.dto.SalesOrderLinkedDiscardResult;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesOrderLinkedDiscardService {
    public static final String LINKED_DISCARD_SOURCE = "SALES_ORDER_ITEM_LINKED";
    static final String ACTION_DISCARD = "SALES_ORDER_ITEM_LINKED_DISCARD";
    static final String ACTION_RESTORE = "SALES_ORDER_ITEM_LINKED_RESTORE";
    public static final String ACTION_BATCH_DISCARD = "SALES_ORDER_LINKED_DISCARD";
    public static final String ACTION_BATCH_RESTORE = "SALES_ORDER_LINKED_RESTORE";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_PENDING_CONFIRMATION = "结果待确认";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";
    private static final Set<String> LOCKED_ALLOCATION_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private SalesOrderManageService salesOrderManageService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private SalesOrderLinkedChangeNoticeService linkedChangeNoticeService;

    @Transactional
    public SalesOrderLinkedDiscardResult discard(Long salesOrderId, Long itemId, OrderStatusChangeReq req) {
        String reason = requiredReason(req);
        SalesOrderHeader order = lockOrder(salesOrderId);
        SalesOrderItem item = lockItem(order, itemId);
        if (Boolean.TRUE.equals(item.getLinkedDiscarded())) {
            throw new RuntimeException("该销售订单零件已经关联废弃");
        }
        assertU9OperationAllowed(order);

        List<OrderDeliveryTicket> tickets = lockRelatedTickets(order, item);
        List<OrderDeliveryTicketAllocation> allocations = lockRelatedAllocations(order, item, tickets);
        List<OrderDeliveryTicket> ticketTargets = new ArrayList<>();
        List<OrderDeliveryTicketAllocation> allocationTargets = new ArrayList<>();
        int skippedCompleted = 0;
        int skippedAlreadyDiscarded = 0;

        for (OrderDeliveryTicket ticket : tickets) {
            if (Boolean.TRUE.equals(ticket.getDiscarded())) {
                skippedAlreadyDiscarded++;
            } else if (isCompletedTicket(ticket, allocations)) {
                skippedCompleted++;
            } else {
                ticketTargets.add(ticket);
            }
        }
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (!isTrackedAllocation(allocation)) {
                continue;
            }
            if (Boolean.TRUE.equals(allocation.getDiscarded())) {
                skippedAlreadyDiscarded++;
            } else if (isCompletedAllocation(allocation)) {
                skippedCompleted++;
            } else {
                allocationTargets.add(allocation);
            }
        }
        rejectCallbackPending(ticketTargets, allocationTargets);

        boolean u9ChangedByLinkedAction = !Boolean.TRUE.equals(item.getU9ChangeExcluded());
        if (u9ChangedByLinkedAction) {
            salesOrderManageService.discardItemForU9Change(salesOrderId, itemId, req);
        }

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        if (linkedChangeNoticeService != null) {
            linkedChangeNoticeService.createForItem(order, item, tickets, allocations,
                    "ITEM", reason, now, operatorId);
        }
        for (OrderDeliveryTicket ticket : ticketTargets) {
            ticket.setDiscarded(true);
            ticket.setDiscardedAt(now);
            ticket.setDiscardedBy(operatorId);
            ticket.setDiscardReason(reason);
            ticket.setDiscardSource(LINKED_DISCARD_SOURCE);
            ticket.setDiscardSourceSalesOrderItemId(item.getId());
            if (ticketMapper.updateById(ticket) <= 0) {
                throw new RuntimeException("关联废弃已提零件失败，请刷新后重试");
            }
        }
        for (OrderDeliveryTicketAllocation allocation : allocationTargets) {
            allocation.setDiscarded(true);
            allocation.setDiscardedAt(now);
            allocation.setDiscardedBy(operatorId);
            allocation.setDiscardReason(reason);
            allocation.setDiscardSource(LINKED_DISCARD_SOURCE);
            allocation.setDiscardSourceSalesOrderItemId(item.getId());
            allocation.setUpdatedAt(now);
            if (allocationMapper.updateById(allocation) <= 0) {
                throw new RuntimeException("关联废弃下游零件失败，请刷新后重试");
            }
        }

        updateLinkedState(item, true, u9ChangedByLinkedAction, reason, now, operatorId);
        SalesOrderLinkedDiscardResult result = result(order, item, true, ticketTargets.size(),
                countProduction(allocationTargets), countLocked(allocationTargets), skippedCompleted,
                skippedAlreadyDiscarded, reason,
                "销售订单零件及仍在工作池中的关联零件已废弃");
        recordAudit(order, item, result, ACTION_DISCARD);
        return result;
    }

    @Transactional
    public SalesOrderLinkedDiscardResult restore(Long salesOrderId, Long itemId, OrderStatusChangeReq req) {
        String reason = requiredReason(req);
        SalesOrderHeader order = lockOrder(salesOrderId);
        SalesOrderItem item = lockItem(order, itemId);
        if (!Boolean.TRUE.equals(item.getLinkedDiscarded())) {
            throw new RuntimeException("该销售订单零件未进行关联废弃，无需关联恢复");
        }
        assertU9OperationAllowed(order);

        List<OrderDeliveryTicket> tickets = lockRelatedTickets(order, item);
        List<OrderDeliveryTicketAllocation> allocations = lockRelatedAllocations(order, item, tickets);
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        int restoredTickets = 0;
        List<OrderDeliveryTicketAllocation> restoredAllocationTargets = new ArrayList<>();
        int skippedCompleted = 0;
        int skippedAlreadyDiscarded = 0;
        for (OrderDeliveryTicket ticket : tickets) {
            if (!isLinkedDiscarded(ticket, item)) {
                if (Boolean.TRUE.equals(ticket.getDiscarded())) {
                    skippedAlreadyDiscarded++;
                }
                continue;
            }
            if (isCompletedTicket(ticket, allocations)) {
                skippedCompleted++;
                continue;
            }
            ticket.setDiscarded(false);
            ticket.setRestoredAt(now);
            ticket.setRestoredBy(operatorId);
            ticket.setRestoreReason(reason);
            ticket.setDiscardSource(null);
            ticket.setDiscardSourceSalesOrderItemId(null);
            if (ticketMapper.updateById(ticket) <= 0) {
                throw new RuntimeException("关联恢复已提零件失败，请刷新后重试");
            }
            restoredTickets++;
        }
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (!isLinkedDiscarded(allocation, item)) {
                if (Boolean.TRUE.equals(allocation.getDiscarded())) {
                    skippedAlreadyDiscarded++;
                }
                continue;
            }
            if (!isTrackedAllocation(allocation)) {
                continue;
            }
            if (isCompletedAllocation(allocation)) {
                skippedCompleted++;
                continue;
            }
            allocation.setDiscarded(false);
            allocation.setRestoredAt(now);
            allocation.setRestoredBy(operatorId);
            allocation.setRestoreReason(reason);
            allocation.setDiscardSource(null);
            allocation.setDiscardSourceSalesOrderItemId(null);
            allocation.setUpdatedAt(now);
            if (allocationMapper.updateById(allocation) <= 0) {
                throw new RuntimeException("关联恢复下游零件失败，请刷新后重试");
            }
            restoredAllocationTargets.add(allocation);
        }

        if (Boolean.TRUE.equals(item.getLinkedU9ChangeExcluded())
                && Boolean.TRUE.equals(item.getU9ChangeExcluded())) {
            salesOrderManageService.restoreItemForU9Change(salesOrderId, itemId, req);
        }
        updateLinkedState(item, false, false, reason, now, operatorId);
        SalesOrderLinkedDiscardResult result = result(order, item, false, restoredTickets,
                countProduction(restoredAllocationTargets),
                countLocked(restoredAllocationTargets), skippedCompleted,
                skippedAlreadyDiscarded, reason, "销售订单零件及本次关联废弃的下游零件已恢复");
        recordAudit(order, item, result, ACTION_RESTORE);
        return result;
    }

    /**
     * Discards an entire sales order in one transaction. The related delivery
     * records are loaded and locked in batches so this does not repeat the
     * single-item order/ticket/allocation queries for every line.
     */
    @Transactional
    public SalesOrderLinkedDiscardBatchResult discardOrder(Long salesOrderId, OrderStatusChangeReq req) {
        String reason = requiredReason(req);
        SalesOrderHeader order = lockOrder(salesOrderId);
        assertOrderReadyForLinkedDiscard(order);
        assertU9OperationAllowed(order);

        String fromStatus = order.getStatus();
        List<SalesOrderItem> items = lockOrderItems(order);
        BatchRelations relations = lockOrderRelations(order, items);
        List<SalesOrderItem> targetItems = items.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getLinkedDiscarded()))
                .toList();

        BatchTargets targets = collectDiscardTargets(targetItems, relations);
        rejectCallbackPending(targets.ticketEntities, targets.allocationEntities);

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        if (linkedChangeNoticeService != null) {
            linkedChangeNoticeService.createForItems(order, targetItems,
                    ticketRowsByItem(relations), allocationRowsByItem(relations), reason, now, operatorId);
        }
        updateLinkedDiscardTargets(targets, reason, operatorId, now);

        List<Long> targetItemIds = targetItems.stream().map(SalesOrderItem::getId).toList();
        List<Long> u9OwnedItemIds = targetItems.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getU9ChangeExcluded()))
                .map(SalesOrderItem::getId)
                .toList();
        if (!u9OwnedItemIds.isEmpty()) {
            int updated = itemMapper.markItemsU9ChangeExcluded(
                    order.getId(), u9OwnedItemIds, reason, operatorId, now);
            requireUpdatedCount(updated, u9OwnedItemIds.size(), "销售订单零件 U9 排除状态");
        }
        if (!targetItemIds.isEmpty()) {
            int updated = itemMapper.markItemsLinkedDiscarded(
                    order.getId(), targetItemIds, u9OwnedItemIds, reason, operatorId, now);
            requireUpdatedCount(updated, targetItemIds.size(), "销售订单零件关联状态");
        }

        int expectedStatusUpdates = (int) items.stream()
                .filter(item -> !QuoteStatusWorkflow.DISCARDED.equals(item.getStatus()))
                .count();
        int statusUpdates = itemMapper.markOrderItemsStatus(
                order.getId(), QuoteStatusWorkflow.DISCARDED, now);
        requireUpdatedCount(statusUpdates, expectedStatusUpdates, "销售订单零件废弃状态");

        order.setStatus(QuoteStatusWorkflow.DISCARDED);
        order.setLinkedDiscarded(true);
        order.setLinkedDiscardedAt(now);
        order.setLinkedDiscardedBy(operatorId);
        order.setLinkedDiscardReason(reason);
        order.setLinkedRestoredAt(null);
        order.setLinkedRestoredBy(null);
        order.setLinkedRestoreReason(null);
        if (headerMapper.updateById(order) <= 0) {
            throw new RuntimeException("销售订单关联废弃状态保存失败，请刷新后重试");
        }

        SalesOrderLinkedDiscardBatchResult result = batchResult(
                order,
                fromStatus,
                QuoteStatusWorkflow.DISCARDED,
                true,
                items.size(),
                targetItems.size(),
                targets,
                items.size() - targetItems.size(),
                reason,
                "销售订单及仍在工作池中的关联零件已整单废弃");
        recordBatchAudit(order, result, ACTION_BATCH_DISCARD);
        return result;
    }

    /**
     * Restores the linked records owned by the order-level or item-level linked
     * discard action. Ordinary status recovery is deliberately tolerant: a
     * previous ordinary restore must not block restoring downstream links.
     */
    @Transactional
    public SalesOrderLinkedDiscardBatchResult restoreOrder(Long salesOrderId, OrderStatusChangeReq req) {
        String reason = requiredReason(req);
        SalesOrderHeader order = lockOrder(salesOrderId);
        assertU9OperationAllowed(order);

        String fromStatus = order.getStatus();
        List<SalesOrderItem> items = lockOrderItems(order);
        BatchRelations relations = lockOrderRelations(order, items);
        List<SalesOrderItem> targetItems = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getLinkedDiscarded()))
                .toList();
        BatchTargets targets = collectRestoreTargets(targetItems, relations);

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        updateLinkedRestoreTargets(targets, reason, operatorId, now);

        List<Long> linkedU9ItemIds = targetItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getLinkedU9ChangeExcluded())
                        && Boolean.TRUE.equals(item.getU9ChangeExcluded()))
                .map(SalesOrderItem::getId)
                .toList();
        if (!linkedU9ItemIds.isEmpty()) {
            int updated = itemMapper.restoreItemsU9ChangeExcluded(
                    order.getId(), linkedU9ItemIds, reason, operatorId, now);
            requireUpdatedCount(updated, linkedU9ItemIds.size(), "销售订单零件 U9 恢复状态");
        }
        List<Long> targetItemIds = targetItems.stream().map(SalesOrderItem::getId).toList();
        if (!targetItemIds.isEmpty()) {
            int updated = itemMapper.restoreItemsLinkedDiscarded(
                    order.getId(), targetItemIds, reason, operatorId, now);
            requireUpdatedCount(updated, targetItemIds.size(), "销售订单零件关联恢复状态");
        }

        int expectedStatusUpdates = (int) items.stream()
                .filter(item -> QuoteStatusWorkflow.DISCARDED.equals(item.getStatus()))
                .count();
        int statusUpdates = itemMapper.restoreOrderItemsStatus(
                order.getId(), QuoteStatusWorkflow.DISCARDED, QuoteStatusWorkflow.RELEASED, now);
        requireUpdatedCount(statusUpdates, expectedStatusUpdates, "销售订单零件恢复状态");

        if (QuoteStatusWorkflow.DISCARDED.equals(order.getStatus())) {
            order.setStatus(QuoteStatusWorkflow.RELEASED);
        }
        order.setLinkedDiscarded(false);
        order.setLinkedRestoredAt(now);
        order.setLinkedRestoredBy(operatorId);
        order.setLinkedRestoreReason(reason);
        if (headerMapper.updateById(order) <= 0) {
            throw new RuntimeException("销售订单关联恢复状态保存失败，请刷新后重试");
        }

        SalesOrderLinkedDiscardBatchResult result = batchResult(
                order,
                fromStatus,
                order.getStatus(),
                false,
                items.size(),
                targetItems.size(),
                targets,
                0,
                reason,
                "销售订单及本次关联废弃的下游零件已整单恢复");
        recordBatchAudit(order, result, ACTION_BATCH_RESTORE);
        return result;
    }

    private SalesOrderHeader lockOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader order = headerMapper.selectByIdForUpdate(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
        return order;
    }

    private void assertOrderReadyForLinkedDiscard(SalesOrderHeader order) {
        if (!QuoteStatusWorkflow.RELEASED.equals(order.getStatus())) {
            throw new RuntimeException("只有已生成提单的销售订单可以关联废弃");
        }
    }

    private List<SalesOrderItem> lockOrderItems(SalesOrderHeader order) {
        List<SalesOrderItem> items = itemMapper.selectBySalesOrderIdForUpdate(order.getId());
        return items == null ? List.of() : items;
    }

    private BatchRelations lockOrderRelations(SalesOrderHeader order, List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return new BatchRelations(List.of(), List.of(), Map.of(), Map.of());
        }
        Set<Long> itemIds = items.stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> sourceTicketIds = items.stream()
                .map(SalesOrderItem::getSourceTicketId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        QueryWrapper<OrderDeliveryTicket> ticketQuery = new QueryWrapper<>();
        ticketQuery.and(wrapper -> {
            wrapper.and(direct -> direct.eq("source_type", "SALES_ORDER")
                    .eq("source_order_id", order.getId())
                    .in("source_item_id", itemIds));
            if (!sourceTicketIds.isEmpty()) {
                wrapper.or().in("id", sourceTicketIds);
            }
        }).orderByAsc("id").last("FOR UPDATE");
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQuery);
        tickets = tickets == null ? List.of() : tickets;
        for (OrderDeliveryTicket ticket : tickets) {
            // The order row is already locked and scope-checked above. Avoid
            // re-querying that same header once per ticket in order-level flow;
            // linked RFQ tickets still require their own scope check.
            if (dataScopeService != null
                    && !("SALES_ORDER".equalsIgnoreCase(text(ticket.getSourceType()))
                    && Objects.equals(order.getId(), ticket.getSourceOrderId()))) {
                dataScopeService.assertCanAccessDeliveryTicket(ticket);
            }
        }

        Set<Integer> ticketIds = tickets.stream()
                .map(OrderDeliveryTicket::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        QueryWrapper<OrderDeliveryTicketAllocation> allocationQuery = new QueryWrapper<>();
        allocationQuery.and(wrapper -> {
            if (ticketIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("ticket_id", ticketIds);
            }
            wrapper.or(direct -> direct.eq("source_type", "SALES_ORDER")
                    .eq("source_order_id", order.getId())
                    .in("source_item_id", itemIds));
        }).orderByAsc("id").last("FOR UPDATE");
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectList(allocationQuery);
        allocations = allocations == null ? List.of() : allocations;

        Map<Integer, Long> sourceTicketToItem = items.stream()
                .filter(item -> item.getSourceTicketId() != null && item.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SalesOrderItem::getSourceTicketId,
                        SalesOrderItem::getId,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Map<Integer, OrderDeliveryTicket>> ticketsByItem = new LinkedHashMap<>();
        Map<Long, Map<Integer, OrderDeliveryTicketAllocation>> allocationsByItem = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            ticketsByItem.put(itemId, new LinkedHashMap<>());
            allocationsByItem.put(itemId, new LinkedHashMap<>());
        }
        Map<Integer, Long> ticketToItem = new LinkedHashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            Long itemId = null;
            if ("SALES_ORDER".equalsIgnoreCase(text(ticket.getSourceType()))
                    && Objects.equals(order.getId(), ticket.getSourceOrderId())) {
                itemId = ticket.getSourceItemId();
            }
            if (itemId == null) {
                itemId = sourceTicketToItem.get(ticket.getId());
            }
            if (itemId != null && ticketsByItem.containsKey(itemId) && ticket.getId() != null) {
                ticketsByItem.get(itemId).put(ticket.getId(), ticket);
                ticketToItem.put(ticket.getId(), itemId);
            }
        }
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            Long itemId = null;
            if ("SALES_ORDER".equalsIgnoreCase(text(allocation.getSourceType()))
                    && Objects.equals(order.getId(), allocation.getSourceOrderId())) {
                itemId = allocation.getSourceItemId();
            }
            if (itemId == null) {
                itemId = ticketToItem.get(allocation.getTicketId());
            }
            if (itemId != null && allocationsByItem.containsKey(itemId) && allocation.getId() != null) {
                allocationsByItem.get(itemId).put(allocation.getId(), allocation);
            }
        }
        return new BatchRelations(
                tickets,
                allocations,
                ticketsByItem,
                allocationsByItem);
    }

    private BatchTargets collectDiscardTargets(List<SalesOrderItem> items, BatchRelations relations) {
        BatchTargets targets = new BatchTargets();
        for (SalesOrderItem item : items) {
            List<OrderDeliveryTicket> tickets = relations.ticketsByItem
                    .getOrDefault(item.getId(), Map.of()).values().stream().toList();
            List<OrderDeliveryTicketAllocation> allocations = relations.allocationsByItem
                    .getOrDefault(item.getId(), Map.of()).values().stream().toList();
            for (OrderDeliveryTicket ticket : tickets) {
                if (Boolean.TRUE.equals(ticket.getDiscarded())) {
                    targets.skippedAlreadyDiscardedCount++;
                } else if (isCompletedTicket(ticket, relations.allocations)) {
                    targets.skippedCompletedCount++;
                } else {
                    targets.ticketEntities.add(ticket);
                    targets.ticketRelations.add(new SalesOrderLinkedDiscardRelation(ticket.getId(), item.getId()));
                }
            }
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (!isTrackedAllocation(allocation)) {
                    continue;
                }
                if (Boolean.TRUE.equals(allocation.getDiscarded())) {
                    targets.skippedAlreadyDiscardedCount++;
                } else if (isCompletedAllocation(allocation)) {
                    targets.skippedCompletedCount++;
                } else {
                    targets.allocationEntities.add(allocation);
                    targets.allocationRelations.add(new SalesOrderLinkedDiscardRelation(allocation.getId(), item.getId()));
                }
            }
        }
        return targets;
    }

    private BatchTargets collectRestoreTargets(List<SalesOrderItem> items, BatchRelations relations) {
        BatchTargets targets = new BatchTargets();
        for (SalesOrderItem item : items) {
            List<OrderDeliveryTicket> tickets = relations.ticketsByItem
                    .getOrDefault(item.getId(), Map.of()).values().stream().toList();
            List<OrderDeliveryTicketAllocation> allocations = relations.allocationsByItem
                    .getOrDefault(item.getId(), Map.of()).values().stream().toList();
            for (OrderDeliveryTicket ticket : tickets) {
                if (!isLinkedDiscarded(ticket, item)) {
                    if (Boolean.TRUE.equals(ticket.getDiscarded())) {
                        targets.skippedAlreadyDiscardedCount++;
                    }
                    continue;
                }
                if (isCompletedTicket(ticket, relations.allocations)) {
                    targets.skippedCompletedCount++;
                } else {
                    targets.ticketEntities.add(ticket);
                    targets.ticketRelations.add(new SalesOrderLinkedDiscardRelation(ticket.getId(), item.getId()));
                }
            }
            for (OrderDeliveryTicketAllocation allocation : allocations) {
                if (!isLinkedDiscarded(allocation, item)) {
                    if (Boolean.TRUE.equals(allocation.getDiscarded())) {
                        targets.skippedAlreadyDiscardedCount++;
                    }
                    continue;
                }
                if (!isTrackedAllocation(allocation)) {
                    continue;
                }
                if (isCompletedAllocation(allocation)) {
                    targets.skippedCompletedCount++;
                } else {
                    targets.allocationEntities.add(allocation);
                    targets.allocationRelations.add(new SalesOrderLinkedDiscardRelation(allocation.getId(), item.getId()));
                }
            }
        }
        return targets;
    }

    private Map<Long, List<OrderDeliveryTicket>> ticketRowsByItem(BatchRelations relations) {
        Map<Long, List<OrderDeliveryTicket>> rows = new LinkedHashMap<>();
        relations.ticketsByItem.forEach((itemId, tickets) -> rows.put(itemId, new ArrayList<>(tickets.values())));
        return rows;
    }

    private Map<Long, List<OrderDeliveryTicketAllocation>> allocationRowsByItem(BatchRelations relations) {
        Map<Long, List<OrderDeliveryTicketAllocation>> rows = new LinkedHashMap<>();
        relations.allocationsByItem.forEach((itemId, allocations) -> rows.put(itemId, new ArrayList<>(allocations.values())));
        return rows;
    }

    private void updateLinkedDiscardTargets(
            BatchTargets targets,
            String reason,
            Long operatorId,
            LocalDateTime now) {
        if (!targets.ticketRelations.isEmpty()) {
            int updated = ticketMapper.markLinkedDiscarded(
                    targets.ticketRelations, LINKED_DISCARD_SOURCE, reason, operatorId, now);
            requireUpdatedCount(updated, targets.ticketRelations.size(), "已提零件关联废弃状态");
        }
        if (!targets.allocationRelations.isEmpty()) {
            int updated = allocationMapper.markLinkedDiscarded(
                    targets.allocationRelations, LINKED_DISCARD_SOURCE, reason, operatorId, now);
            requireUpdatedCount(updated, targets.allocationRelations.size(), "下游零件关联废弃状态");
        }
    }

    private void updateLinkedRestoreTargets(
            BatchTargets targets,
            String reason,
            Long operatorId,
            LocalDateTime now) {
        if (!targets.ticketRelations.isEmpty()) {
            int updated = ticketMapper.restoreLinkedDiscarded(
                    targets.ticketRelations, LINKED_DISCARD_SOURCE, reason, operatorId, now);
            requireUpdatedCount(updated, targets.ticketRelations.size(), "已提零件关联恢复状态");
        }
        if (!targets.allocationRelations.isEmpty()) {
            int updated = allocationMapper.restoreLinkedDiscarded(
                    targets.allocationRelations, LINKED_DISCARD_SOURCE, reason, operatorId, now);
            requireUpdatedCount(updated, targets.allocationRelations.size(), "下游零件关联恢复状态");
        }
    }

    private SalesOrderLinkedDiscardBatchResult batchResult(
            SalesOrderHeader order,
            String fromStatus,
            String toStatus,
            boolean linkedDiscarded,
            int totalItemCount,
            int processedItemCount,
            BatchTargets targets,
            int skippedAlreadyLinkedCount,
            String reason,
            String message) {
        SalesOrderLinkedDiscardBatchResult result = new SalesOrderLinkedDiscardBatchResult();
        result.setSuccess(true);
        result.setSalesOrderId(order.getId());
        result.setSalesOrderNo(firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        result.setFromStatus(fromStatus);
        result.setToStatus(toStatus);
        result.setLinkedDiscarded(linkedDiscarded);
        result.setTotalItemCount(totalItemCount);
        result.setProcessedItemCount(processedItemCount);
        result.setDeliveredItemCount(targets.ticketRelations.size());
        result.setProductionItemCount(countProduction(targets.allocationEntities));
        result.setLockedItemCount(countLocked(targets.allocationEntities));
        result.setSkippedCompletedCount(targets.skippedCompletedCount);
        result.setSkippedAlreadyDiscardedCount(targets.skippedAlreadyDiscardedCount);
        result.setSkippedAlreadyLinkedCount(skippedAlreadyLinkedCount);
        result.setReason(reason);
        result.setMessage(message);
        return result;
    }

    private void recordBatchAudit(SalesOrderHeader order, SalesOrderLinkedDiscardBatchResult result, String action) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("salesOrderId", order.getId());
        before.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        before.put("status", result.getFromStatus());
        before.put("linkedDiscarded", !result.isLinkedDiscarded());
        before.put("itemCount", result.getTotalItemCount());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("salesOrderId", order.getId());
        after.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        after.put("status", result.getToStatus());
        after.put("linkedDiscarded", result.isLinkedDiscarded());
        after.put("processedItemCount", result.getProcessedItemCount());
        after.put("deliveredItemCount", result.getDeliveredItemCount());
        after.put("productionItemCount", result.getProductionItemCount());
        after.put("lockedItemCount", result.getLockedItemCount());
        after.put("skippedCompletedCount", result.getSkippedCompletedCount());
        after.put("skippedAlreadyDiscardedCount", result.getSkippedAlreadyDiscardedCount());
        after.put("skippedAlreadyLinkedCount", result.getSkippedAlreadyLinkedCount());
        after.put("reason", result.getReason());
        auditService.record(action, "sales_order_header", order.getId(), before, after,
                "MARKET", "SUCCESS", result.getMessage(), null);
    }

    private void requireUpdatedCount(int updated, int expected, String target) {
        if (updated != expected) {
            throw new RuntimeException(target + "批量更新数量不一致，请刷新后重试");
        }
    }

    private record BatchRelations(
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations,
            Map<Long, Map<Integer, OrderDeliveryTicket>> ticketsByItem,
            Map<Long, Map<Integer, OrderDeliveryTicketAllocation>> allocationsByItem) {
    }

    private static final class BatchTargets {
        private final List<OrderDeliveryTicket> ticketEntities = new ArrayList<>();
        private final List<OrderDeliveryTicketAllocation> allocationEntities = new ArrayList<>();
        private final List<SalesOrderLinkedDiscardRelation> ticketRelations = new ArrayList<>();
        private final List<SalesOrderLinkedDiscardRelation> allocationRelations = new ArrayList<>();
        private int skippedCompletedCount;
        private int skippedAlreadyDiscardedCount;
    }

    private SalesOrderItem lockItem(SalesOrderHeader order, Long itemId) {
        if (itemId == null) {
            throw new RuntimeException("销售订单零件行 ID 不能为空");
        }
        SalesOrderItem item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(order.getId(), item.getSalesOrderId())) {
            throw new RuntimeException("销售订单零件行不属于当前销售订单");
        }
        return item;
    }

    private List<OrderDeliveryTicket> lockRelatedTickets(SalesOrderHeader order, SalesOrderItem item) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.and(wrapper -> {
            wrapper.eq("source_type", "SALES_ORDER")
                    .eq("source_order_id", order.getId())
                    .eq("source_item_id", item.getId());
            if (item.getSourceTicketId() != null) {
                wrapper.or().eq("id", item.getSourceTicketId());
            }
        }).orderByAsc("id");
        List<OrderDeliveryTicket> listed = ticketMapper.selectList(query);
        Map<Integer, OrderDeliveryTicket> locked = new LinkedHashMap<>();
        for (OrderDeliveryTicket listedTicket : listed == null ? List.<OrderDeliveryTicket>of() : listed) {
            if (listedTicket == null || listedTicket.getId() == null) {
                continue;
            }
            OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(listedTicket.getId());
            if (ticket == null) {
                continue;
            }
            if (dataScopeService != null) {
                dataScopeService.assertCanAccessDeliveryTicket(ticket);
            }
            locked.put(ticket.getId(), ticket);
        }
        return new ArrayList<>(locked.values());
    }

    private List<OrderDeliveryTicketAllocation> lockRelatedAllocations(
            SalesOrderHeader order,
            SalesOrderItem item,
            List<OrderDeliveryTicket> tickets) {
        Set<Integer> ticketIds = tickets.stream()
                .map(OrderDeliveryTicket::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        QueryWrapper<OrderDeliveryTicketAllocation> query = new QueryWrapper<>();
        query.and(wrapper -> {
            if (ticketIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("ticket_id", ticketIds);
            }
            wrapper.or(or -> or.eq("source_type", "SALES_ORDER")
                    .eq("source_order_id", order.getId())
                    .eq("source_item_id", item.getId()));
        }).orderByAsc("id");
        List<OrderDeliveryTicketAllocation> listed = allocationMapper.selectList(query);
        Map<Integer, OrderDeliveryTicketAllocation> locked = new LinkedHashMap<>();
        for (OrderDeliveryTicketAllocation listedAllocation : listed == null ? List.<OrderDeliveryTicketAllocation>of() : listed) {
            if (listedAllocation == null || listedAllocation.getId() == null) {
                continue;
            }
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(listedAllocation.getId());
            if (allocation != null) {
                locked.put(allocation.getId(), allocation);
            }
        }
        return new ArrayList<>(locked.values());
    }

    private void rejectCallbackPending(
            List<OrderDeliveryTicket> tickets,
            List<OrderDeliveryTicketAllocation> allocations) {
        for (OrderDeliveryTicket ticket : tickets) {
            if ("CALLBACK_PENDING".equalsIgnoreCase(text(ticket.getMesSyncStatus()))) {
                throw new RuntimeException("MES 待回调的已提零件不能关联废弃，请等待回调后再处理");
            }
        }
        for (OrderDeliveryTicketAllocation allocation : allocations) {
            if (PRODUCTION_REQUIRED.equalsIgnoreCase(text(allocation.getAllocationType()))
                    && "CALLBACK_PENDING".equalsIgnoreCase(text(allocation.getMesProductionStatus()))) {
                throw new RuntimeException("MES 待回调的待产零件不能关联废弃，请等待回调后再处理");
            }
        }
    }

    private boolean isCompletedTicket(OrderDeliveryTicket ticket, List<OrderDeliveryTicketAllocation> allocations) {
        boolean hasAllocation = allocations.stream().anyMatch(allocation -> Objects.equals(allocation.getTicketId(), ticket.getId()));
        return hasText(ticket.getMaterialNo())
                && STATUS_SUCCESS.equalsIgnoreCase(text(ticket.getMesSyncStatus()))
                && STATUS_SUCCESS.equalsIgnoreCase(text(ticket.getWmsAllocateStatus()))
                && hasAllocation;
    }

    private boolean isTrackedAllocation(OrderDeliveryTicketAllocation allocation) {
        String type = text(allocation.getAllocationType());
        return PRODUCTION_REQUIRED.equalsIgnoreCase(type) || LOCKED_ALLOCATION_TYPES.contains(type);
    }

    private boolean isCompletedAllocation(OrderDeliveryTicketAllocation allocation) {
        String type = text(allocation.getAllocationType());
        if (PRODUCTION_REQUIRED.equalsIgnoreCase(type)) {
            return STATUS_SUCCESS.equalsIgnoreCase(text(allocation.getMesProductionStatus()));
        }
        return LOCKED_ALLOCATION_TYPES.contains(type)
                && STATUS_SUCCESS.equalsIgnoreCase(text(allocation.getStockPickingStatus()));
    }

    private void updateLinkedState(
            SalesOrderItem item,
            boolean discarded,
            boolean u9Owned,
            String reason,
            LocalDateTime now,
            Long operatorId) {
        UpdateWrapper<SalesOrderItem> update = new UpdateWrapper<>();
        update.eq("id", item.getId())
                .eq("sales_order_id", item.getSalesOrderId())
                .set("linked_discarded", discarded)
                .set("linked_u9_change_excluded", discarded && u9Owned)
                .set("updated_at", now);
        if (discarded) {
            update.set("linked_discarded_at", now)
                    .set("linked_discarded_by", operatorId)
                    .set("linked_discard_reason", reason)
                    .set("linked_restored_at", null)
                    .set("linked_restored_by", null)
                    .set("linked_restore_reason", null);
        } else {
            update.set("linked_restored_at", now)
                    .set("linked_restored_by", operatorId)
                    .set("linked_restore_reason", reason);
        }
        if (itemMapper.update(null, update) <= 0) {
            throw new RuntimeException("销售订单零件关联状态保存失败，请刷新后重试");
        }
        item.setLinkedDiscarded(discarded);
        item.setLinkedU9ChangeExcluded(discarded && u9Owned);
    }

    private SalesOrderLinkedDiscardResult result(
            SalesOrderHeader order,
            SalesOrderItem item,
            boolean discarded,
            int deliveredCount,
            int productionCount,
            int lockedCount,
            int skippedCompleted,
            int skippedAlreadyDiscarded,
            String reason,
            String message) {
        SalesOrderLinkedDiscardResult result = new SalesOrderLinkedDiscardResult();
        result.setSuccess(true);
        result.setSalesOrderId(order.getId());
        result.setSalesOrderNo(firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        result.setSalesOrderItemId(item.getId());
        result.setLineNo(item.getLineNo());
        result.setLinkedDiscarded(discarded);
        result.setDeliveredItemCount(deliveredCount);
        result.setProductionItemCount(productionCount);
        result.setLockedItemCount(lockedCount);
        result.setSkippedCompletedCount(skippedCompleted);
        result.setSkippedAlreadyDiscardedCount(skippedAlreadyDiscarded);
        result.setReason(reason);
        result.setMessage(message);
        return result;
    }

    private int countProduction(List<OrderDeliveryTicketAllocation> allocations) {
        return (int) allocations.stream().filter(allocation -> PRODUCTION_REQUIRED.equalsIgnoreCase(text(allocation.getAllocationType()))).count();
    }

    private int countLocked(List<OrderDeliveryTicketAllocation> allocations) {
        return (int) allocations.stream().filter(allocation -> LOCKED_ALLOCATION_TYPES.contains(text(allocation.getAllocationType()))).count();
    }

    private boolean isLinkedDiscarded(OrderDeliveryTicket ticket, SalesOrderItem item) {
        return Boolean.TRUE.equals(ticket.getDiscarded())
                && LINKED_DISCARD_SOURCE.equals(ticket.getDiscardSource())
                && Objects.equals(item.getId(), ticket.getDiscardSourceSalesOrderItemId());
    }

    private boolean isLinkedDiscarded(OrderDeliveryTicketAllocation allocation, SalesOrderItem item) {
        return Boolean.TRUE.equals(allocation.getDiscarded())
                && LINKED_DISCARD_SOURCE.equals(allocation.getDiscardSource())
                && Objects.equals(item.getId(), allocation.getDiscardSourceSalesOrderItemId());
    }

    private void assertU9OperationAllowed(SalesOrderHeader order) {
        String status = text(order.getU9SyncStatus());
        if (STATUS_SYNCING.equals(status) || STATUS_PENDING_CONFIRMATION.equals(status)) {
            throw new RuntimeException("U9 状态为" + status + "，不能关联废弃或恢复销售订单零件");
        }
    }

    private String requiredReason(OrderStatusChangeReq req) {
        String reason = req == null ? null : req.getReason();
        if (!hasText(reason)) {
            throw new RuntimeException("原因不能为空");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new RuntimeException("原因长度不能超过 500 个字符");
        }
        return normalized;
    }

    private void recordAudit(
            SalesOrderHeader order,
            SalesOrderItem item,
            SalesOrderLinkedDiscardResult result,
            String action) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", order.getId());
        summary.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        summary.put("salesOrderItemId", item.getId());
        summary.put("lineNo", item.getLineNo());
        summary.put("linkedDiscarded", result.isLinkedDiscarded());
        summary.put("deliveredItemCount", result.getDeliveredItemCount());
        summary.put("productionItemCount", result.getProductionItemCount());
        summary.put("lockedItemCount", result.getLockedItemCount());
        summary.put("skippedCompletedCount", result.getSkippedCompletedCount());
        summary.put("skippedAlreadyDiscardedCount", result.getSkippedAlreadyDiscardedCount());
        summary.put("reason", result.getReason());
        auditService.record(action, "sales_order_item", item.getId(), null, summary,
                "MARKET", "SUCCESS", result.getMessage(), null);
    }

    private Long currentUserId() {
        AuthContext.SessionUser current = AuthContext.get();
        return current == null || current.id() == null ? null : current.id().longValue();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
