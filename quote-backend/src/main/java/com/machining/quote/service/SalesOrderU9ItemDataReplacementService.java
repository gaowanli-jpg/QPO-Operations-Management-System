package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementChangeDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelParseResult;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementField;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementSummaryDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SalesOrderU9ItemDataReplacementService {
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
        }
        return sha256(canonical.toString());
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
}
