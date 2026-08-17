package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementChangeDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementConfirmDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelParseResult;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementField;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementSummaryDTO;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesOrderU9ItemDataReplacementService {
    private static final String ACTION = "SALES_ORDER_U9_ITEM_DATA_REPLACEMENT";
    private static final String TARGET_TYPE = "sales_order_header";
    private static final String FINGERPRINT_CONFLICT_MESSAGE = "数据已发生变化，请重新预览";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_PENDING_CONFIRMATION = "结果待确认";
    private static final String STATUS_SYNCED = "已同步";

    @Autowired
    private SalesOrderU9ItemDataReplacementExcelParser excelParser;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private SalesOrderU9SyncService previewService;
    @Autowired(required = false)
    private AuditService auditService;

    public SalesOrderU9ItemDataReplacementPreviewDTO preview(Long salesOrderId, InputStream excelInput) {
        if (salesOrderId == null) {
            throw new IllegalArgumentException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectById(salesOrderId);
        if (header == null) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        assertDataScope(header);
        SalesOrderU9ItemDataReplacementExcelParseResult parsed = excelParser.parse(excelInput);
        List<SalesOrderItem> items = listItems(salesOrderId);
        return buildPreview(header, parsed, items);
    }

    @Transactional
    public SalesOrderU9ItemDataReplacementConfirmDTO confirm(
            Long salesOrderId,
            InputStream excelInput,
            String previewFingerprint) {
        long startedAt = System.currentTimeMillis();
        if (salesOrderId == null) {
            throw new IllegalArgumentException("销售订单 ID 不能为空");
        }
        if (previewFingerprint == null || previewFingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("预览指纹不能为空");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
        }
        assertDataScope(header);
        SalesOrderU9ItemDataReplacementExcelParseResult parsed = excelParser.parse(excelInput);
        List<SalesOrderItem> lockedItems = lockOrderItems(salesOrderId);
        SalesOrderU9ItemDataReplacementPreviewDTO currentPreview = buildPreview(header, parsed, lockedItems);
        if (!fingerprintsEqual(previewFingerprint.trim(), currentPreview.getPreviewFingerprint())) {
            throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
        }
        if (!currentPreview.isCanConfirm()) {
            throw new IllegalArgumentException("当前替换数据不能确认，请重新预览");
        }

        ApplyResult applied = applyConfirmation(header, lockedItems, currentPreview);
        SalesOrderU9ItemDataReplacementConfirmDTO result = new SalesOrderU9ItemDataReplacementConfirmDTO();
        result.setSuccess(true);
        result.setMessage("表格数据替换完成，已更新 " + applied.updatedCount() + " 行零件数据。");
        result.setSalesOrderId(header.getId());
        result.setSalesOrderNo(header.getSalesOrderNo());
        result.setUpdatedCount(applied.updatedCount());
        result.setChangedFields(new ArrayList<>(applied.changedFields()));
        result.setSummary(currentPreview.getSummary());
        result.setPreview(previewService.previewSalesOrder(header.getId()));
        auditSuccess(header, applied, currentPreview.getSummary(), System.currentTimeMillis() - startedAt);
        return result;
    }

    private SalesOrderU9ItemDataReplacementPreviewDTO buildPreview(
            SalesOrderHeader header,
            SalesOrderU9ItemDataReplacementExcelParseResult parsed,
            List<SalesOrderItem> items) {
        Map<ItemKey, List<SalesOrderItem>> candidatesByKey = groupCandidates(items);
        boolean locked = isLocked(header);
        List<SalesOrderU9ItemDataReplacementRowDTO> rows = new ArrayList<>();
        for (SalesOrderU9ItemDataReplacementExcelRowDTO excelRow : safeList(parsed.getRows())) {
            rows.add(previewRow(excelRow, candidatesByKey, locked, header.getU9SyncStatus()));
        }

        SalesOrderU9ItemDataReplacementPreviewDTO preview = new SalesOrderU9ItemDataReplacementPreviewDTO();
        preview.setSuccess(true);
        preview.setRecognizedFields(new ArrayList<>(safeList(parsed.getRecognizedFields())));
        preview.setRows(rows);
        preview.setSummary(summarize(rows));
        preview.setCanConfirm(preview.getSummary().getErrorCount() == 0
                && preview.getSummary().getChangedCount() > 0);
        preview.setPreviewFingerprint(fingerprint(header, parsed, rows, items));
        return preview;
    }

    private SalesOrderU9ItemDataReplacementRowDTO previewRow(
            SalesOrderU9ItemDataReplacementExcelRowDTO excelRow,
            Map<ItemKey, List<SalesOrderItem>> candidatesByKey,
            boolean locked,
            String u9SyncStatus) {
        SalesOrderU9ItemDataReplacementRowDTO row = baseRow(excelRow);
        if (excelRow.hasErrors()) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.ERROR);
            row.setReason(excelRow.getErrors().stream()
                    .map(SalesOrderU9ItemDataReplacementExcelErrorDTO::getMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("；")));
            return row;
        }

        List<SalesOrderItem> candidates = candidatesByKey.getOrDefault(
                new ItemKey(excelRow.getNormalizedPartNo(), excelRow.getNormalizedVersion()),
                List.of());
        if (excelRow.getLineNo() != null) {
            candidates = candidates.stream()
                    .filter(item -> Objects.equals(item.getLineNo(), excelRow.getLineNo()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.UNMATCHED);
            row.setReason("当前销售订单内未找到对应零件");
            return row;
        }
        if (candidates.size() > 1) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.ERROR);
            row.setReason("当前销售订单内存在多个相同图号和版本的零件，请提供行号");
            return row;
        }

        SalesOrderItem item = candidates.get(0);
        row.setSalesOrderItemId(item.getId());
        row.setLineNo(item.getLineNo());
        if (locked) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.SKIPPED);
            row.setReason("U9 状态为" + trimToEmpty(u9SyncStatus) + "，不能替换销售订单零件行");
            return row;
        }
        String quantityError = quantityError(item, excelRow.getValues().get("qty"));
        if (quantityError != null) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.ERROR);
            row.setReason(quantityError);
            return row;
        }

        row.setChanges(changes(item, excelRow.getValues()));
        if (row.getChanges().isEmpty()) {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.UNCHANGED);
        } else {
            row.setStatus(SalesOrderU9ItemDataReplacementRowStatus.CHANGED);
        }
        row.setReason("");
        return row;
    }

    private List<SalesOrderU9ItemDataReplacementChangeDTO> changes(
            SalesOrderItem item,
            Map<String, Object> values) {
        List<SalesOrderU9ItemDataReplacementChangeDTO> changes = new ArrayList<>();
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        for (SalesOrderU9ItemDataReplacementField field : SalesOrderU9ItemDataReplacementField.replaceableFields()) {
            String propertyName = field.getPropertyName();
            if (!safeValues.containsKey(propertyName)) {
                continue;
            }
            Object beforeValue = currentValue(item, field);
            Object afterValue = safeValues.get(propertyName);
            if (sameValue(beforeValue, afterValue)) {
                continue;
            }
            SalesOrderU9ItemDataReplacementChangeDTO change = new SalesOrderU9ItemDataReplacementChangeDTO();
            change.setField(propertyName);
            change.setLabel(field.getHeader());
            change.setBeforeValue(beforeValue);
            change.setAfterValue(afterValue);
            changes.add(change);
        }
        return changes;
    }

    private String quantityError(SalesOrderItem item, Object importedQty) {
        if (!(importedQty instanceof Integer qty)) {
            return null;
        }
        List<String> blockers = new ArrayList<>();
        int releasedQty = safeInt(item.getReleasedQty());
        int shippedQty = safeInt(item.getShippedQty());
        if (qty < releasedQty) {
            blockers.add("已提数量 " + releasedQty);
        }
        if (qty < shippedQty) {
            blockers.add("已出货数量 " + shippedQty);
        }
        if (blockers.isEmpty()) {
            return null;
        }
        return "数量不能小于" + String.join("、", blockers);
    }

    private SalesOrderU9ItemDataReplacementSummaryDTO summarize(List<SalesOrderU9ItemDataReplacementRowDTO> rows) {
        SalesOrderU9ItemDataReplacementSummaryDTO summary = new SalesOrderU9ItemDataReplacementSummaryDTO();
        summary.setImportedCount(rows.size());
        for (SalesOrderU9ItemDataReplacementRowDTO row : rows) {
            if (row.getSalesOrderItemId() != null) {
                summary.setMatchedCount(summary.getMatchedCount() + 1);
            }
            switch (row.getStatus()) {
                case CHANGED -> summary.setChangedCount(summary.getChangedCount() + 1);
                case UNCHANGED -> summary.setUnchangedCount(summary.getUnchangedCount() + 1);
                case UNMATCHED -> summary.setUnmatchedCount(summary.getUnmatchedCount() + 1);
                case SKIPPED -> summary.setSkippedCount(summary.getSkippedCount() + 1);
                case ERROR -> summary.setErrorCount(summary.getErrorCount() + 1);
            }
        }
        return summary;
    }

    private SalesOrderU9ItemDataReplacementRowDTO baseRow(SalesOrderU9ItemDataReplacementExcelRowDTO excelRow) {
        SalesOrderU9ItemDataReplacementRowDTO row = new SalesOrderU9ItemDataReplacementRowDTO();
        row.setExcelRowNumber(excelRow.getExcelRowNumber());
        row.setLineNo(excelRow.getLineNo());
        row.setPartNo(excelRow.getPartNo());
        row.setVersion(excelRow.getVersion());
        return row;
    }

    private List<SalesOrderItem> listItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .orderByAsc("line_no")
                .orderByAsc("id");
        return safeList(itemMapper.selectList(query));
    }

    private List<SalesOrderItem> lockOrderItems(Long salesOrderId) {
        List<SalesOrderItem> snapshot = listItems(salesOrderId);
        List<SalesOrderItem> locked = new ArrayList<>();
        for (SalesOrderItem item : snapshot) {
            if (item == null || item.getId() == null) {
                continue;
            }
            SalesOrderItem lockedItem = itemMapper.selectByIdForUpdate(item.getId());
            if (lockedItem == null || !Objects.equals(salesOrderId, lockedItem.getSalesOrderId())) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            locked.add(lockedItem);
        }
        locked.sort(Comparator
                .comparing(SalesOrderItem::getLineNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SalesOrderItem::getId, Comparator.nullsLast(Long::compareTo)));
        return locked;
    }

    private ApplyResult applyConfirmation(
            SalesOrderHeader header,
            List<SalesOrderItem> lockedItems,
            SalesOrderU9ItemDataReplacementPreviewDTO preview) {
        Map<Long, SalesOrderItem> itemsById = lockedItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        SalesOrderItem::getId,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> changedFields = new LinkedHashSet<>();
        int updatedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SalesOrderU9ItemDataReplacementRowDTO row : preview.getRows()) {
            if (row.getStatus() != SalesOrderU9ItemDataReplacementRowStatus.CHANGED) {
                continue;
            }
            SalesOrderItem item = itemsById.get(row.getSalesOrderItemId());
            if (item == null) {
                throw new PreviewFingerprintConflictException(FINGERPRINT_CONFLICT_MESSAGE);
            }
            for (SalesOrderU9ItemDataReplacementChangeDTO change : row.getChanges()) {
                applyChange(item, change);
                changedFields.add(change.getField());
            }
            recalculateDerivedFields(item);
            item.setUpdatedAt(now);
            persistItem(header, item, now);
            updatedCount++;
        }
        return new ApplyResult(updatedCount, changedFields);
    }

    private void applyChange(SalesOrderItem item, SalesOrderU9ItemDataReplacementChangeDTO change) {
        Object value = change.getAfterValue();
        switch (change.getField()) {
            case "qty" -> item.setQty((Integer) value);
            case "customerDeliveryDate" -> item.setCustomerDeliveryDate((LocalDate) value);
            case "internalDeliveryDate" -> item.setInternalDeliveryDate((LocalDate) value);
            case "untaxedUnitPrice" -> item.setUntaxedUnitPrice((BigDecimal) value);
            case "customerOrderNo" -> item.setCustomerOrderNo(trimToNull(stringValue(value)));
            case "customerProjectNo" -> item.setCustomerProjectNo(trimToNull(stringValue(value)));
            case "internalProjectNo" -> item.setInternalProjectNo(trimToNull(stringValue(value)));
            case "remark" -> item.setRemark(trimToNull(stringValue(value)));
            default -> throw new IllegalArgumentException("存在不支持的 U9 同步表格替换字段");
        }
    }

    private void persistItem(SalesOrderHeader header, SalesOrderItem item, LocalDateTime updatedAt) {
        UpdateWrapper<SalesOrderItem> update = new UpdateWrapper<>();
        update.eq("id", item.getId())
                .eq("sales_order_id", header.getId())
                .set("qty", item.getQty())
                .set("customer_delivery_date", item.getCustomerDeliveryDate())
                .set("internal_delivery_date", item.getInternalDeliveryDate())
                .set("untaxed_unit_price", item.getUntaxedUnitPrice())
                .set("customer_order_no", item.getCustomerOrderNo())
                .set("customer_project_no", item.getCustomerProjectNo())
                .set("internal_project_no", item.getInternalProjectNo())
                .set("remark", item.getRemark())
                .set("untaxed_total_price", item.getUntaxedTotalPrice())
                .set("tax_included_total_price", item.getTaxIncludedTotalPrice())
                .set("remaining_qty", item.getRemainingQty())
                .set("unshipped_qty", item.getUnshippedQty())
                .set("updated_at", updatedAt);
        int updated = itemMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("销售订单零件行表格替换保存失败，请刷新后重试");
        }
    }

    private void recalculateDerivedFields(SalesOrderItem item) {
        Integer qty = item.getQty();
        if (qty == null) {
            item.setUntaxedTotalPrice(null);
            item.setTaxIncludedTotalPrice(null);
            item.setRemainingQty(0);
            item.setUnshippedQty(0);
            return;
        }
        if (item.getUntaxedUnitPrice() != null) {
            item.setUntaxedTotalPrice(item.getUntaxedUnitPrice().multiply(BigDecimal.valueOf(qty)));
        } else {
            item.setUntaxedTotalPrice(null);
        }
        if (item.getTaxIncludedUnitPrice() != null) {
            item.setTaxIncludedTotalPrice(item.getTaxIncludedUnitPrice().multiply(BigDecimal.valueOf(qty)));
        } else {
            item.setTaxIncludedTotalPrice(null);
        }
        item.setRemainingQty(Math.max(0, qty - safeInt(item.getReleasedQty())));
        item.setUnshippedQty(Math.max(0, qty - safeInt(item.getShippedQty())));
    }

    private Map<ItemKey, List<SalesOrderItem>> groupCandidates(List<SalesOrderItem> items) {
        Map<ItemKey, List<SalesOrderItem>> candidates = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            if (item == null) {
                continue;
            }
            candidates.computeIfAbsent(itemKey(item.getPartNo(), item.getVersion()), ignored -> new ArrayList<>())
                    .add(item);
        }
        candidates.values().forEach(group -> group.sort(Comparator.comparing(
                SalesOrderItem::getId,
                Comparator.nullsLast(Long::compareTo))));
        return candidates;
    }

    private Object currentValue(SalesOrderItem item, SalesOrderU9ItemDataReplacementField field) {
        return switch (field) {
            case QTY -> item.getQty();
            case CUSTOMER_DELIVERY_DATE -> item.getCustomerDeliveryDate();
            case INTERNAL_DELIVERY_DATE -> item.getInternalDeliveryDate();
            case UNTAXED_UNIT_PRICE -> item.getUntaxedUnitPrice();
            case CUSTOMER_ORDER_NO -> item.getCustomerOrderNo();
            case CUSTOMER_PROJECT_NO -> item.getCustomerProjectNo();
            case INTERNAL_PROJECT_NO -> item.getInternalProjectNo();
            case REMARK -> item.getRemark();
            default -> null;
        };
    }

    private boolean sameValue(Object beforeValue, Object afterValue) {
        if (beforeValue instanceof BigDecimal beforeDecimal && afterValue instanceof BigDecimal afterDecimal) {
            return beforeDecimal.compareTo(afterDecimal) == 0;
        }
        return Objects.equals(beforeValue, afterValue);
    }

    private boolean isLocked(SalesOrderHeader header) {
        String status = trimToNull(header.getU9SyncStatus());
        return STATUS_SYNCING.equals(status) || STATUS_PENDING_CONFIRMATION.equals(status) || STATUS_SYNCED.equals(status);
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private String fingerprint(
            SalesOrderHeader header,
            SalesOrderU9ItemDataReplacementExcelParseResult parsed,
            List<SalesOrderU9ItemDataReplacementRowDTO> previewRows,
            List<SalesOrderItem> items) {
        StringBuilder canonical = new StringBuilder();
        appendToken(canonical, "salesOrderId", header.getId());
        appendToken(canonical, "salesOrderNo", header.getSalesOrderNo());
        appendToken(canonical, "u9SyncStatus", header.getU9SyncStatus());
        appendToken(canonical, "headerUpdatedAt", header.getUpdatedAt());
        safeList(parsed.getRecognizedFields()).stream()
                .sorted()
                .forEach(field -> appendToken(canonical, "field", field));
        for (SalesOrderU9ItemDataReplacementExcelRowDTO row : safeList(parsed.getRows())) {
            appendToken(canonical, "excelRow", row.getExcelRowNumber());
            appendToken(canonical, "partNo", row.getNormalizedPartNo());
            appendToken(canonical, "version", row.getNormalizedVersion());
            appendToken(canonical, "lineNo", row.getLineNo());
            row.getValues().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> appendToken(canonical, "value:" + entry.getKey(), canonicalValue(entry.getValue())));
            row.getErrors().stream()
                    .map(SalesOrderU9ItemDataReplacementExcelErrorDTO::getMessage)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEach(error -> appendToken(canonical, "error", error));
        }
        for (SalesOrderU9ItemDataReplacementRowDTO row : previewRows) {
            appendToken(canonical, "status", row.getStatus());
            appendToken(canonical, "matchedItemId", row.getSalesOrderItemId());
            for (SalesOrderU9ItemDataReplacementChangeDTO change : row.getChanges()) {
                appendToken(canonical, "change", change.getField() + "=" + canonicalValue(change.getAfterValue()));
            }
        }
        for (SalesOrderItem item : safeList(items)) {
            appendToken(canonical, "itemId", item.getId());
            appendToken(canonical, "itemLineNo", item.getLineNo());
            appendToken(canonical, "itemPartNo", itemKey(item.getPartNo(), item.getVersion()));
            appendToken(canonical, "itemUpdatedAt", item.getUpdatedAt());
            appendToken(canonical, "itemQty", item.getQty());
            appendToken(canonical, "itemReleasedQty", item.getReleasedQty());
            appendToken(canonical, "itemShippedQty", item.getShippedQty());
            for (SalesOrderU9ItemDataReplacementField field : SalesOrderU9ItemDataReplacementField.replaceableFields()) {
                appendToken(canonical, "current:" + field.getPropertyName(), currentValue(item, field));
            }
        }
        return sha256(canonical.toString());
    }

    private boolean fingerprintsEqual(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String left = expected.trim();
        String right = actual.trim();
        if (left.length() != 64 || right.length() != 64) {
            return false;
        }
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(left), HexFormat.of().parseHex(right));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void auditSuccess(
            SalesOrderHeader header,
            ApplyResult applied,
            SalesOrderU9ItemDataReplacementSummaryDTO summary,
            long durationMs) {
        if (auditService == null || applied.updatedCount() <= 0) {
            return;
        }
        Map<String, Object> safeSummary = new LinkedHashMap<>();
        safeSummary.put("salesOrderId", header.getId());
        safeSummary.put("salesOrderNo", header.getSalesOrderNo());
        safeSummary.put("updatedCount", applied.updatedCount());
        safeSummary.put("changedFields", new ArrayList<>(applied.changedFields()));
        safeSummary.put("importedCount", summary.getImportedCount());
        safeSummary.put("matchedCount", summary.getMatchedCount());
        safeSummary.put("changedCount", summary.getChangedCount());
        safeSummary.put("unchangedCount", summary.getUnchangedCount());
        safeSummary.put("unmatchedCount", summary.getUnmatchedCount());
        safeSummary.put("skippedCount", summary.getSkippedCount());
        safeSummary.put("errorCount", summary.getErrorCount());
        safeSummary.put("durationMs", durationMs);
        auditService.record(
                ACTION,
                TARGET_TYPE,
                header.getId(),
                null,
                safeSummary,
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction(ACTION),
                null);
    }

    private void appendToken(StringBuilder canonical, String key, Object value) {
        String text = canonicalValue(value);
        canonical.append(key.length()).append(':').append(key)
                .append(text.length()).append(':').append(text).append('\n');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String canonicalValue(Object value) {
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ItemKey itemKey(String partNo, String version) {
        return new ItemKey(normalizeIdentity(partNo), normalizeIdentity(version));
    }

    private String normalizeIdentity(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private record ItemKey(String partNo, String version) {
    }

    private record ApplyResult(int updatedCount, Set<String> changedFields) {
    }
}
