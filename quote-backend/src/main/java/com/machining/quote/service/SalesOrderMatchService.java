package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderMatchItemRow;
import com.machining.quote.dto.SalesOrderMatchOrderRow;
import com.machining.quote.dto.SalesOrderMatchPreviewReq;
import com.machining.quote.dto.SalesOrderMatchPreviewResp;
import com.machining.quote.dto.SalesOrderMatchPreviewRow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderMatchService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 200;
    private static final String SOURCE_TYPE_SALES_ORDER = "SALES_ORDER";
    private static final String MATCHED = "已匹配";
    private static final String UNMATCHED = "未匹配";
    private static final String MATCH_STATUS_PENDING = "待匹配";
    private static final String DISCARDED = "废弃";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;
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
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        applyOrderFilters(query, customerName, keyword, status, matchStatus);
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

    public SalesOrderMatchPreviewResp preview(SalesOrderMatchPreviewReq req) {
        SalesOrderMatchPreviewResp resp = new SalesOrderMatchPreviewResp();
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
            SalesOrderHeader header = requireSalesOrder(headerId);
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
        row.setQty(item.getQty());
        row.setCurrentMatchStatus(defaultText(item.getMatchStatus(), MATCH_STATUS_PENDING));
        row.setAssemblyRelationStatus(defaultText(item.getAssemblyRelationStatus(), "不适用"));
        if (source == null) {
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
        row.setEngineeringMaterialName(source.getEngineeringMaterialName());
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
        putIfCurrentBlank(fields, "materialName", current.getMaterialName(), source.getMaterialName());
        putIfCurrentBlank(fields, "surfaceTreatment", current.getSurfaceTreatment(), source.getSurfaceTreatment());
        putSnapshot(fields, "engineeringMaterialName", current.getEngineeringMaterialName(), source.getEngineeringMaterialName());
        putSnapshot(fields, "materialGrossWeight", current.getMaterialGrossWeight(), source.getMaterialGrossWeight());
        putSnapshot(fields, "partNetWeight", current.getPartNetWeight(), source.getPartNetWeight());
        putSnapshot(fields, "quoteMode", current.getQuoteMode(), source.getQuoteMode());
        putSnapshot(fields, "itemType", current.getItemType(), QUOTE_MODE_ASSEMBLY.equalsIgnoreCase(source.getQuoteMode()) ? "组合件" : "普通件");
        putSnapshot(fields, "assemblyFee", current.getAssemblyFee(), toBigDecimal(source.getAssemblyFee()));
        putSnapshot(fields, "baseCost", current.getBaseCost(), toBigDecimal(source.getBaseCost()));
        if (isBlank(current.getDrawingUrl()) && !isBlank(source.getDrawingUrl())) {
            fields.put("drawingUrl", Map.of("current", "", "source", source.getDrawingUrl()));
        }
        fields.put("materialSnapshotAvailable", source.getMaterialData() != null);
        fields.put("processSnapshotAvailable", source.getProcessRoute() != null);
        fields.put("feeSnapshotAvailable", source.getFeeData() != null);
        return fields;
    }

    private Map<String, Object> protectedFields(SalesOrderItem current, ItemQuoteVersion source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (!isBlank(current.getMaterialName())) {
            fields.put("materialName", Map.of("current", current.getMaterialName(), "source", defaultText(source.getMaterialName(), "")));
        }
        if (!isBlank(current.getSurfaceTreatment())) {
            fields.put("surfaceTreatment", Map.of("current", current.getSurfaceTreatment(), "source", defaultText(source.getSurfaceTreatment(), "")));
        }
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

    private void putIfCurrentBlank(Map<String, Object> target, String field, String current, String source) {
        if (isBlank(current) && !isBlank(source)) {
            target.put(field, Map.of("current", "", "source", source));
        }
    }

    private void putSnapshot(Map<String, Object> target, String field, Object current, Object source) {
        if (source != null && !Objects.equals(current, source)) {
            target.put(field, Map.of("current", current == null ? "" : current, "source", source));
        }
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
