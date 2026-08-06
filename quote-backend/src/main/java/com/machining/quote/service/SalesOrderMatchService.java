package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderMatchApplyReq;
import com.machining.quote.dto.SalesOrderMatchApplyResp;
import com.machining.quote.dto.SalesOrderMatchApplyRow;
import com.machining.quote.dto.SalesOrderMatchItemRow;
import com.machining.quote.dto.SalesOrderMatchOrderRow;
import com.machining.quote.dto.SalesOrderMatchPreviewReq;
import com.machining.quote.dto.SalesOrderMatchPreviewResp;
import com.machining.quote.dto.SalesOrderMatchPreviewRow;
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
public class SalesOrderMatchService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderMatchService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 200;
    private static final String SOURCE_TYPE_SALES_ORDER = "SALES_ORDER";
    private static final String ACTION = "SALES_ORDER_MATCH_APPLY";
    private static final String TRIGGER_MANUAL = "MANUAL";
    private static final String TRIGGER_IMPORT_AUTO = "IMPORT_AUTO";
    private static final String MATCHED = "已匹配";
    private static final String UNMATCHED = "未匹配";
    private static final String SKIPPED = "跳过";
    private static final String MATCH_STATUS_PENDING = "待匹配";
    private static final String DISCARDED = "废弃";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";
    private static final String ASSEMBLY_RELATION_NOT_APPLICABLE = "不适用";
    private static final String ASSEMBLY_RELATION_UNSPECIFIED = "未指定父子关系";
    private static final String ASSEMBLY_RELATION_SPECIFIED = "已指定父子关系";

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
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (dataScopeService == null) {
            return List.of();
        }
        return dataScopeService.listCustomerOptions(field, keyword);
    }

    public PageResult<SalesOrderMatchOrderRow> listOrders(
            String customerName,
            String keyword,
            String status,
            String matchStatus,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String effectiveStatus = normalizeMatchableOrderListStatus(status);
        if (effectiveStatus == null) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        applyOrderFilters(query, customerName, keyword, effectiveStatus, matchStatus);
        Long total = headerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(headerMapper.selectList(query).stream()
                .map(this::toOrderRow)
                .toList(), total, safePage, safePageSize);
    }

    public PageResult<SalesOrderMatchItemRow> listItems(Long salesOrderId, String matchStatus, Integer page, Integer pageSize) {
        SalesOrderHeader header = requireSalesOrder(salesOrderId);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", header.getId());
        applyItemMatchFilter(query, matchStatus);
        Long total = salesOrderItemMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByAsc("line_no").orderByAsc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(salesOrderItemMapper.selectList(query).stream()
                .map(this::toItemRow)
                .toList(), total, safePage, safePageSize);
    }

    public SalesOrderMatchPreviewResp previewOrder(Long salesOrderId) {
        SalesOrderHeader header = requireMatchableSalesOrder(salesOrderId);
        SalesOrderMatchPreviewReq req = new SalesOrderMatchPreviewReq();
        req.setSalesOrderIds(List.of(header.getId()));
        req.setSalesOrderItemIds(List.of());
        req.setOnlyUnmatched(false);
        return preview(req);
    }

    public SalesOrderMatchPreviewResp preview(SalesOrderMatchPreviewReq req) {
        SalesOrderMatchPreviewResp resp = new SalesOrderMatchPreviewResp();
        validateMatchableOrderIds(req == null ? null : req.getSalesOrderIds());
        List<SalesOrderItem> items = loadPreviewItems(req);
        if (items.isEmpty()) {
            return resp;
        }
        Map<Long, SalesOrderHeader> headers = loadAuthorizedHeaders(items);
        Map<String, ItemQuoteVersion> sourceByKey = releasedProductionSnapshotsByKey(items);
        int matchedCount = 0;
        int unmatchedCount = 0;
        for (SalesOrderItem item : items) {
            SalesOrderHeader header = headers.get(item.getSalesOrderId());
            ItemQuoteVersion source = sourceByKey.get(itemKey(item.getPartNo(), item.getVersion()));
            SalesOrderMatchPreviewRow row = toPreviewRow(header, item, source);
            resp.getRows().add(row);
            if (Boolean.TRUE.equals(row.getMatched())) {
                matchedCount++;
            } else {
                unmatchedCount++;
            }
        }
        resp.setTotalItemCount(resp.getRows().size());
        resp.setMatchedItemCount(matchedCount);
        resp.setUnmatchedItemCount(unmatchedCount);
        resp.setWarningMessage(unmatchedCount <= 0 ? "" : "未匹配历史生产快照的销售订单零件数：" + unmatchedCount);
        return resp;
    }

    @Transactional
    public SalesOrderMatchApplyResp apply(SalesOrderMatchApplyReq req) {
        return applyInternal(req, TRIGGER_MANUAL);
    }

    @Transactional
    public SalesOrderMatchApplyResp applyImportedOrder(Long salesOrderId) {
        SalesOrderHeader header = requireMatchableSalesOrder(salesOrderId);
        SalesOrderMatchApplyReq req = new SalesOrderMatchApplyReq();
        req.setSalesOrderIds(List.of(header.getId()));
        req.setSalesOrderItemIds(List.of());
        req.setApplyAllMatchedInSelectedOrders(false);
        return applyInternal(req, TRIGGER_IMPORT_AUTO);
    }

    private SalesOrderMatchApplyResp applyInternal(SalesOrderMatchApplyReq req, String trigger) {
        long startedAt = System.currentTimeMillis();
        validateMatchableOrderIds(req == null ? null : req.getSalesOrderIds());
        List<SalesOrderItem> items = loadApplyItems(req);
        SalesOrderMatchApplyResp resp = new SalesOrderMatchApplyResp();
        resp.setTotalItemCount(items.size());
        if (items.isEmpty()) {
            resp.setSuccess(true);
            resp.setMessage("没有需要补全的销售订单零件");
            return resp;
        }
        log.info("Sales order match apply started itemCount={}", items.size());

        Map<Long, SalesOrderHeader> headers = loadLockedAuthorizedHeaders(items);
        Map<String, ItemQuoteVersion> sourceByKey = releasedProductionSnapshotsByKey(items);
        int matchedCount = 0;
        int unmatchedCount = 0;
        int skippedCount = 0;
        int updatedCount = 0;
        for (SalesOrderItem item : items) {
            SalesOrderHeader header = headers.get(item.getSalesOrderId());
            ItemQuoteVersion source = sourceByKey.get(itemKey(item.getPartNo(), item.getVersion()));
            SalesOrderMatchApplyRow row;
            if (shouldSkipApply(item)) {
                markSkipped(item);
                row = toApplyRow(header, item, source, false, false, true);
                skippedCount++;
            } else if (source == null) {
                markUnmatched(item);
                salesOrderItemMapper.updateById(item);
                row = toApplyRow(header, item, null, false, true, false);
                unmatchedCount++;
                updatedCount++;
            } else {
                applyProductionSnapshot(item, source, req);
                salesOrderItemMapper.updateById(item);
                row = toApplyRow(header, item, source, true, true, false);
                matchedCount++;
                updatedCount++;
            }
            resp.getRows().add(row);
        }

        updateHeaderSummaries(headers, items);
        resp.setMatchedItemCount(matchedCount);
        resp.setUnmatchedItemCount(unmatchedCount);
        resp.setSkippedItemCount(skippedCount);
        resp.setUpdatedItemCount(updatedCount);
        resp.setWarningMessage(unmatchedCount <= 0 ? "" : "未匹配历史生产快照的销售订单零件数：" + unmatchedCount);
        resp.setSuccess(true);
        resp.setMessage("销售匹配补全完成，匹配 " + matchedCount + " 个，未匹配 " + unmatchedCount + " 个，跳过 " + skippedCount + " 个。");
        recordApplyAudit(headers, resp, startedAt, trigger);
        log.info("Sales order match apply completed trigger={} orderCount={} itemCount={} matched={} unmatched={} skipped={} durationMs={}",
                trigger, headers.size(), items.size(), matchedCount, unmatchedCount, skippedCount, System.currentTimeMillis() - startedAt);
        return resp;
    }

    @Transactional
    public SalesOrderMatchApplyResp applyOrder(Long salesOrderId) {
        SalesOrderHeader header = requireMatchableSalesOrder(salesOrderId);
        SalesOrderMatchApplyReq req = new SalesOrderMatchApplyReq();
        req.setSalesOrderIds(List.of(header.getId()));
        req.setSalesOrderItemIds(List.of());
        req.setApplyAllMatchedInSelectedOrders(false);
        return apply(req);
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

    private List<SalesOrderItem> loadApplyItems(SalesOrderMatchApplyReq req) {
        List<Long> itemIds = normalizeIds(req == null ? null : req.getSalesOrderItemIds());
        List<Long> orderIds = normalizeIds(req == null ? null : req.getSalesOrderIds());
        LinkedHashMap<Long, SalesOrderItem> result = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
            if (item == null) {
                throw new RuntimeException("销售订单零件不存在：" + itemId);
            }
            result.put(item.getId(), item);
        }
        if (!orderIds.isEmpty()) {
            QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
            query.in("sales_order_id", orderIds);
            if (req != null && req.isApplyAllMatchedInSelectedOrders()) {
                query.and(wrapper -> wrapper.isNull("match_status").or().ne("match_status", MATCHED));
            }
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

    private List<SalesOrderItem> loadPreviewItems(SalesOrderMatchPreviewReq req) {
        List<Long> orderIds = normalizeIds(req == null ? null : req.getSalesOrderIds());
        List<Long> itemIds = normalizeIds(req == null ? null : req.getSalesOrderItemIds());
        boolean onlyUnmatched = req != null && req.isOnlyUnmatched();
        if (orderIds.isEmpty() && itemIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        if (!orderIds.isEmpty()) {
            query.in("sales_order_id", orderIds);
        }
        if (!itemIds.isEmpty()) {
            if (!orderIds.isEmpty()) {
                query.and(wrapper -> wrapper.in("id", itemIds));
            } else {
                query.in("id", itemIds);
            }
        }
        if (onlyUnmatched) {
            query.and(wrapper -> wrapper.isNull("match_status").or().ne("match_status", MATCHED));
        }
        query.orderByAsc("sales_order_id").orderByAsc("line_no").orderByAsc("id");
        return salesOrderItemMapper.selectList(query);
    }

    private Map<Long, SalesOrderHeader> loadAuthorizedHeaders(List<SalesOrderItem> items) {
        LinkedHashSet<Long> headerIds = items.stream()
                .map(SalesOrderItem::getSalesOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SalesOrderHeader> result = new LinkedHashMap<>();
        for (Long headerId : headerIds) {
            SalesOrderHeader header = requireMatchableSalesOrder(headerId);
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
                if (source == null || isBlank(source.getPartNo()) || isBlank(source.getVersion())) {
                    continue;
                }
                String key = itemKey(source.getPartNo(), source.getVersion());
                result.merge(key, source, this::newerSource);
            }
        }
        return result;
    }

    private void applyProductionSnapshot(SalesOrderItem item, ItemQuoteVersion source, SalesOrderMatchApplyReq req) {
        String engineeringMaterialName = resolveEngineeringMaterialName(source);
        String materialShape = resolveMaterialShape(source);
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
        if (isBlank(item.getQuoteMode())) {
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
        if (item.getMaterialData() == null) {
            item.setMaterialData(source.getMaterialData());
        }
        if (item.getProcessRoute() == null) {
            item.setProcessRoute(source.getProcessRoute());
        }
        if (item.getFeeData() == null) {
            item.setFeeData(source.getFeeData());
        }
        if (!isBlank(item.getDrawingUrl())) {
            item.setHasDrawing(true);
        }
        item.setMatchedRfqId(source.getRfqId());
        item.setMatchedItemId(source.getId());
        item.setMatchStatus(MATCHED);
        item.setMatchMessage("已补全历史生产快照：RFQ " + source.getRfqId() + " / item " + source.getId());
        item.setAssemblyRelationStatus(resolveAssemblyRelationStatus(item, source));
        item.setUpdatedAt(LocalDateTime.now());
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

    private SalesOrderMatchPreviewRow toPreviewRow(SalesOrderHeader header, SalesOrderItem item, ItemQuoteVersion source) {
        SalesOrderMatchPreviewRow row = new SalesOrderMatchPreviewRow();
        row.setSalesOrderId(item.getSalesOrderId());
        row.setSalesOrderItemId(item.getId());
        row.setSalesOrderNo(header == null ? null : header.getSalesOrderNo());
        row.setCustomerOrderNo(firstNonBlank(item.getCustomerOrderNo(), header == null ? null : header.getCustomerOrderNo()));
        row.setInternalProjectNo(firstNonBlank(item.getInternalProjectNo(), header == null ? null : header.getInternalProjectNo()));
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setLineNo(item.getLineNo());
        row.setQty(item.getQty());
        row.setCurrentMatchStatus(defaultText(item.getMatchStatus(), MATCH_STATUS_PENDING));
        row.setAssemblyRelationStatus(defaultText(item.getAssemblyRelationStatus(), "不适用"));
        if (source == null) {
            row.setMaterialShape(item.getMaterialShape());
            row.setMatched(false);
            row.setWarningMessage("未命中历史已提单生产快照");
            return row;
        }
        row.setMatched(true);
        row.setSourceRfqId(source.getRfqId());
        row.setSourceItemId(source.getId());
        row.setSourcePartNo(source.getPartNo());
        row.setSourceVersion(source.getVersion());
        row.setMaterialName(source.getMaterialName());
        row.setEngineeringMaterialName(resolveEngineeringMaterialName(source));
        row.setMaterialShape(resolveMaterialShape(source));
        row.setSurfaceTreatment(source.getSurfaceTreatment());
        row.setQuoteMode(source.getQuoteMode());
        row.setItemType(QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? "组合件" : "普通件");
        row.setWarningMessage(buildWarningMessage(item, source));
        row.setChangedFields(changedFields(item, source));
        row.setProtectedFields(protectedFields(item, source));
        return row;
    }

    private Map<String, Object> changedFields(SalesOrderItem current, ItemQuoteVersion source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        putSnapshot(fields, "engineeringMaterialName", current.getEngineeringMaterialName(), resolveEngineeringMaterialName(source));
        putSnapshot(fields, "materialShape", current.getMaterialShape(), resolveMaterialShape(source));
        putSnapshot(fields, "materialGrossWeight", current.getMaterialGrossWeight(), source.getMaterialGrossWeight());
        putSnapshot(fields, "partNetWeight", current.getPartNetWeight(), source.getPartNetWeight());
        putSnapshot(fields, "quoteMode", current.getQuoteMode(), source.getQuoteMode());
        putSnapshot(fields, "itemType", current.getItemType(), QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? "组合件" : "普通件");
        putSnapshot(fields, "assemblyFee", current.getAssemblyFee(), toBigDecimal(source.getAssemblyFee()));
        putSnapshot(fields, "baseCost", current.getBaseCost(), toBigDecimal(source.getBaseCost()));
        fields.put("materialSnapshotAvailable", current.getMaterialData() == null && source.getMaterialData() != null);
        fields.put("processSnapshotAvailable", current.getProcessRoute() == null && source.getProcessRoute() != null);
        fields.put("feeSnapshotAvailable", current.getFeeData() == null && source.getFeeData() != null);
        return fields;
    }

    private Map<String, Object> protectedFields(SalesOrderItem current, ItemQuoteVersion source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("materialName", protectedText(current.getMaterialName(), source.getMaterialName()));
        fields.put("surfaceTreatment", protectedText(current.getSurfaceTreatment(), source.getSurfaceTreatment()));
        fields.put("drawingUrl", protectedText(current.getDrawingUrl(), source.getDrawingUrl()));
        fields.put("description", protectedText(current.getDescription(), source.getDescription()));
        fields.put("qty", current.getQty());
        fields.put("materialNo", defaultText(current.getMaterialNo(), ""));
        fields.put("taxIncludedUnitPrice", current.getTaxIncludedUnitPrice());
        fields.put("finalUnitPrice", current.getFinalUnitPrice());
        fields.put("customerOrderNo", defaultText(current.getCustomerOrderNo(), ""));
        fields.put("internalProjectNo", defaultText(current.getInternalProjectNo(), ""));
        return fields;
    }

    private String buildWarningMessage(SalesOrderItem current, ItemQuoteVersion source) {
        List<String> warnings = new ArrayList<>();
        if (!isBlank(current.getMaterialName()) && !Objects.equals(current.getMaterialName(), source.getMaterialName())) {
            warnings.add("模板材料已保护");
        }
        if (!isBlank(current.getSurfaceTreatment()) && !Objects.equals(current.getSurfaceTreatment(), source.getSurfaceTreatment())) {
            warnings.add("模板表面处理已保护");
        }
        if (!isBlank(current.getDrawingUrl()) && !Objects.equals(current.getDrawingUrl(), source.getDrawingUrl())) {
            warnings.add("销售订单图纸已保护");
        }
        return String.join("；", warnings);
    }

    private SalesOrderMatchOrderRow toOrderRow(SalesOrderHeader header) {
        SalesOrderMatchOrderRow row = new SalesOrderMatchOrderRow();
        row.setSalesOrderId(header.getId());
        row.setSourceType(SOURCE_TYPE_SALES_ORDER);
        row.setSalesOrderNo(header.getSalesOrderNo());
        row.setCustomerName(header.getCustomerName());
        row.setCustomerOrderNo(header.getCustomerOrderNo());
        row.setCustomerProjectNo(header.getCustomerProjectNo());
        row.setInternalProjectNo(header.getInternalProjectNo());
        row.setDocumentTypeCode(header.getDocumentTypeCode());
        row.setStatus(header.getStatus());
        row.setTotalItemCount(header.getTotalItemCount());
        row.setMatchedItemCount(header.getMatchedItemCount());
        row.setUnmatchedItemCount(header.getUnmatchedItemCount());
        row.setWarningMessage(header.getWarningMessage());
        row.setUpdatedAt(header.getUpdatedAt());
        return row;
    }

    private SalesOrderMatchItemRow toItemRow(SalesOrderItem item) {
        SalesOrderMatchItemRow row = new SalesOrderMatchItemRow();
        row.setSalesOrderId(item.getSalesOrderId());
        row.setSalesOrderItemId(item.getId());
        row.setLineNo(item.getLineNo());
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setDescription(item.getDescription());
        row.setQty(item.getQty());
        row.setMaterialName(item.getMaterialName());
        row.setSurfaceTreatment(item.getSurfaceTreatment());
        row.setMaterialNo(item.getMaterialNo());
        row.setTaxIncludedUnitPrice(item.getTaxIncludedUnitPrice());
        row.setFinalUnitPrice(item.getFinalUnitPrice());
        row.setCurrentMatchStatus(defaultText(item.getMatchStatus(), MATCH_STATUS_PENDING));
        row.setMatchMessage(item.getMatchMessage());
        row.setMatchedRfqId(item.getMatchedRfqId());
        row.setMatchedItemId(item.getMatchedItemId());
        row.setAssemblyRelationStatus(defaultText(item.getAssemblyRelationStatus(), "不适用"));
        row.setStatus(item.getStatus());
        return row;
    }

    private SalesOrderMatchApplyRow toApplyRow(
            SalesOrderHeader header,
            SalesOrderItem item,
            ItemQuoteVersion source,
            boolean matched,
            boolean updated,
            boolean skipped) {
        SalesOrderMatchApplyRow row = new SalesOrderMatchApplyRow();
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
        row.setAssemblyRelationStatus(defaultText(item.getAssemblyRelationStatus(), ASSEMBLY_RELATION_NOT_APPLICABLE));
        return row;
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

    private void recordApplyAudit(Map<Long, SalesOrderHeader> headers, SalesOrderMatchApplyResp resp, long startedAt, String trigger) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trigger", isBlank(trigger) ? TRIGGER_MANUAL : trigger);
        summary.put("salesOrderIds", new ArrayList<>(headers.keySet()));
        summary.put("salesOrderNos", headers.values().stream().map(SalesOrderHeader::getSalesOrderNo).toList());
        summary.put("selectedItemCount", resp.getTotalItemCount());
        summary.put("matchedItemCount", resp.getMatchedItemCount());
        summary.put("unmatchedItemCount", resp.getUnmatchedItemCount());
        summary.put("skippedItemCount", resp.getSkippedItemCount());
        summary.put("updatedItemCount", resp.getUpdatedItemCount());
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        Object targetId = headers.size() == 1 ? headers.keySet().iterator().next() : new ArrayList<>(headers.keySet()).toString();
        auditService.record(ACTION, "sales_order_header", targetId, null, summary);
    }

    private void applyOrderFilters(
            QueryWrapper<SalesOrderHeader> query,
            String customerName,
            String keyword,
            String status,
            String matchStatus) {
        if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
        } else {
            query.and(wrapper -> wrapper.ne("status", DISCARDED).or().isNull("status"));
        }
        if (!isBlank(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("sales_order_no", text)
                    .or()
                    .like("customer_order_no", text)
                    .or()
                    .like("internal_project_no", text)
                    .or()
                    .like("customer_project_no", text));
        }
        applyHeaderMatchFilter(query, matchStatus);
    }

    private String normalizeMatchableOrderListStatus(String status) {
        if (isBlank(status)) {
            return QuoteStatusWorkflow.QUOTED;
        }
        String normalized = status.trim();
        return QuoteStatusWorkflow.QUOTED.equals(normalized) ? normalized : null;
    }

    private void validateMatchableOrderIds(List<Long> salesOrderIds) {
        for (Long salesOrderId : normalizeIds(salesOrderIds)) {
            requireMatchableSalesOrder(salesOrderId);
        }
    }

    private void assertMatchableSalesOrder(SalesOrderHeader header) {
        if (header == null || !QuoteStatusWorkflow.QUOTED.equals(header.getStatus())) {
            throw new RuntimeException("销售匹配只能处理" + QuoteStatusWorkflow.QUOTED + "销售订单，请确认订单状态后重试");
        }
    }

    private void applyHeaderMatchFilter(QueryWrapper<SalesOrderHeader> query, String matchStatus) {
        if (isBlank(matchStatus)) {
            return;
        }
        String normalized = matchStatus.trim();
        if (MATCHED.equals(normalized)) {
            query.apply("COALESCE(total_item_count, 0) > 0 AND COALESCE(unmatched_item_count, 0) = 0");
        } else if (UNMATCHED.equals(normalized)) {
            query.apply("COALESCE(unmatched_item_count, 0) > 0");
        } else if (MATCH_STATUS_PENDING.equals(normalized)) {
            query.apply("COALESCE(matched_item_count, 0) = 0 AND COALESCE(unmatched_item_count, 0) = 0");
        }
    }

    private void applyItemMatchFilter(QueryWrapper<SalesOrderItem> query, String matchStatus) {
        if (!isBlank(matchStatus)) {
            query.eq("match_status", matchStatus.trim());
        }
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private void assertSalesOrderScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String itemKey(String partNo, String version) {
        return defaultText(partNo, "").trim() + "||" + defaultText(version, "").trim();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void putSnapshot(Map<String, Object> target, String field, Object current, Object source) {
        if (source == null || isPresent(current) || isBlankSource(source)) {
            return;
        }
        target.put(field, Map.of("current", "", "source", source));
    }

    private boolean isPresent(Object value) {
        if (value instanceof String text) {
            return !isBlank(text);
        }
        return value != null;
    }

    private boolean isBlankSource(Object value) {
        return value instanceof String text && isBlank(text);
    }

    private Map<String, Object> protectedText(String current, String source) {
        return Map.of("current", defaultText(current, ""), "source", defaultText(source, ""));
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
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
}
