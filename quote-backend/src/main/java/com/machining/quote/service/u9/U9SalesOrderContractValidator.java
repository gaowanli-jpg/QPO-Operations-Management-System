package com.machining.quote.service.u9;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class U9SalesOrderContractValidator {
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_SYNCED = "已同步";

    public ValidationResult validate(SalesOrderHeader header, List<SalesOrderItem> items) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (header == null) {
            errors.add("销售订单不存在");
            return new ValidationResult(errors, warnings);
        }
        validateHeader(header, errors);
        List<SalesOrderItem> safeItems = items == null ? List.of() : items;
        if (safeItems.isEmpty()) {
            errors.add("销售订单没有可同步零件");
        }
        for (SalesOrderItem item : safeItems) {
            ValidationResult lineResult = validateItem(item);
            errors.addAll(lineResult.blockingErrors());
            warnings.addAll(lineResult.warnings());
        }
        return new ValidationResult(distinct(errors), distinct(warnings));
    }

    public ValidationResult validateItem(SalesOrderItem item) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (item == null) {
            errors.add("销售订单零件不存在");
            return new ValidationResult(errors, warnings);
        }
        if (!isFullyReleased(item)) {
            errors.add("销售订单零件" + itemLabel(item) + "尚未全部生成提单，不能同步 U9");
        }
        if (isBlank(item.getMaterialNo())) {
            errors.add("销售订单零件" + itemLabel(item) + "缺少物料号，不能同步 U9");
        } else if (!MATERIAL_CONFIRMED.equals(item.getMaterialNoStatus())) {
            errors.add("销售订单零件" + itemLabel(item) + "物料号未确认，不能同步 U9");
        }
        if (item.getQty() == null || item.getQty() <= 0) {
            errors.add("销售订单零件" + itemLabel(item) + "数量必须为正整数，不能同步 U9");
        }
        if (item.getUntaxedUnitPrice() == null) {
            errors.add("销售订单零件" + itemLabel(item) + "未税单价为空，不能同步 U9");
        }
        if (item.getCustomerDeliveryDate() == null) {
            warnings.add("销售订单零件" + itemLabel(item) + "客户交期为空，U9 请求不发送客户交期");
        }
        if (item.getInternalDeliveryDate() == null) {
            warnings.add("销售订单零件" + itemLabel(item) + "内部交期为空，U9 请求不发送内部交期");
        }
        return new ValidationResult(distinct(errors), distinct(warnings));
    }

    private void validateHeader(SalesOrderHeader header, List<String> errors) {
        if (QuoteStatusWorkflow.DISCARDED.equals(header.getStatus())) {
            errors.add("销售订单已废弃，不能同步 U9");
        } else if (QuoteStatusWorkflow.QUOTED.equals(header.getStatus())) {
            errors.add("销售订单尚未生成 QPO 提单，不能同步 U9");
        } else if (QuoteStatusWorkflow.PARTIALLY_RELEASED.equals(header.getStatus())) {
            errors.add("销售订单仍存在未提单数量，需全部生成提单后再同步 U9");
        } else if (!QuoteStatusWorkflow.RELEASED.equals(header.getStatus())) {
            errors.add("销售订单状态暂不可同步 U9");
        }
        if (STATUS_SYNCED.equals(header.getU9SyncStatus())) {
            errors.add("销售订单已同步，不能重复创建 U9 订单");
        } else if (STATUS_SYNCING.equals(header.getU9SyncStatus())) {
            errors.add("U9 同步处理中，请稍后刷新");
        }
        if (isBlank(header.getSalesOrderNo())) {
            errors.add("销售订单号不能为空，不能同步 U9");
        }
        if (isBlank(header.getDocumentNo())) {
            errors.add("稳定 DocNo 不能为空，不能同步 U9");
        }
        if (header.getSalesOrderDate() == null) {
            errors.add("销售订单下单日期不能为空，不能同步 U9");
        }
        if (isBlank(header.getCustomerCode())) {
            errors.add("客户编码不能为空，不能同步 U9");
        }
        if (isBlank(header.getDocumentTypeCode())) {
            errors.add("单据类型编码不能为空，不能同步 U9");
        }
    }

    private boolean isFullyReleased(SalesOrderItem item) {
        if (item == null) {
            return false;
        }
        if (item.getRemainingQty() != null && item.getRemainingQty() > 0) {
            return false;
        }
        if (item.getQty() != null && item.getReleasedQty() != null && item.getReleasedQty() < item.getQty()) {
            return false;
        }
        return isBlank(item.getStatus()) || QuoteStatusWorkflow.RELEASED.equals(item.getStatus());
    }

    private List<String> distinct(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private String itemLabel(SalesOrderItem item) {
        List<String> parts = new ArrayList<>();
        if (item.getId() != null) {
            parts.add("ID=" + item.getId());
        }
        if (!isBlank(item.getPartNo())) {
            parts.add("图号=" + item.getPartNo());
        }
        if (!isBlank(item.getVersion())) {
            parts.add("版本=" + item.getVersion());
        }
        return parts.isEmpty() ? "" : " " + String.join(" ", parts) + " ";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record ValidationResult(List<String> blockingErrors, List<String> warnings) {
        public boolean hasBlockingErrors() {
            return blockingErrors != null && !blockingErrors.isEmpty();
        }

        public String firstBlockingError() {
            return hasBlockingErrors() ? blockingErrors.get(0) : "";
        }
    }
}
