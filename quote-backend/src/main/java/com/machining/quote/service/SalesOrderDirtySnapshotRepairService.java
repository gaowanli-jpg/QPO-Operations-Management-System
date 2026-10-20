package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderDirtySnapshotRepairService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderDirtySnapshotRepairService.class);
    private static final String ACTION = "SALES_ORDER_DIRTY_SNAPSHOT_REPAIR";
    private static final String SOURCE_TYPE_SALES_ORDER = "SALES_ORDER";
    private static final String MATCHED = "\u5df2\u5339\u914d";
    private static final String WMS_UNASSIGNED = "\u672a\u5206\u914d";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";
    private static final String QUOTE_MODE_NORMAL = "NORMAL";
    private static final String ITEM_TYPE_ASSEMBLY = "\u7ec4\u5408\u4ef6";
    private static final String ITEM_TYPE_NORMAL = "\u666e\u901a\u4ef6";
    private static final String ASSEMBLY_RELATION_NOT_APPLICABLE = "\u4e0d\u9002\u7528";
    private static final String ASSEMBLY_RELATION_UNSPECIFIED = "\u672a\u6307\u5b9a\u7236\u5b50\u5173\u7cfb";
    private static final String ASSEMBLY_RELATION_SPECIFIED = "\u5df2\u6307\u5b9a\u7236\u5b50\u5173\u7cfb";

    private final SalesOrderItemMapper itemMapper;
    private final ItemQuoteVersionMapper sourceMapper;
    private final OrderDeliveryTicketMapper ticketMapper;
    private final ItemAssemblyRelationMapper historyRelationMapper;
    private final SalesOrderItemAssemblyRelationMapper salesOrderAssemblyRelationMapper;
    private final JsonSnapshotCopyService jsonSnapshotCopyService;
    private final ProcessRouteSnapshotService processRouteSnapshotService;
    private final AuditService auditService;

    @Autowired
    public SalesOrderDirtySnapshotRepairService(
            SalesOrderItemMapper itemMapper,
            ItemQuoteVersionMapper sourceMapper,
            OrderDeliveryTicketMapper ticketMapper,
            ItemAssemblyRelationMapper historyRelationMapper,
            SalesOrderItemAssemblyRelationMapper salesOrderAssemblyRelationMapper,
            JsonSnapshotCopyService jsonSnapshotCopyService,
            ProcessRouteSnapshotService processRouteSnapshotService,
            AuditService auditService) {
        this.itemMapper = itemMapper;
        this.sourceMapper = sourceMapper;
        this.ticketMapper = ticketMapper;
        this.historyRelationMapper = historyRelationMapper;
        this.salesOrderAssemblyRelationMapper = salesOrderAssemblyRelationMapper;
        this.jsonSnapshotCopyService = jsonSnapshotCopyService == null ? new JsonSnapshotCopyService() : jsonSnapshotCopyService;
        this.processRouteSnapshotService = processRouteSnapshotService == null
                ? new ProcessRouteSnapshotService(this.jsonSnapshotCopyService)
                : processRouteSnapshotService;
        this.auditService = auditService;
    }

    public RepairResult preview(Long salesOrderId, String executor) {
        RepairPlan plan = buildPlan(salesOrderId, resolveExecutor(executor));
        return plan.toResult(false, 0, 0, 0L);
    }

    @Transactional
    public RepairResult repair(Long salesOrderId, String executor) {
        long started = System.currentTimeMillis();
        RepairPlan plan = buildPlan(salesOrderId, resolveExecutor(executor));
        Map<String, Integer> skipped = new LinkedHashMap<>(plan.skippedReasons());
        Map<Long, SalesOrderItem> latestItemsById = new LinkedHashMap<>();
        int repairedItems = 0;
        int repairedTickets = 0;

        for (ItemCandidate candidate : plan.itemCandidates()) {
            SalesOrderItem item = itemMapper.selectByIdForUpdate(candidate.item().getId());
            if (!isMatchedDirtyItem(item)) {
                increment(skipped, "itemChangedSincePreview");
                continue;
            }
            ItemQuoteVersion source = sourceMapper.selectById(item.getMatchedItemId());
            if (source == null) {
                increment(skipped, "itemSourceMissing");
                continue;
            }
            if (!hasAnySnapshot(source)) {
                increment(skipped, "itemSourceSnapshotMissing");
                continue;
            }
            if (applyItemRepair(item, source)) {
                itemMapper.updateById(item);
                repairedItems++;
            }
            latestItemsById.put(item.getId(), item);
        }

        for (TicketCandidate candidate : plan.ticketCandidates()) {
            OrderDeliveryTicket ticket = ticketMapper.selectByIdForUpdate(candidate.ticket().getId());
            if (!isEligibleDirtyTicket(ticket)) {
                increment(skipped, "ticketChangedSincePreview");
                continue;
            }
            SalesOrderItem sourceItem = latestItemsById.get(ticket.getSourceItemId());
            if (sourceItem == null) {
                sourceItem = itemMapper.selectByIdForUpdate(ticket.getSourceItemId());
            }
            if (sourceItem == null) {
                increment(skipped, "ticketMissingSalesOrderItem");
                continue;
            }
            if (isMissingSnapshot(sourceItem.getMaterialData()) && isMissingSnapshot(sourceItem.getProcessRoute())) {
                increment(skipped, "ticketSourceSnapshotMissing");
                continue;
            }
            if (applyTicketRepair(ticket, sourceItem)) {
                ticketMapper.updateById(ticket);
                repairedTickets++;
            }
        }

        long durationMs = System.currentTimeMillis() - started;
        RepairResult result = plan.toResult(true, repairedItems, repairedTickets, durationMs, skipped);
        recordSummary(salesOrderId, result);
        log.info(
                "Sales order dirty snapshot repair completed executor={} sourceOrderId={} orderCount={} salesOrderItemCount={} ticketCount={} skippedReasons={} durationMs={}",
                result.executor(), salesOrderId, result.orderCount(), result.repairedItemCount(), result.repairedTicketCount(),
                result.skippedReasons(), result.durationMs());
        return result;
    }

    private RepairPlan buildPlan(Long salesOrderId, String executor) {
        LocalDateTime executedAt = LocalDateTime.now();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        List<SalesOrderItem> dirtyItems = safeList(itemMapper.selectList(buildDirtyItemQuery(salesOrderId)));
        List<ItemCandidate> itemCandidates = new ArrayList<>();
        Set<Long> orderIds = new LinkedHashSet<>();
        Map<Long, SalesOrderItem> itemById = new LinkedHashMap<>();
        for (SalesOrderItem item : dirtyItems) {
            if (item == null) {
                continue;
            }
            if (item.getSalesOrderId() != null) {
                orderIds.add(item.getSalesOrderId());
            }
            if (item.getId() != null) {
                itemById.put(item.getId(), item);
            }
            ItemQuoteVersion source = sourceMapper.selectById(item.getMatchedItemId());
            if (source == null) {
                increment(skipped, "itemSourceMissing");
                continue;
            }
            if (!hasAnySnapshot(source)) {
                increment(skipped, "itemSourceSnapshotMissing");
                continue;
            }
            itemCandidates.add(new ItemCandidate(item, source));
        }
        Set<Long> repairableItemIds = itemCandidates.stream()
                .map(candidate -> candidate.item().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<OrderDeliveryTicket> dirtyTickets = safeList(ticketMapper.selectList(buildDirtyTicketQuery(salesOrderId)));
        loadMissingTicketSourceItems(dirtyTickets, itemById);
        List<TicketCandidate> ticketCandidates = new ArrayList<>();
        for (OrderDeliveryTicket ticket : dirtyTickets) {
            if (ticket == null) {
                continue;
            }
            if (ticket.getSourceOrderId() != null) {
                orderIds.add(ticket.getSourceOrderId());
            }
            if (!isPendingMes(ticket)) {
                increment(skipped, "ticketBlockedByMes");
                continue;
            }
            if (!isPendingWms(ticket)) {
                increment(skipped, "ticketBlockedByWms");
                continue;
            }
            SalesOrderItem sourceItem = itemById.get(ticket.getSourceItemId());
            if (sourceItem == null) {
                increment(skipped, "ticketMissingSalesOrderItem");
                continue;
            }
            if (isMissingSnapshot(sourceItem.getMaterialData())
                    && isMissingSnapshot(sourceItem.getProcessRoute())
                    && !repairableItemIds.contains(sourceItem.getId())) {
                increment(skipped, "ticketSourceSnapshotMissing");
                continue;
            }
            ticketCandidates.add(new TicketCandidate(ticket, sourceItem));
        }
        return new RepairPlan(executor, executedAt, orderIds.size(), dirtyItems.size(), itemCandidates,
                ticketCandidates, skipped);
    }

    private QueryWrapper<SalesOrderItem> buildDirtyItemQuery(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("match_status", MATCHED)
                .isNotNull("matched_item_id");
        if (salesOrderId != null) {
            query.eq("sales_order_id", salesOrderId);
        }
        query.and(wrapper -> wrapper
                .isNull("material_data")
                .or().apply("material_data = CAST({0} AS jsonb)", "{}")
                .or().apply("material_data = CAST({0} AS jsonb)", "[]")
                .or().isNull("process_route")
                .or().apply("process_route = CAST({0} AS jsonb)", "{}")
                .or().apply("process_route = CAST({0} AS jsonb)", "[]")
                .or().isNull("fee_data")
                .or().apply("fee_data = CAST({0} AS jsonb)", "{}")
                .or().apply("fee_data = CAST({0} AS jsonb)", "[]"));
        query.orderByAsc("sales_order_id").orderByAsc("line_no").orderByAsc("id");
        return query;
    }

    private QueryWrapper<OrderDeliveryTicket> buildDirtyTicketQuery(Long salesOrderId) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("source_type", SOURCE_TYPE_SALES_ORDER);
        if (salesOrderId != null) {
            query.eq("source_order_id", salesOrderId);
        }
        query.and(wrapper -> wrapper
                .isNull("material_data")
                .or().apply("material_data = CAST({0} AS jsonb)", "{}")
                .or().apply("material_data = CAST({0} AS jsonb)", "[]")
                .or().isNull("process_route")
                .or().apply("process_route = CAST({0} AS jsonb)", "{}")
                .or().apply("process_route = CAST({0} AS jsonb)", "[]"));
        query.orderByAsc("source_order_id").orderByAsc("source_item_id").orderByAsc("id");
        return query;
    }

    private void loadMissingTicketSourceItems(List<OrderDeliveryTicket> tickets, Map<Long, SalesOrderItem> itemById) {
        List<Long> missingIds = tickets.stream()
                .map(OrderDeliveryTicket::getSourceItemId)
                .filter(Objects::nonNull)
                .filter(id -> !itemById.containsKey(id))
                .distinct()
                .toList();
        if (missingIds.isEmpty()) {
            return;
        }
        for (SalesOrderItem item : safeList(itemMapper.selectBatchIds(missingIds))) {
            if (item != null && item.getId() != null) {
                itemById.put(item.getId(), item);
            }
        }
    }

    private boolean applyItemRepair(SalesOrderItem item, ItemQuoteVersion source) {
        boolean changed = false;
        changed |= setTextIfBlank(item.getEngineeringMaterialCode(), source.getEngineeringMaterialCode(), item::setEngineeringMaterialCode);
        changed |= setTextIfBlank(item.getEngineeringMaterialName(), source.getEngineeringMaterialName(), item::setEngineeringMaterialName);
        changed |= setTextIfBlank(item.getEngineeringMaterialTypeCode(), source.getEngineeringMaterialTypeCode(), item::setEngineeringMaterialTypeCode);
        changed |= setTextIfBlank(item.getEngineeringMaterialTypeName(), source.getEngineeringMaterialTypeName(), item::setEngineeringMaterialTypeName);
        changed |= setTextIfBlank(item.getEngineeringMaterialCategory(), source.getEngineeringMaterialCategory(), item::setEngineeringMaterialCategory);
        changed |= setTextIfBlank(item.getMaterialShape(), source.getMaterialShape(), item::setMaterialShape);
        if (item.getMaterialGrossWeight() == null && source.getMaterialGrossWeight() != null) {
            item.setMaterialGrossWeight(source.getMaterialGrossWeight());
            changed = true;
        }
        if (item.getPartNetWeight() == null && source.getPartNetWeight() != null) {
            item.setPartNetWeight(source.getPartNetWeight());
            changed = true;
        }
        if (shouldFillQuoteMode(item, source)) {
            item.setQuoteMode(source.getQuoteMode());
            changed = true;
        }
        if (item.getAssemblyFee() == null && source.getAssemblyFee() != null) {
            item.setAssemblyFee(BigDecimal.valueOf(source.getAssemblyFee()));
            changed = true;
        }
        if (item.getBaseCost() == null && source.getBaseCost() != null) {
            item.setBaseCost(BigDecimal.valueOf(source.getBaseCost()));
            changed = true;
        }
        if (isBlank(item.getItemType())) {
            item.setItemType(QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? ITEM_TYPE_ASSEMBLY : ITEM_TYPE_NORMAL);
            changed = true;
        }
        if (isMissingSnapshot(item.getMaterialData()) && !isMissingSnapshot(source.getMaterialData())) {
            item.setMaterialData(jsonSnapshotCopyService.deepCopySnapshot(source.getMaterialData()));
            changed = true;
        }
        if (isMissingSnapshot(item.getProcessRoute()) && !isMissingSnapshot(source.getProcessRoute())) {
            item.setProcessRoute(processRouteSnapshotService.copySnapshot(source.getProcessRoute()));
            changed = true;
        }
        if (isMissingSnapshot(item.getFeeData()) && !isMissingSnapshot(source.getFeeData())) {
            item.setFeeData(jsonSnapshotCopyService.deepCopySnapshot(source.getFeeData()));
            changed = true;
        }
        String assemblyStatus = resolveAssemblyRelationStatus(item, source);
        if (!Objects.equals(item.getAssemblyRelationStatus(), assemblyStatus)) {
            item.setAssemblyRelationStatus(assemblyStatus);
            changed = true;
        }
        if (changed) {
            item.setUpdatedAt(LocalDateTime.now());
        }
        return changed;
    }

    private boolean applyTicketRepair(OrderDeliveryTicket ticket, SalesOrderItem item) {
        boolean changed = false;
        changed |= setTextIfBlank(ticket.getEngineeringMaterialCode(), item.getEngineeringMaterialCode(), ticket::setEngineeringMaterialCode);
        changed |= setTextIfBlank(ticket.getEngineeringMaterialName(), item.getEngineeringMaterialName(), ticket::setEngineeringMaterialName);
        changed |= setTextIfBlank(ticket.getEngineeringMaterialTypeCode(), item.getEngineeringMaterialTypeCode(), ticket::setEngineeringMaterialTypeCode);
        changed |= setTextIfBlank(ticket.getEngineeringMaterialTypeName(), item.getEngineeringMaterialTypeName(), ticket::setEngineeringMaterialTypeName);
        changed |= setTextIfBlank(ticket.getEngineeringMaterialCategory(), item.getEngineeringMaterialCategory(), ticket::setEngineeringMaterialCategory);
        changed |= setTextIfBlank(ticket.getMaterialShape(), item.getMaterialShape(), ticket::setMaterialShape);
        if (ticket.getMaterialGrossWeight() == null && item.getMaterialGrossWeight() != null) {
            ticket.setMaterialGrossWeight(item.getMaterialGrossWeight());
            changed = true;
        }
        if (ticket.getPartNetWeight() == null && item.getPartNetWeight() != null) {
            ticket.setPartNetWeight(item.getPartNetWeight());
            changed = true;
        }
        if (isMissingSnapshot(ticket.getMaterialData()) && !isMissingSnapshot(item.getMaterialData())) {
            ticket.setMaterialData(jsonSnapshotCopyService.deepCopySnapshot(item.getMaterialData()));
            changed = true;
        }
        if (isMissingSnapshot(ticket.getProcessRoute()) && !isMissingSnapshot(item.getProcessRoute())) {
            ticket.setProcessRoute(processRouteSnapshotService.copySnapshot(item.getProcessRoute()));
            changed = true;
        }
        return changed;
    }

    private String resolveAssemblyRelationStatus(SalesOrderItem item, ItemQuoteVersion source) {
        if (!QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode())) {
            deleteSalesOrderAssemblyRelations(item.getId());
            return ASSEMBLY_RELATION_NOT_APPLICABLE;
        }
        List<ItemAssemblyRelation> historyRelations = listHistoryAssemblyRelations(source.getId());
        if (historyRelations.isEmpty()) {
            deleteSalesOrderAssemblyRelations(item.getId());
            return ASSEMBLY_RELATION_UNSPECIFIED;
        }
        Map<Integer, ItemQuoteVersion> childSourceById = loadHistoryChildSources(historyRelations);
        Map<String, SalesOrderItem> currentChildByKey = listSalesOrderItems(item.getSalesOrderId()).stream()
                .filter(candidate -> candidate != null && candidate.getId() != null && !Objects.equals(candidate.getId(), item.getId()))
                .collect(Collectors.toMap(
                        candidate -> itemKey(candidate.getPartNo(), candidate.getVersion()),
                        candidate -> candidate,
                        (left, ignored) -> left,
                        LinkedHashMap::new));
        List<SalesOrderItemAssemblyRelation> relationsToWrite = new ArrayList<>();
        for (ItemAssemblyRelation historyRelation : historyRelations) {
            ItemQuoteVersion childSource = childSourceById.get(historyRelation.getChildItemId());
            if (childSource == null) {
                deleteSalesOrderAssemblyRelations(item.getId());
                return ASSEMBLY_RELATION_UNSPECIFIED;
            }
            SalesOrderItem currentChild = currentChildByKey.get(itemKey(childSource.getPartNo(), childSource.getVersion()));
            if (currentChild == null) {
                deleteSalesOrderAssemblyRelations(item.getId());
                return ASSEMBLY_RELATION_UNSPECIFIED;
            }
            SalesOrderItemAssemblyRelation relation = new SalesOrderItemAssemblyRelation();
            relation.setSalesOrderId(item.getSalesOrderId());
            relation.setParentItemId(item.getId());
            relation.setChildItemId(currentChild.getId());
            relation.setQtyPerParent(toBigDecimal(historyRelation.getChildQtyPerParent()));
            relation.setSortNo(historyRelation.getSortNo());
            relation.setSourceParentItemId(historyRelation.getParentItemId());
            relation.setSourceChildItemId(historyRelation.getChildItemId());
            relation.setCreatedAt(LocalDateTime.now());
            relation.setUpdatedAt(LocalDateTime.now());
            relationsToWrite.add(relation);
        }
        deleteSalesOrderAssemblyRelations(item.getId());
        for (SalesOrderItemAssemblyRelation relation : relationsToWrite) {
            salesOrderAssemblyRelationMapper.insert(relation);
        }
        return ASSEMBLY_RELATION_SPECIFIED;
    }

    private List<ItemAssemblyRelation> listHistoryAssemblyRelations(Integer sourceParentItemId) {
        if (sourceParentItemId == null) {
            return List.of();
        }
        QueryWrapper<ItemAssemblyRelation> query = new QueryWrapper<>();
        query.eq("parent_item_id", sourceParentItemId).orderByAsc("sort_no").orderByAsc("id");
        return safeList(historyRelationMapper.selectList(query));
    }

    private Map<Integer, ItemQuoteVersion> loadHistoryChildSources(List<ItemAssemblyRelation> relations) {
        List<Integer> ids = relations.stream()
                .map(ItemAssemblyRelation::getChildItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return safeList(sourceMapper.selectBatchIds(ids)).stream()
                .filter(source -> source != null && source.getId() != null)
                .collect(Collectors.toMap(ItemQuoteVersion::getId, source -> source, (left, ignored) -> left));
    }

    private List<SalesOrderItem> listSalesOrderItems(Long salesOrderId) {
        if (salesOrderId == null) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("line_no").orderByAsc("id");
        return safeList(itemMapper.selectList(query));
    }

    private void deleteSalesOrderAssemblyRelations(Long parentItemId) {
        if (parentItemId == null) {
            return;
        }
        QueryWrapper<SalesOrderItemAssemblyRelation> query = new QueryWrapper<>();
        query.eq("parent_item_id", parentItemId);
        salesOrderAssemblyRelationMapper.delete(query);
    }

    private boolean isMatchedDirtyItem(SalesOrderItem item) {
        return item != null
                && MATCHED.equals(item.getMatchStatus())
                && item.getMatchedItemId() != null
                && (isMissingSnapshot(item.getMaterialData())
                || isMissingSnapshot(item.getProcessRoute())
                || isMissingSnapshot(item.getFeeData()));
    }

    private boolean isEligibleDirtyTicket(OrderDeliveryTicket ticket) {
        return ticket != null
                && SOURCE_TYPE_SALES_ORDER.equals(ticket.getSourceType())
                && isPendingMes(ticket)
                && isPendingWms(ticket)
                && (isMissingSnapshot(ticket.getMaterialData()) || isMissingSnapshot(ticket.getProcessRoute()));
    }

    private boolean isPendingMes(OrderDeliveryTicket ticket) {
        return ticket != null && "PENDING".equals(normalize(ticket.getMesSyncStatus()));
    }

    private boolean isPendingWms(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return false;
        }
        String status = normalize(ticket.getWmsAllocateStatus());
        return status == null || "PENDING".equals(status) || WMS_UNASSIGNED.equals(status);
    }

    private boolean shouldFillQuoteMode(SalesOrderItem item, ItemQuoteVersion source) {
        if (source == null || isBlank(source.getQuoteMode())) {
            return false;
        }
        if (isBlank(item.getQuoteMode())) {
            return true;
        }
        return QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode())
                && QUOTE_MODE_NORMAL.equalsIgnoreCase(item.getQuoteMode());
    }

    private boolean hasAnySnapshot(ItemQuoteVersion source) {
        return source != null
                && (!isMissingSnapshot(source.getMaterialData())
                || !isMissingSnapshot(source.getProcessRoute())
                || !isMissingSnapshot(source.getFeeData()));
    }

    private boolean isMissingSnapshot(Object value) {
        return jsonSnapshotCopyService.isMissingSnapshot(value);
    }

    private boolean setTextIfBlank(String current, String source, TextSetter setter) {
        if (isBlank(current) && !isBlank(source)) {
            setter.set(source.trim());
            return true;
        }
        return false;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String itemKey(String partNo, String version) {
        return normalize(partNo) + "\n" + normalize(version);
    }

    private void recordSummary(Long salesOrderId, RepairResult result) {
        if (auditService != null) {
            auditService.record(ACTION, "sales_order_item", salesOrderId, null, result.toAuditSummary());
        }
    }

    private String resolveExecutor(String executor) {
        if (!isBlank(executor)) {
            return executor.trim();
        }
        AuthContext.SessionUser user = AuthContext.get();
        if (user == null) {
            return "system";
        }
        if (!isBlank(user.displayName())) {
            return user.displayName().trim();
        }
        if (!isBlank(user.username())) {
            return user.username().trim();
        }
        return "system";
    }

    private void increment(Map<String, Integer> counters, String key) {
        counters.merge(key, 1, Integer::sum);
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    @FunctionalInterface
    private interface TextSetter {
        void set(String value);
    }

    public record RepairResult(
            boolean applied,
            String executor,
            LocalDateTime executedAt,
            long durationMs,
            int orderCount,
            int candidateItemCount,
            int repairedItemCount,
            int candidateTicketCount,
            int repairedTicketCount,
            Map<String, Integer> skippedReasons) {
        public RepairResult {
            skippedReasons = skippedReasons == null ? Map.of() : new LinkedHashMap<>(skippedReasons);
        }

        public Map<String, Object> toAuditSummary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("executor", executor);
            summary.put("executedAt", executedAt == null ? null : executedAt.toString());
            summary.put("applied", applied);
            summary.put("durationMs", durationMs);
            summary.put("orderCount", orderCount);
            summary.put("candidateItemCount", candidateItemCount);
            summary.put("salesOrderItemCount", repairedItemCount);
            summary.put("candidateTicketCount", candidateTicketCount);
            summary.put("ticketCount", repairedTicketCount);
            summary.put("skippedReasons", skippedReasons);
            return summary;
        }
    }

    private record RepairPlan(
            String executor,
            LocalDateTime executedAt,
            int orderCount,
            int scannedItemCount,
            List<ItemCandidate> itemCandidates,
            List<TicketCandidate> ticketCandidates,
            Map<String, Integer> skippedReasons) {
        RepairResult toResult(boolean applied, int repairedItemCount, int repairedTicketCount, long durationMs) {
            return toResult(applied, repairedItemCount, repairedTicketCount, durationMs, skippedReasons);
        }

        RepairResult toResult(
                boolean applied,
                int repairedItemCount,
                int repairedTicketCount,
                long durationMs,
                Map<String, Integer> effectiveSkippedReasons) {
            return new RepairResult(
                    applied,
                    executor,
                    executedAt,
                    durationMs,
                    orderCount,
                    scannedItemCount,
                    repairedItemCount,
                    ticketCandidates.size(),
                    repairedTicketCount,
                    effectiveSkippedReasons);
        }
    }

    private record ItemCandidate(SalesOrderItem item, ItemQuoteVersion source) {
    }

    private record TicketCandidate(OrderDeliveryTicket ticket, SalesOrderItem sourceItem) {
    }
}
