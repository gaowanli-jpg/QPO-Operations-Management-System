package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SalesOrderItemUpdateLineReq;
import com.machining.quote.dto.SalesOrderItemUpdateReq;
import com.machining.quote.dto.SalesOrderMaintenanceOrderUpdateReq;
import com.machining.quote.dto.SalesOrderMaintenanceUpdateResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
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
import java.util.function.Consumer;

@Service
public class SalesOrderMaintenanceService {
    static final String ACTION_ORDER_UPDATE = "SALES_ORDER_UPDATE";
    static final String ACTION_ITEM_UPDATE = "SALES_ORDER_ITEM_UPDATE";
    static final String TARGET_TYPE = "sales_order_header";
    static final String STATUS_SYNCING = "同步中";
    static final String STATUS_PENDING_CONFIRMATION = "结果待确认";
    static final String STATUS_SYNCED = "已同步";

    private static final Set<String> ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");
    private static final int TEXT_50 = 50;
    private static final int TEXT_120 = 120;
    private static final int TEXT_255 = 255;
    private static final int TEXT_200 = 200;
    private static final int TEXT_500 = 500;

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private AuditService auditService;

    @Transactional
    public SalesOrderMaintenanceUpdateResp saveOrder(Long salesOrderId, SalesOrderMaintenanceOrderUpdateReq req) {
        long startedAt = System.currentTimeMillis();
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        if (req == null || !req.hasEditableFields()) {
            throw new RuntimeException("请提供需要保存的销售订单字段");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        assertEditable(header);

        Map<String, Object> before = orderSnapshot(header);
        Set<String> changedFields = applyOrderFields(header, req);
        List<SalesOrderItem> items = listItems(salesOrderId);
        int itemDefaultFillCount = propagateOrderDefaults(header, req, items);
        LocalDateTime now = LocalDateTime.now();
        if (!changedFields.isEmpty()) {
            header.setUpdatedAt(now);
            persistHeader(header, now);
        }
        Map<String, Object> after = orderSnapshot(header);
        after.put("itemDefaultFillCount", itemDefaultFillCount);
        after.put("durationMs", System.currentTimeMillis() - startedAt);
        auditSuccess(ACTION_ORDER_UPDATE, header, before, after, changedFields, System.currentTimeMillis() - startedAt);
        return response(header, changedFields.isEmpty() ? 0 : 1, changedFields, "销售订单信息已保存。");
    }

    @Transactional
    public SalesOrderMaintenanceUpdateResp saveItems(Long salesOrderId, SalesOrderItemUpdateReq req) {
        long startedAt = System.currentTimeMillis();
        SalesOrderHeader header = lockEditableHeader(salesOrderId);
        List<SalesOrderItemUpdateLineReq> lines = normalizeLines(req);
        assertNoDuplicateLineIds(lines);

        int updatedCount = 0;
        Set<String> changedFields = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (SalesOrderItemUpdateLineReq line : lines) {
            SalesOrderItem item = lockItem(header, line.getSalesOrderItemId());
            Set<String> rowChanged = applyItemLine(header, item, line, now);
            if (rowChanged.isEmpty()) {
                continue;
            }
            changedFields.addAll(rowChanged);
            updatedCount++;
        }
        auditSuccess(ACTION_ITEM_UPDATE, header, null, itemAuditSummary(header, updatedCount, changedFields), changedFields,
                System.currentTimeMillis() - startedAt);
        return response(header, updatedCount, changedFields, "销售订单零件信息已保存。");
    }

    SalesOrderHeader lockEditableHeader(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        assertEditable(header);
        return header;
    }

    Set<String> applyItemLine(
            SalesOrderHeader header,
            SalesOrderItem item,
            SalesOrderItemUpdateLineReq line,
            LocalDateTime updatedAt) {
        Set<String> changed = new LinkedHashSet<>();
        if (line.hasField(SalesOrderItemUpdateLineReq.FIELD_QTY)) {
            Integer qty = line.getQty();
            validateQty(qty);
            if (!Objects.equals(item.getQty(), qty)) {
                item.setQty(qty);
                changed.add(SalesOrderItemUpdateLineReq.FIELD_QTY);
            }
        }
        if (line.hasField(SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_DELIVERY_DATE)) {
            changedIfDate(item.getCustomerDeliveryDate(), line.getCustomerDeliveryDate(), changed,
                    SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_DELIVERY_DATE);
            item.setCustomerDeliveryDate(line.getCustomerDeliveryDate());
        }
        if (line.hasField(SalesOrderItemUpdateLineReq.FIELD_INTERNAL_DELIVERY_DATE)) {
            changedIfDate(item.getInternalDeliveryDate(), line.getInternalDeliveryDate(), changed,
                    SalesOrderItemUpdateLineReq.FIELD_INTERNAL_DELIVERY_DATE);
            item.setInternalDeliveryDate(line.getInternalDeliveryDate());
        }
        if (line.hasField(SalesOrderItemUpdateLineReq.FIELD_UNTAXED_UNIT_PRICE)) {
            BigDecimal price = line.getUntaxedUnitPrice();
            validatePrice(price, "未税单价");
            if (!sameDecimal(item.getUntaxedUnitPrice(), price)) {
                item.setUntaxedUnitPrice(price);
                changed.add(SalesOrderItemUpdateLineReq.FIELD_UNTAXED_UNIT_PRICE);
            }
        }
        if (line.hasField(SalesOrderItemUpdateLineReq.FIELD_TAX_INCLUDED_UNIT_PRICE)) {
            BigDecimal price = line.getTaxIncludedUnitPrice();
            validatePrice(price, "含税单价");
            if (!sameDecimal(item.getTaxIncludedUnitPrice(), price)) {
                item.setTaxIncludedUnitPrice(price);
                changed.add(SalesOrderItemUpdateLineReq.FIELD_TAX_INCLUDED_UNIT_PRICE);
            }
        }
        applyText(item.getVersion(), line.getVersion(), TEXT_50,
                SalesOrderItemUpdateLineReq.FIELD_VERSION, changed, line, item::setVersion);
        applyText(item.getMaterialName(), line.getMaterialName(), TEXT_255,
                SalesOrderItemUpdateLineReq.FIELD_MATERIAL_NAME, changed, line, item::setMaterialName);
        applyText(item.getSurfaceTreatment(), line.getSurfaceTreatment(), TEXT_255,
                SalesOrderItemUpdateLineReq.FIELD_SURFACE_TREATMENT, changed, line, item::setSurfaceTreatment);
        applyText(item.getCustomerOrderNo(), line.getCustomerOrderNo(), TEXT_120,
                SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_ORDER_NO, changed, line, item::setCustomerOrderNo);
        applyText(item.getCustomerProjectNo(), line.getCustomerProjectNo(), TEXT_120,
                SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_PROJECT_NO, changed, line, item::setCustomerProjectNo);
        applyText(item.getInternalProjectNo(), line.getInternalProjectNo(), TEXT_120,
                SalesOrderItemUpdateLineReq.FIELD_INTERNAL_PROJECT_NO, changed, line, item::setInternalProjectNo);
        applyText(item.getRemark(), line.getRemark(), TEXT_500,
                SalesOrderItemUpdateLineReq.FIELD_REMARK, changed, line, item::setRemark);

        if (!changed.isEmpty()) {
            recalculateDerivedFields(item);
            item.setUpdatedAt(updatedAt);
            persistItem(header, item, updatedAt);
        }
        return changed;
    }

    private Set<String> applyOrderFields(SalesOrderHeader header, SalesOrderMaintenanceOrderUpdateReq req) {
        Set<String> changed = new LinkedHashSet<>();
        applyOrderText(header.getDocumentTypeCode(), req.getDocumentTypeCode(), TEXT_120,
                SalesOrderMaintenanceOrderUpdateReq.FIELD_DOCUMENT_TYPE_CODE, changed, req, header::setDocumentTypeCode);
        if (req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_SALES_ORDER_DATE)) {
            if (!Objects.equals(header.getSalesOrderDate(), req.getSalesOrderDate())) {
                changed.add(SalesOrderMaintenanceOrderUpdateReq.FIELD_SALES_ORDER_DATE);
            }
            header.setSalesOrderDate(req.getSalesOrderDate());
        }
        applyOrderText(header.getCustomerOrderNo(), req.getCustomerOrderNo(), TEXT_120,
                SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_ORDER_NO, changed, req, header::setCustomerOrderNo);
        applyOrderText(header.getCustomerProjectNo(), req.getCustomerProjectNo(), TEXT_120,
                SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_PROJECT_NO, changed, req, header::setCustomerProjectNo);
        applyOrderText(header.getCustomerOrderTitle(), req.getCustomerOrderTitle(), TEXT_200,
                SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_ORDER_TITLE, changed, req, header::setCustomerOrderTitle);
        if (req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_ORDER_TYPE)) {
            String orderType = trimToNull(req.getOrderType());
            if (orderType != null && !ORDER_TYPES.contains(orderType)) {
                throw new RuntimeException("订单类型必须为预测订单、备货订单或正式订单");
            }
            if (!Objects.equals(trimToNull(header.getOrderType()), orderType)) {
                changed.add(SalesOrderMaintenanceOrderUpdateReq.FIELD_ORDER_TYPE);
            }
            header.setOrderType(orderType);
        }
        applyOrderText(header.getInternalProjectNo(), req.getInternalProjectNo(), TEXT_120,
                SalesOrderMaintenanceOrderUpdateReq.FIELD_INTERNAL_PROJECT_NO, changed, req, header::setInternalProjectNo);
        return changed;
    }

    private int propagateOrderDefaults(
            SalesOrderHeader header,
            SalesOrderMaintenanceOrderUpdateReq req,
            List<SalesOrderItem> items) {
        int changedRows = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SalesOrderItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            boolean changed = false;
            if (req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_SALES_ORDER_DATE)
                    && !Objects.equals(item.getSalesOrderDate(), header.getSalesOrderDate())) {
                item.setSalesOrderDate(header.getSalesOrderDate());
                changed = true;
            }
            changed |= fillBlank(item.getCustomerOrderNo(), header.getCustomerOrderNo(),
                    req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_ORDER_NO), item::setCustomerOrderNo);
            changed |= fillBlank(item.getCustomerProjectNo(), header.getCustomerProjectNo(),
                    req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_PROJECT_NO), item::setCustomerProjectNo);
            changed |= fillBlank(item.getOrderType(), header.getOrderType(),
                    req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_ORDER_TYPE), item::setOrderType);
            changed |= fillBlank(item.getInternalProjectNo(), header.getInternalProjectNo(),
                    req.hasField(SalesOrderMaintenanceOrderUpdateReq.FIELD_INTERNAL_PROJECT_NO), item::setInternalProjectNo);
            if (changed) {
                item.setUpdatedAt(now);
                persistOrderDefaultItem(header, item, now);
                changedRows++;
            }
        }
        return changedRows;
    }

    private boolean fillBlank(String current, String candidate, boolean fieldProvided, Consumer<String> setter) {
        String value = trimToNull(candidate);
        if (!fieldProvided || !isBlank(current) || value == null) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private SalesOrderItem lockItem(SalesOrderHeader header, Long itemId) {
        if (itemId == null) {
            throw new RuntimeException("销售订单零件行 ID 不能为空");
        }
        SalesOrderItem item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("销售订单零件行不存在：" + itemId);
        }
        if (!Objects.equals(header.getId(), item.getSalesOrderId())) {
            throw new RuntimeException("销售订单零件行 " + itemId + " 不属于当前销售订单");
        }
        return item;
    }

    private List<SalesOrderItem> normalizeItems(List<SalesOrderItem> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    private List<SalesOrderItem> listItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .orderByAsc("line_no")
                .orderByAsc("id");
        return normalizeItems(itemMapper.selectList(query));
    }

    private List<SalesOrderItemUpdateLineReq> normalizeLines(SalesOrderItemUpdateReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请提供需要保存的销售订单零件行");
        }
        List<SalesOrderItemUpdateLineReq> lines = req.getItems().stream()
                .filter(Objects::nonNull)
                .toList();
        if (lines.isEmpty()) {
            throw new RuntimeException("请提供需要保存的销售订单零件行");
        }
        for (SalesOrderItemUpdateLineReq line : lines) {
            if (line.getSalesOrderItemId() == null) {
                throw new RuntimeException("销售订单零件行 ID 不能为空");
            }
            if (!line.hasEditableFields()) {
                throw new RuntimeException("销售订单零件行 " + line.getSalesOrderItemId() + " 没有可保存字段");
            }
        }
        return lines;
    }

    private void assertNoDuplicateLineIds(List<SalesOrderItemUpdateLineReq> lines) {
        Set<Long> seen = new LinkedHashSet<>();
        for (SalesOrderItemUpdateLineReq line : lines) {
            if (!seen.add(line.getSalesOrderItemId())) {
                throw new RuntimeException("销售订单零件行重复提交：" + line.getSalesOrderItemId());
            }
        }
    }

    private void applyText(
            String current,
            String raw,
            int maxLength,
            String field,
            Set<String> changed,
            SalesOrderItemUpdateLineReq line,
            Consumer<String> setter) {
        if (!line.hasField(field)) {
            return;
        }
        String value = validateText(raw, maxLength, label(field));
        if (!Objects.equals(trimToNull(current), value)) {
            changed.add(field);
        }
        setter.accept(value);
    }

    private void applyOrderText(
            String current,
            String raw,
            int maxLength,
            String field,
            Set<String> changed,
            SalesOrderMaintenanceOrderUpdateReq req,
            Consumer<String> setter) {
        if (!req.hasField(field)) {
            return;
        }
        String value = validateText(raw, maxLength, label(field));
        if (!Objects.equals(trimToNull(current), value)) {
            changed.add(field);
        }
        setter.accept(value);
    }

    private String validateText(String raw, int maxLength, String label) {
        String value = trimToNull(raw);
        if (value != null && value.length() > maxLength) {
            throw new RuntimeException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private void changedIfDate(LocalDate current, LocalDate value, Set<String> changed, String field) {
        if (!Objects.equals(current, value)) {
            changed.add(field);
        }
    }

    private void validateQty(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new RuntimeException("数量必须为正整数");
        }
    }

    private void validatePrice(BigDecimal price, String label) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(label + "必须大于等于 0");
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

    private void persistHeader(SalesOrderHeader header, LocalDateTime updatedAt) {
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", header.getId())
                .set("document_type_code", header.getDocumentTypeCode())
                .set("sales_order_date", header.getSalesOrderDate())
                .set("customer_order_no", header.getCustomerOrderNo())
                .set("customer_project_no", header.getCustomerProjectNo())
                .set("customer_order_title", header.getCustomerOrderTitle())
                .set("order_type", header.getOrderType())
                .set("internal_project_no", header.getInternalProjectNo())
                .set("updated_at", updatedAt);
        if (headerMapper.update(null, update) <= 0) {
            throw new RuntimeException("销售订单信息保存失败，请刷新后重试");
        }
    }

    private void persistOrderDefaultItem(SalesOrderHeader header, SalesOrderItem item, LocalDateTime updatedAt) {
        UpdateWrapper<SalesOrderItem> update = new UpdateWrapper<>();
        update.eq("id", item.getId())
                .eq("sales_order_id", header.getId())
                .set("sales_order_date", item.getSalesOrderDate())
                .set("customer_order_no", item.getCustomerOrderNo())
                .set("customer_project_no", item.getCustomerProjectNo())
                .set("order_type", item.getOrderType())
                .set("internal_project_no", item.getInternalProjectNo())
                .set("updated_at", updatedAt);
        if (itemMapper.update(null, update) <= 0) {
            throw new RuntimeException("销售订单零件默认字段保存失败，请刷新后重试");
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
                .set("tax_included_unit_price", item.getTaxIncludedUnitPrice())
                .set("version", item.getVersion())
                .set("material_name", item.getMaterialName())
                .set("surface_treatment", item.getSurfaceTreatment())
                .set("customer_order_no", item.getCustomerOrderNo())
                .set("customer_project_no", item.getCustomerProjectNo())
                .set("internal_project_no", item.getInternalProjectNo())
                .set("remark", item.getRemark())
                .set("untaxed_total_price", item.getUntaxedTotalPrice())
                .set("tax_included_total_price", item.getTaxIncludedTotalPrice())
                .set("remaining_qty", item.getRemainingQty())
                .set("unshipped_qty", item.getUnshippedQty())
                .set("updated_at", updatedAt);
        if (itemMapper.update(null, update) <= 0) {
            throw new RuntimeException("销售订单零件行保存失败，请刷新后重试");
        }
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private void assertEditable(SalesOrderHeader header) {
        String status = trimToNull(header.getU9SyncStatus());
        if (STATUS_SYNCING.equals(status) || STATUS_PENDING_CONFIRMATION.equals(status) || STATUS_SYNCED.equals(status)) {
            throw new RuntimeException("U9 状态为" + status + "，不能修改销售订单");
        }
    }

    private void auditSuccess(
            String action,
            SalesOrderHeader header,
            Map<String, Object> before,
            Map<String, Object> after,
            Set<String> changedFields,
            long durationMs) {
        if (auditService == null || changedFields.isEmpty()) {
            return;
        }
        Map<String, Object> safeAfter = after == null ? new LinkedHashMap<>() : new LinkedHashMap<>(after);
        safeAfter.put("salesOrderId", header.getId());
        safeAfter.put("salesOrderNo", header.getSalesOrderNo());
        safeAfter.put("changedFields", new ArrayList<>(changedFields));
        safeAfter.put("durationMs", durationMs);
        auditService.record(
                action,
                TARGET_TYPE,
                header.getId(),
                before,
                safeAfter,
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction(action),
                null);
    }

    private Map<String, Object> itemAuditSummary(SalesOrderHeader header, int updatedCount, Set<String> changedFields) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", header.getId());
        summary.put("salesOrderNo", header.getSalesOrderNo());
        summary.put("updatedCount", updatedCount);
        summary.put("changedFields", new ArrayList<>(changedFields));
        return summary;
    }

    private Map<String, Object> orderSnapshot(SalesOrderHeader header) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("salesOrderId", header.getId());
        data.put("salesOrderNo", header.getSalesOrderNo());
        data.put("documentTypeCode", header.getDocumentTypeCode());
        data.put("salesOrderDate", header.getSalesOrderDate());
        data.put("customerOrderNo", header.getCustomerOrderNo());
        data.put("customerProjectNo", header.getCustomerProjectNo());
        data.put("customerOrderTitle", header.getCustomerOrderTitle());
        data.put("orderType", header.getOrderType());
        data.put("internalProjectNo", header.getInternalProjectNo());
        data.put("u9SyncStatus", header.getU9SyncStatus());
        return data;
    }

    private SalesOrderMaintenanceUpdateResp response(
            SalesOrderHeader header,
            int updatedCount,
            Set<String> changedFields,
            String message) {
        SalesOrderMaintenanceUpdateResp resp = new SalesOrderMaintenanceUpdateResp();
        resp.setSuccess(true);
        resp.setMessage(message);
        resp.setSalesOrderId(header.getId());
        resp.setSalesOrderNo(header.getSalesOrderNo());
        resp.setUpdatedCount(updatedCount);
        resp.setChangedFields(new ArrayList<>(changedFields));
        return resp;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String label(String field) {
        if (SalesOrderMaintenanceOrderUpdateReq.FIELD_DOCUMENT_TYPE_CODE.equals(field)) {
            return "单据类型";
        }
        if (SalesOrderMaintenanceOrderUpdateReq.FIELD_CUSTOMER_ORDER_TITLE.equals(field)) {
            return "客户下单抬头";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_VERSION.equals(field)) {
            return "版本";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_MATERIAL_NAME.equals(field)) {
            return "材料";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_SURFACE_TREATMENT.equals(field)) {
            return "表面处理";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_ORDER_NO.equals(field)) {
            return "客户订单号";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_CUSTOMER_PROJECT_NO.equals(field)) {
            return "客户项目号";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_INTERNAL_PROJECT_NO.equals(field)) {
            return "内部项目号";
        }
        if (SalesOrderItemUpdateLineReq.FIELD_REMARK.equals(field)) {
            return "备注";
        }
        return field;
    }
}
