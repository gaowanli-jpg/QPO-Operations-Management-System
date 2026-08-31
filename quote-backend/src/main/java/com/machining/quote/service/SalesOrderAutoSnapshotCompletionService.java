package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionResult;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionRow;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderAutoSnapshotCompletionService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderAutoSnapshotCompletionService.class);
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 200;
    private static final String MATCHED = "已匹配";
    private static final String UNMATCHED = "未匹配";
    private static final String SKIPPED = "跳过";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";
    private static final String ASSEMBLY_RELATION_NOT_APPLICABLE = "不适用";
    private static final String ASSEMBLY_RELATION_UNSPECIFIED = "未指定父子关系";
    private static final String ASSEMBLY_RELATION_SPECIFIED = "已指定父子关系";
    private static final Set<String> AUTO_REUSABLE_REPEAT_STATUSES = Set.of(
            QuoteStatusWorkflow.PRICED,
            QuoteStatusWorkflow.QUOTED,
            QuoteStatusWorkflow.PARTIALLY_RELEASED,
            QuoteStatusWorkflow.RELEASED,
            QuoteStatusWorkflow.ITEM_RELEASED
    );

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;
    @Autowired
    private ItemAssemblyRelationMapper historyAssemblyRelationMapper;
    @Autowired
    private SalesOrderItemAssemblyRelationMapper salesOrderAssemblyRelationMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private ConfirmedMaterialNoReuseService confirmedMaterialNoReuseService = new ConfirmedMaterialNoReuseService();
    @Autowired(required = false)
    private ProcessRouteSnapshotService processRouteSnapshotService = new ProcessRouteSnapshotService();
    @Autowired(required = false)
    private JsonSnapshotCopyService jsonSnapshotCopyService = new JsonSnapshotCopyService();

    @Transactional
    public SalesOrderAutoSnapshotCompletionResult completeImportedOrder(Long salesOrderId) {
        SalesOrderHeader header = requireMatchableSalesOrder(salesOrderId);
        return completeInternal(header.getId());
    }

    private SalesOrderAutoSnapshotCompletionResult completeInternal(Long salesOrderId) {
        long startedAt = System.currentTimeMillis();
        List<SalesOrderItem> items = loadApplyItems(salesOrderId);
        SalesOrderAutoSnapshotCompletionResult resp = new SalesOrderAutoSnapshotCompletionResult();
        resp.setTotalItemCount(items.size());
        if (items.isEmpty()) {
            resp.setSuccess(true);
            resp.setMessage("没有需要补全的销售订单零件");
            return resp;
        }
        log.info("Sales order auto snapshot completion started salesOrderId={} itemCount={}", salesOrderId, items.size());

        Map<Long, SalesOrderHeader> headers = loadLockedAuthorizedHeaders(items);
        Map<String, ItemQuoteVersion> sourceByKey = releasedProductionSnapshotsByKey(items);
        int matchedCount = 0;
        int unmatchedCount = 0;
        int skippedCount = 0;
        int updatedCount = 0;
        int materialNoCopiedCount = 0;
        int materialNoSkippedCount = 0;
        int materialNoConflictCount = 0;
        for (SalesOrderItem item : items) {
            SalesOrderHeader header = headers.get(item.getSalesOrderId());
            ItemQuoteVersion source = sourceByKey.get(itemKey(item.getPartNo(), item.getVersion()));
            SalesOrderAutoSnapshotCompletionRow row;
            MaterialNoReuseResult materialNoResult;
            if (shouldSkipApply(item)) {
                markSkipped(item);
                materialNoResult = new MaterialNoReuseResult(MaterialNoReuseResult.Status.NOT_APPLICABLE);
                row = toApplyRow(header, item, source, false, false, true, materialNoResult);
                skippedCount++;
            } else if (source == null) {
                markUnmatched(item);
                materialNoResult = confirmedMaterialNoReuseService.copyToSalesOrderItem(item, null);
                salesOrderItemMapper.updateById(item);
                row = toApplyRow(header, item, null, false, true, false, materialNoResult);
                unmatchedCount++;
                updatedCount++;
            } else {
                materialNoResult = applyProductionSnapshot(item, source);
                salesOrderItemMapper.updateById(item);
                row = toApplyRow(header, item, source, true, true, false, materialNoResult);
                matchedCount++;
                updatedCount++;
            }
            switch (materialNoResult.status()) {
                case COPIED -> materialNoCopiedCount++;
                case IDEMPOTENT, NOT_APPLICABLE -> materialNoSkippedCount++;
                case CONFLICT -> materialNoConflictCount++;
            }
            resp.getRows().add(row);
        }

        updateHeaderSummaries(headers, items);
        resp.setMatchedItemCount(matchedCount);
        resp.setUnmatchedItemCount(unmatchedCount);
        resp.setSkippedItemCount(skippedCount);
        resp.setUpdatedItemCount(updatedCount);
        resp.setMaterialNoCopiedCount(materialNoCopiedCount);
        resp.setMaterialNoSkippedCount(materialNoSkippedCount);
        resp.setMaterialNoConflictCount(materialNoConflictCount);
        resp.setWarningMessage(unmatchedCount <= 0 ? "" : "未匹配历史生产快照的销售订单零件数：" + unmatchedCount);
        resp.setSuccess(true);
        resp.setMessage("销售订单导入自动快照补全完成，匹配 " + matchedCount + " 个，未匹配 " + unmatchedCount
                + " 个，跳过 " + skippedCount + " 个；物料号补全 " + materialNoCopiedCount
                + " 个，物料号跳过 " + materialNoSkippedCount + " 个，物料号冲突 "
                + materialNoConflictCount + " 个。");
        log.info("Sales order auto snapshot completion completed orderCount={} itemCount={} matched={} unmatched={} skipped={} "
                        + "materialNoCopied={} materialNoSkipped={} materialNoConflict={} durationMs={}",
                headers.size(), items.size(), matchedCount, unmatchedCount, skippedCount,
                materialNoCopiedCount, materialNoSkippedCount, materialNoConflictCount,
                System.currentTimeMillis() - startedAt);
        return resp;
    }

    private SalesOrderHeader requireSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectById(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderScope(header);
        return header;
    }

    public SalesOrderHeader requireMatchableSalesOrder(Long salesOrderId) {
        SalesOrderHeader header = requireSalesOrder(salesOrderId);
        assertMatchableSalesOrder(header);
        return header;
    }

    private List<SalesOrderItem> loadApplyItems(Long salesOrderId) {
        LinkedHashMap<Long, SalesOrderItem> result = new LinkedHashMap<>();
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId);
        query.orderByAsc("sales_order_id").orderByAsc("line_no").orderByAsc("id");
        List<SalesOrderItem> candidates = safeList(salesOrderItemMapper.selectList(query));
        for (SalesOrderItem candidate : candidates) {
            if (candidate == null || candidate.getId() == null || result.containsKey(candidate.getId())) {
                continue;
            }
            SalesOrderItem locked = salesOrderItemMapper.selectByIdForUpdate(candidate.getId());
            if (locked != null) {
                result.put(locked.getId(), locked);
            }
        }
        return new ArrayList<>(result.values());
    }

    private Map<Long, SalesOrderHeader> loadLockedAuthorizedHeaders(List<SalesOrderItem> items) {
        LinkedHashSet<Long> headerIds = items.stream()
                .map(SalesOrderItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SalesOrderHeader> result = new LinkedHashMap<>();
        for (Long headerId : headerIds) {
            SalesOrderHeader header = headerMapper.selectByIdForUpdate(headerId);
            if (header == null) {
                throw new RuntimeException("销售订单不存在：" + headerId);
            }
            assertSalesOrderScope(header);
            assertMatchableSalesOrder(header);
            result.put(headerId, header);
        }
        return result;
    }

    private Map<String, ItemQuoteVersion> releasedProductionSnapshotsByKey(List<SalesOrderItem> items) {
        List<PartVersionKey> keys = partVersionKeys(items);
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemQuoteVersion> result = new LinkedHashMap<>();
        for (int start = 0; start < keys.size(); start += REPEAT_SOURCE_KEY_BATCH_SIZE) {
            int end = Math.min(start + REPEAT_SOURCE_KEY_BATCH_SIZE, keys.size());
            List<ItemQuoteVersion> sources = itemQuoteVersionMapper.selectLatestReleasedRepeatSources(null, keys.subList(start, end));
            for (ItemQuoteVersion source : sources == null ? List.<ItemQuoteVersion>of() : sources) {
                if (source == null || isBlank(source.getPartNo()) || isBlank(source.getVersion())
                        || !hasReusableProductionSnapshot(source)) {
                    continue;
                }
                String key = itemKey(source.getPartNo(), source.getVersion());
                result.merge(key, source, this::newerSource);
            }
        }
        return result;
    }

    private MaterialNoReuseResult applyProductionSnapshot(SalesOrderItem item, ItemQuoteVersion source) {
        String engineeringMaterialName = resolveEngineeringMaterialName(source);
        String materialShape = resolveMaterialShape(source);
        copyEngineeringMaterialIdentityIfMissing(item, source);
        if (isBlank(item.getEngineeringMaterialName()) && !isBlank(engineeringMaterialName)) {
            item.setEngineeringMaterialName(engineeringMaterialName);
        }
        if (isBlank(item.getMaterialShape()) && !isBlank(materialShape)) {
            item.setMaterialShape(materialShape);
        }
        if (item.getMaterialGrossWeight() == null) {
            item.setMaterialGrossWeight(source.getMaterialGrossWeight());
        }
        if (item.getPartNetWeight() == null) {
            item.setPartNetWeight(source.getPartNetWeight());
        }
        if (shouldFillQuoteMode(item, source)) {
            item.setQuoteMode(source.getQuoteMode());
        }
        if (item.getAssemblyFee() == null) {
            item.setAssemblyFee(toBigDecimal(source.getAssemblyFee()));
        }
        if (item.getBaseCost() == null) {
            item.setBaseCost(toBigDecimal(source.getBaseCost()));
        }
        if (isBlank(item.getItemType())) {
            item.setItemType(QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? "组合件" : "普通件");
        }
        if (isMissingSnapshot(item.getMaterialData()) && !isMissingSnapshot(source.getMaterialData())) {
            item.setMaterialData(deepCopySnapshot(source.getMaterialData()));
        }
        if (isMissingSnapshot(item.getProcessRoute()) && !isMissingSnapshot(source.getProcessRoute())) {
            item.setProcessRoute(processRouteSnapshotService.copySnapshot(source.getProcessRoute()));
        }
        if (isMissingSnapshot(item.getFeeData()) && !isMissingSnapshot(source.getFeeData())) {
            item.setFeeData(deepCopySnapshot(source.getFeeData()));
        }
        if (!isBlank(item.getDrawingUrl())) {
            item.setHasDrawing(true);
        }
        item.setMatchedRfqId(source.getRfqId());
        item.setMatchedItemId(source.getId());
        item.setMatchStatus(MATCHED);
        MaterialNoReuseResult materialNoResult;
        try {
            materialNoResult = confirmedMaterialNoReuseService.copyToSalesOrderItem(item, source);
        } catch (ConfirmedMaterialNoReuseService.MaterialNoConflictException ex) {
            materialNoResult = new MaterialNoReuseResult(MaterialNoReuseResult.Status.CONFLICT);
            item.setMatchMessage("已补全历史生产快照：RFQ " + source.getRfqId() + " / item " + source.getId()
                    + "；物料号冲突，未覆盖已有物料号");
        }
        if (materialNoResult.status() != MaterialNoReuseResult.Status.CONFLICT) {
            item.setMatchMessage("已补全历史生产快照：RFQ " + source.getRfqId() + " / item " + source.getId());
        }
        item.setAssemblyRelationStatus(resolveAssemblyRelationStatus(item, source));
        item.setUpdatedAt(LocalDateTime.now());
        return materialNoResult;
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
        return safeList(historyAssemblyRelationMapper.selectList(query));
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
        return safeList(itemQuoteVersionMapper.selectBatchIds(ids)).stream()
                .filter(source -> source != null && source.getId() != null)
                .collect(Collectors.toMap(ItemQuoteVersion::getId, source -> source, (left, ignored) -> left));
    }

    private void deleteSalesOrderAssemblyRelations(Long parentItemId) {
        if (parentItemId == null) {
            return;
        }
        QueryWrapper<SalesOrderItemAssemblyRelation> query = new QueryWrapper<>();
        query.eq("parent_item_id", parentItemId);
        salesOrderAssemblyRelationMapper.delete(query);
    }

    private ItemQuoteVersion newerSource(ItemQuoteVersion left, ItemQuoteVersion right) {
        Integer leftId = left == null ? null : left.getId();
        Integer rightId = right == null ? null : right.getId();
        if (leftId == null) {
            return right;
        }
        if (rightId == null) {
            return left;
        }
        return rightId > leftId ? right : left;
    }

    private boolean hasReusableProductionSnapshot(ItemQuoteVersion source) {
        return source != null
                && isAutoReusableRepeatStatus(source.getStatus())
                && source.getTaxIncludedUnitPrice() != null
                && !isMissingSnapshot(source.getMaterialData())
                && !isMissingSnapshot(source.getProcessRoute());
    }

    private boolean isAutoReusableRepeatStatus(String status) {
        return status != null && AUTO_REUSABLE_REPEAT_STATUSES.contains(status.trim());
    }

    private List<PartVersionKey> partVersionKeys(List<SalesOrderItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<PartVersionKey> result = new ArrayList<>();
        for (SalesOrderItem item : items) {
            if (item == null || isBlank(item.getPartNo()) || isBlank(item.getVersion())) {
                continue;
            }
            String key = itemKey(item.getPartNo(), item.getVersion());
            if (seen.add(key)) {
                result.add(new PartVersionKey(item.getPartNo().trim(), item.getVersion().trim()));
            }
        }
        return result;
    }

    private SalesOrderAutoSnapshotCompletionRow toApplyRow(
            SalesOrderHeader header,
            SalesOrderItem item,
            ItemQuoteVersion source,
            boolean matched,
            boolean updated,
            boolean skipped,
            MaterialNoReuseResult materialNoResult) {
        SalesOrderAutoSnapshotCompletionRow row = new SalesOrderAutoSnapshotCompletionRow();
        row.setSalesOrderId(item.getSalesOrderId());
        row.setSalesOrderItemId(item.getId());
        row.setSalesOrderNo(header == null ? null : header.getSalesOrderNo());
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setMatched(matched);
        row.setUpdated(updated);
        row.setSkipped(skipped);
        row.setSourceRfqId(source == null ? null : source.getRfqId());
        row.setSourceItemId(source == null ? null : source.getId());
        row.setMatchStatus(item.getMatchStatus());
        row.setMatchMessage(item.getMatchMessage());
        row.setMaterialNoReuseStatus(materialNoResult == null ? null : materialNoResult.status().name());
        row.setMaterialNoReuseMessage(materialNoReuseMessage(materialNoResult));
        row.setAssemblyRelationStatus(defaultText(item.getAssemblyRelationStatus(), ASSEMBLY_RELATION_NOT_APPLICABLE));
        return row;
    }

    private String materialNoReuseMessage(MaterialNoReuseResult result) {
        if (result == null || result.status() == null) {
            return null;
        }
        return switch (result.status()) {
            case COPIED -> "已补全 MES 已确认物料号";
            case IDEMPOTENT -> "物料号已一致，跳过覆盖";
            case NOT_APPLICABLE -> "来源未确认，跳过物料号复用";
            case CONFLICT -> "目标已有不同物料号，未覆盖";
        };
    }

    private boolean shouldSkipApply(SalesOrderItem item) {
        return QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || (item.getRemainingQty() != null && item.getRemainingQty() == 0);
    }

    private void markSkipped(SalesOrderItem item) {
        item.setMatchStatus(SKIPPED);
        item.setMatchMessage("零件已生成提单或无剩余数量，跳过补全");
        item.setUpdatedAt(LocalDateTime.now());
    }

    private void markUnmatched(SalesOrderItem item) {
        item.setMatchedRfqId(null);
        item.setMatchedItemId(null);
        item.setMatchStatus(UNMATCHED);
        item.setMatchMessage("未命中历史已提单生产快照");
        item.setAssemblyRelationStatus(ASSEMBLY_RELATION_NOT_APPLICABLE);
        item.setUpdatedAt(LocalDateTime.now());
    }

    private void updateHeaderSummaries(Map<Long, SalesOrderHeader> headers, List<SalesOrderItem> affectedItems) {
        for (SalesOrderHeader header : headers.values()) {
            List<SalesOrderItem> allItems = mergeAffectedItems(listSalesOrderItems(header.getId()), affectedItems, header.getId());
            long matched = allItems.stream().filter(item -> MATCHED.equals(item.getMatchStatus())).count();
            long unmatched = allItems.stream().filter(item -> UNMATCHED.equals(item.getMatchStatus())).count();
            header.setTotalItemCount(allItems.size());
            header.setMatchedItemCount((int) matched);
            header.setUnmatchedItemCount((int) unmatched);
            header.setWarningMessage(unmatched <= 0 ? "" : "未匹配历史生产快照的销售订单零件数：" + unmatched);
            header.setUpdatedAt(LocalDateTime.now());
            headerMapper.updateById(header);
        }
    }

    private List<SalesOrderItem> listSalesOrderItems(Long salesOrderId) {
        if (salesOrderId == null) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("line_no").orderByAsc("id");
        return safeList(salesOrderItemMapper.selectList(query));
    }

    private List<SalesOrderItem> mergeAffectedItems(List<SalesOrderItem> persistedItems, List<SalesOrderItem> affectedItems, Long salesOrderId) {
        LinkedHashMap<Long, SalesOrderItem> rows = new LinkedHashMap<>();
        for (SalesOrderItem item : safeList(persistedItems)) {
            if (item != null && item.getId() != null) {
                rows.put(item.getId(), item);
            }
        }
        for (SalesOrderItem item : safeList(affectedItems)) {
            if (item != null && item.getId() != null && Objects.equals(item.getSalesOrderId(), salesOrderId)) {
                rows.put(item.getId(), item);
            }
        }
        return new ArrayList<>(rows.values());
    }

    private void assertMatchableSalesOrder(SalesOrderHeader header) {
        if (header == null || !QuoteStatusWorkflow.QUOTED.equals(header.getStatus())) {
            throw new RuntimeException("销售订单导入自动快照补全只能处理" + QuoteStatusWorkflow.QUOTED + "销售订单，请确认订单状态后重试");
        }
    }

    private void assertSalesOrderScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private String itemKey(String partNo, String version) {
        return defaultText(partNo, "").trim() + "||" + defaultText(version, "").trim();
    }

    private boolean shouldFillQuoteMode(SalesOrderItem item, ItemQuoteVersion source) {
        if (source == null || isBlank(source.getQuoteMode())) {
            return false;
        }
        String current = item == null ? null : item.getQuoteMode();
        return isBlank(current)
                || ("NORMAL".equalsIgnoreCase(current.trim())
                && QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode().trim()));
    }

    private boolean isMissingSnapshot(Object value) {
        return jsonSnapshotCopyService().isMissingSnapshot(value);
    }

    private Object deepCopySnapshot(Object value) {
        return jsonSnapshotCopyService().deepCopySnapshot(value);
    }

    private JsonSnapshotCopyService jsonSnapshotCopyService() {
        if (jsonSnapshotCopyService == null) {
            jsonSnapshotCopyService = new JsonSnapshotCopyService();
        }
        return jsonSnapshotCopyService;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveEngineeringMaterialName(ItemQuoteVersion source) {
        if (source == null) {
            return "";
        }
        if (!isBlank(source.getEngineeringMaterialName())) {
            return source.getEngineeringMaterialName();
        }
        Object materialName = MaterialDataResolver.normalizeMap(source.getMaterialData()).get("name");
        return materialName == null ? "" : materialName.toString().trim();
    }

    private String resolveMaterialShape(ItemQuoteVersion source) {
        return MaterialDataResolver.materialShape(source);
    }

    private void copyEngineeringMaterialIdentityIfMissing(SalesOrderItem item, ItemQuoteVersion source) {
        if (isBlank(item.getEngineeringMaterialCode()) && !isBlank(MaterialDataResolver.materialCode(source))) {
            item.setEngineeringMaterialCode(MaterialDataResolver.materialCode(source));
        }
        if (isBlank(item.getEngineeringMaterialTypeCode()) && !isBlank(MaterialDataResolver.materialTypeCode(source))) {
            item.setEngineeringMaterialTypeCode(MaterialDataResolver.materialTypeCode(source));
        }
        if (isBlank(item.getEngineeringMaterialTypeName()) && !isBlank(MaterialDataResolver.materialTypeName(source))) {
            item.setEngineeringMaterialTypeName(MaterialDataResolver.materialTypeName(source));
        }
        if (isBlank(item.getEngineeringMaterialCategory()) && !isBlank(MaterialDataResolver.materialCategory(source))) {
            item.setEngineeringMaterialCategory(MaterialDataResolver.materialCategory(source));
        }
    }
}
