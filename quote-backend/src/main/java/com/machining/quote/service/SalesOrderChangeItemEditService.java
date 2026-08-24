package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.SalesOrderChangeItemUpdateReq;
import com.machining.quote.dto.SalesOrderChangeItemUpdateResp;
import com.machining.quote.dto.SalesOrderU9ItemUpdateLineReq;
import com.machining.quote.entity.SalesOrderChangeRecord;
import com.machining.quote.entity.SalesOrderChangeRecordItem;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderChangeRecordItemMapper;
import com.machining.quote.mapper.SalesOrderChangeRecordMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesOrderChangeItemEditService {
    private static final String ACTION = "SALES_ORDER_CHANGE_ITEM_UPDATE";
    private static final String TARGET_TYPE = "sales_order_header";
    private static final String STATUS_SYNCED = "已同步";
    private static final String CHANGE_TYPE_ITEM_UPDATE = "ITEM_UPDATE";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private SalesOrderChangeRecordMapper changeRecordMapper;
    @Autowired
    private SalesOrderChangeRecordItemMapper changeRecordItemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private SalesOrderChangeService previewService;
    @Autowired(required = false)
    private AuditService auditService;

    @Transactional
    public SalesOrderChangeItemUpdateResp saveItems(Long salesOrderId, SalesOrderChangeItemUpdateReq req) {
        long startedAt = System.currentTimeMillis();
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        String reason = requireReason(req);
        List<SalesOrderU9ItemUpdateLineReq> lines = normalizeLines(req);

        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        assertSynced(header);
        assertNoDuplicateLineIds(lines);

        LocalDateTime now = LocalDateTime.now();
        int updatedCount = 0;
        Set<String> changedFields = new LinkedHashSet<>();
        List<RowChange> rowChanges = new ArrayList<>();
        for (SalesOrderU9ItemUpdateLineReq line : lines) {
            SalesOrderItem item = lockItem(header, line);
            Map<String, Object> beforeSnapshot = safeSnapshot(item);
            Set<String> rowChangedFields = applyLine(item, line);
            if (rowChangedFields.isEmpty()) {
                continue;
            }
            item.setUpdatedAt(now);
            persistItem(header, item, now);
            Map<String, Object> afterSnapshot = safeSnapshot(item);
            rowChanges.add(new RowChange(item, rowChangedFields, beforeSnapshot, afterSnapshot));
            changedFields.addAll(rowChangedFields);
            updatedCount++;
        }

        Long changeRecordId = null;
        if (updatedCount > 0) {
            changeRecordId = insertChangeRecord(header, reason, updatedCount, changedFields, rowChanges, now);
            auditSuccess(header, changeRecordId, reason, updatedCount, changedFields,
                    System.currentTimeMillis() - startedAt);
        }

        SalesOrderChangeItemUpdateResp resp = new SalesOrderChangeItemUpdateResp();
        resp.setSuccess(true);
        resp.setMessage("已保存 " + updatedCount + " 行销售变更零件数据。");
        resp.setSalesOrderId(header.getId());
        resp.setSalesOrderNo(header.getSalesOrderNo());
        resp.setChangeRecordId(changeRecordId);
        resp.setUpdatedCount(updatedCount);
        resp.setChangedFields(new ArrayList<>(changedFields));
        resp.setPreview(previewService.preview(header.getId()));
        return resp;
    }

    private String requireReason(SalesOrderChangeItemUpdateReq req) {
        String reason = req == null ? null : trimToNull(req.getReason());
        if (reason == null) {
            throw new RuntimeException("变更原因不能为空");
        }
        if (reason.length() > 500) {
            throw new RuntimeException("变更原因长度不能超过 500 个字符");
        }
        return reason;
    }

    private List<SalesOrderU9ItemUpdateLineReq> normalizeLines(SalesOrderChangeItemUpdateReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请提供需要保存的销售订单零件行");
        }
        List<SalesOrderU9ItemUpdateLineReq> lines = req.getItems().stream()
                .filter(Objects::nonNull)
                .toList();
        if (lines.isEmpty()) {
            throw new RuntimeException("请提供需要保存的销售订单零件行");
        }
        for (SalesOrderU9ItemUpdateLineReq line : lines) {
            if (line.getSalesOrderItemId() == null) {
                throw new RuntimeException("销售订单零件行 ID 不能为空");
            }
            if (!line.hasEditableFields()) {
                throw new RuntimeException("销售订单零件行 " + line.getSalesOrderItemId() + " 没有可保存字段");
            }
        }
        return lines;
    }

    private void assertSynced(SalesOrderHeader header) {
        if (!STATUS_SYNCED.equals(trimToNull(header.getU9SyncStatus()))) {
            throw new RuntimeException("只允许变更已同步 U9 的销售订单");
        }
    }

    private void assertNoDuplicateLineIds(List<SalesOrderU9ItemUpdateLineReq> lines) {
        Set<Long> seen = new LinkedHashSet<>();
        for (SalesOrderU9ItemUpdateLineReq line : lines) {
            if (!seen.add(line.getSalesOrderItemId())) {
                throw new RuntimeException("销售订单零件行重复提交：" + line.getSalesOrderItemId());
            }
        }
    }

    private SalesOrderItem lockItem(SalesOrderHeader header, SalesOrderU9ItemUpdateLineReq line) {
        SalesOrderItem item = itemMapper.selectByIdForUpdate(line.getSalesOrderItemId());
        if (item == null) {
            throw new RuntimeException("销售订单零件行不存在：" + line.getSalesOrderItemId());
        }
        if (!Objects.equals(header.getId(), item.getSalesOrderId())) {
            throw new RuntimeException("销售订单零件行 " + line.getSalesOrderItemId() + " 不属于当前销售订单");
        }
        return item;
    }

    private Set<String> applyLine(SalesOrderItem item, SalesOrderU9ItemUpdateLineReq line) {
        Set<String> changed = new LinkedHashSet<>();
        if (line.hasField(SalesOrderU9ItemUpdateLineReq.FIELD_QTY)) {
            Integer qty = line.getQty();
            validateQty(item, qty);
            if (!Objects.equals(item.getQty(), qty)) {
                item.setQty(qty);
                changed.add(SalesOrderU9ItemUpdateLineReq.FIELD_QTY);
            }
        }
        if (line.hasField(SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_DELIVERY_DATE)) {
            changedIfDate(item.getCustomerDeliveryDate(), line.getCustomerDeliveryDate(), changed,
                    SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_DELIVERY_DATE);
            item.setCustomerDeliveryDate(line.getCustomerDeliveryDate());
        }
        if (line.hasField(SalesOrderU9ItemUpdateLineReq.FIELD_INTERNAL_DELIVERY_DATE)) {
            changedIfDate(item.getInternalDeliveryDate(), line.getInternalDeliveryDate(), changed,
                    SalesOrderU9ItemUpdateLineReq.FIELD_INTERNAL_DELIVERY_DATE);
            item.setInternalDeliveryDate(line.getInternalDeliveryDate());
        }
        if (line.hasField(SalesOrderU9ItemUpdateLineReq.FIELD_UNTAXED_UNIT_PRICE)) {
            BigDecimal price = line.getUntaxedUnitPrice();
            validatePrice(price);
            if (!sameDecimal(item.getUntaxedUnitPrice(), price)) {
                item.setUntaxedUnitPrice(price);
                changed.add(SalesOrderU9ItemUpdateLineReq.FIELD_UNTAXED_UNIT_PRICE);
            }
        }
        applyText(item.getCustomerOrderNo(), line.getCustomerOrderNo(), 120,
                SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_ORDER_NO, changed, line,
                item::setCustomerOrderNo);
        applyText(item.getCustomerProjectNo(), line.getCustomerProjectNo(), 120,
                SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_PROJECT_NO, changed, line,
                item::setCustomerProjectNo);
        applyText(item.getInternalProjectNo(), line.getInternalProjectNo(), 120,
                SalesOrderU9ItemUpdateLineReq.FIELD_INTERNAL_PROJECT_NO, changed, line,
                item::setInternalProjectNo);
        applyText(item.getRemark(), line.getRemark(), 500,
                SalesOrderU9ItemUpdateLineReq.FIELD_REMARK, changed, line,
                item::setRemark);

        if (!changed.isEmpty()) {
            recalculateDerivedFields(item);
        }
        return changed;
    }

    private void applyText(
            String current,
            String raw,
            int maxLength,
            String field,
            Set<String> changed,
            SalesOrderU9ItemUpdateLineReq line,
            java.util.function.Consumer<String> setter) {
        if (!line.hasField(field)) {
            return;
        }
        String value = trimToNull(raw);
        if (value != null && value.length() > maxLength) {
            throw new RuntimeException(label(field) + "长度不能超过 " + maxLength + " 个字符");
        }
        if (!Objects.equals(trimToNull(current), value)) {
            changed.add(field);
        }
        setter.accept(value);
    }

    private void changedIfDate(LocalDate current, LocalDate value, Set<String> changed, String field) {
        if (!Objects.equals(current, value)) {
            changed.add(field);
        }
    }

    private void validateQty(SalesOrderItem item, Integer qty) {
        if (qty == null || qty <= 0) {
            throw new RuntimeException("数量必须为正整数");
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
        if (!blockers.isEmpty()) {
            throw new RuntimeException("数量不能小于" + String.join("、", blockers));
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("未税单价必须大于等于 0");
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
            throw new RuntimeException("销售订单零件行保存失败，请刷新后重试");
        }
    }

    private Long insertChangeRecord(
            SalesOrderHeader header,
            String reason,
            int updatedCount,
            Set<String> changedFields,
            List<RowChange> rowChanges,
            LocalDateTime now) {
        SalesOrderChangeRecord record = new SalesOrderChangeRecord();
        record.setSalesOrderId(header.getId());
        record.setSalesOrderNo(header.getSalesOrderNo());
        record.setChangeType(CHANGE_TYPE_ITEM_UPDATE);
        record.setReason(reason);
        record.setUpdatedCount(updatedCount);
        record.setChangedFieldsJson(toJson(new ArrayList<>(changedFields)));
        AuthContext.SessionUser user = AuthContext.get();
        if (user != null) {
            record.setCreatedBy(user.id() == null ? null : user.id().longValue());
            record.setCreatedByName(firstNonBlank(user.displayName(), user.username()));
        }
        record.setCreatedAt(now);
        if (changeRecordMapper.insert(record) <= 0 || record.getId() == null) {
            throw new RuntimeException("销售变更记录保存失败，请刷新后重试");
        }
        for (RowChange row : rowChanges) {
            SalesOrderChangeRecordItem detail = new SalesOrderChangeRecordItem();
            detail.setChangeRecordId(record.getId());
            detail.setSalesOrderItemId(row.item().getId());
            detail.setLineNo(row.item().getLineNo());
            detail.setPartNo(row.item().getPartNo());
            detail.setVersion(row.item().getVersion());
            detail.setChangedFieldsJson(toJson(new ArrayList<>(row.changedFields())));
            detail.setBeforeSnapshotJson(toJson(row.beforeSnapshot()));
            detail.setAfterSnapshotJson(toJson(row.afterSnapshot()));
            detail.setCreatedAt(now);
            if (changeRecordItemMapper.insert(detail) <= 0) {
                throw new RuntimeException("销售变更明细保存失败，请刷新后重试");
            }
        }
        return record.getId();
    }

    private Map<String, Object> safeSnapshot(SalesOrderItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("salesOrderItemId", item.getId());
        snapshot.put("lineNo", item.getLineNo());
        snapshot.put("partNo", item.getPartNo());
        snapshot.put("version", item.getVersion());
        snapshot.put("qty", item.getQty());
        snapshot.put("customerDeliveryDate", item.getCustomerDeliveryDate() == null ? null : item.getCustomerDeliveryDate().toString());
        snapshot.put("internalDeliveryDate", item.getInternalDeliveryDate() == null ? null : item.getInternalDeliveryDate().toString());
        snapshot.put("untaxedUnitPrice", item.getUntaxedUnitPrice());
        snapshot.put("customerOrderNo", item.getCustomerOrderNo());
        snapshot.put("customerProjectNo", item.getCustomerProjectNo());
        snapshot.put("internalProjectNo", item.getInternalProjectNo());
        snapshot.put("remark", item.getRemark());
        return snapshot;
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private void auditSuccess(
            SalesOrderHeader header,
            Long changeRecordId,
            String reason,
            int updatedCount,
            Set<String> changedFields,
            long durationMs) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", header.getId());
        summary.put("salesOrderNo", header.getSalesOrderNo());
        summary.put("changeRecordId", changeRecordId);
        summary.put("reason", reason);
        summary.put("updatedCount", updatedCount);
        summary.put("changedFields", new ArrayList<>(changedFields));
        summary.put("durationMs", durationMs);
        auditService.record(
                ACTION,
                TARGET_TYPE,
                header.getId(),
                null,
                summary,
                "CHANGE",
                "SUCCESS",
                AuditService.labelForAction(ACTION),
                null
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("销售变更快照序列化失败", e);
        }
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String primary, String fallback) {
        return trimToNull(primary) == null ? trimToNull(fallback) : trimToNull(primary);
    }

    private String label(String field) {
        return switch (field) {
            case SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_ORDER_NO -> "客户订单号";
            case SalesOrderU9ItemUpdateLineReq.FIELD_CUSTOMER_PROJECT_NO -> "客户项目号";
            case SalesOrderU9ItemUpdateLineReq.FIELD_INTERNAL_PROJECT_NO -> "内部项目号";
            case SalesOrderU9ItemUpdateLineReq.FIELD_REMARK -> "备注";
            default -> field;
        };
    }

    private record RowChange(
            SalesOrderItem item,
            Set<String> changedFields,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot) {
    }
}
