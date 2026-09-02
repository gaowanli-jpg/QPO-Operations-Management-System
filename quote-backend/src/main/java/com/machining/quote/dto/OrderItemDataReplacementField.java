package com.machining.quote.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum OrderItemDataReplacementField {
    ITEM_ID("零件ID", "itemId", ValueType.ITEM_ID, null, false),
    PART_NO("图号", "matchPartNo", ValueType.MATCH_KEY, 100, false),
    VERSION("版本", "matchVersion", ValueType.MATCH_KEY, 20, false),
    NEW_PART_NO("新图号", "partNo", ValueType.TEXT, 100, true),
    NEW_VERSION("新版本", "version", ValueType.TEXT, 20, true),
    QTY("询价数量", "qty", ValueType.POSITIVE_INTEGER, null, true),
    LEAD_TIME("客户询价交期", "leadTime", ValueType.LEAD_TIME, 100, true),
    QUOTE_LEAD_TIME("报价承诺交期", "quoteLeadTime", ValueType.LEAD_TIME, 100, true),
    INTERNAL_TARGET_LEAD_TIME("内部目标交期", "internalTargetLeadTime", ValueType.LEAD_TIME, 100, true),
    DESCRIPTION("物料描述", "description", ValueType.TEXT, 500, true),
    SURFACE_TREATMENT("表面处理", "surfaceTreatment", ValueType.TEXT, 200, true),
    REMARK("备注", "remark", ValueType.TEXT, 500, true),
    SALES_ORDER_DATE("下单日期", "salesOrderDate", ValueType.DATE, null, true),
    CUSTOMER_ORDER_NO("客户订单号", "customerOrderNo", ValueType.TEXT, 120, true),
    CUSTOMER_PROJECT_NO("客户项目号", "customerProjectNo", ValueType.TEXT, 120, true),
    CUSTOMER_ORDER_TITLE("客户下单抬头", "customerOrderTitle", ValueType.TEXT, 255, true),
    ORDER_TYPE("订单类型", "orderType", ValueType.ORDER_TYPE, 20, true),
    INTERNAL_PROJECT_NO("内部项目号", "internalProjectNo", ValueType.TEXT, 120, true);

    private static final List<OrderItemDataReplacementField> REPLACEABLE_FIELDS = Arrays.stream(values())
            .filter(OrderItemDataReplacementField::isReplaceable)
            .toList();
    private static final Map<String, OrderItemDataReplacementField> BY_NORMALIZED_HEADER;

    static {
        Map<String, OrderItemDataReplacementField> fieldsByHeader = new LinkedHashMap<>();
        for (OrderItemDataReplacementField field : values()) {
            fieldsByHeader.put(normalizeHeader(field.header), field);
        }
        BY_NORMALIZED_HEADER = Collections.unmodifiableMap(fieldsByHeader);
    }

    private final String header;
    private final String propertyName;
    private final ValueType valueType;
    private final Integer maxLength;
    private final boolean persistent;

    OrderItemDataReplacementField(
            String header,
            String propertyName,
            ValueType valueType,
            Integer maxLength,
            boolean persistent) {
        this.header = header;
        this.propertyName = propertyName;
        this.valueType = valueType;
        this.maxLength = maxLength;
        this.persistent = persistent;
    }

    public String getHeader() {
        return header;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isReplaceable() {
        return persistent;
    }

    public static Optional<OrderItemDataReplacementField> fromHeader(String header) {
        return Optional.ofNullable(BY_NORMALIZED_HEADER.get(normalizeHeader(header)));
    }

    public static List<OrderItemDataReplacementField> replaceableFields() {
        return REPLACEABLE_FIELDS;
    }

    public static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[\\s\\u00A0\\u3000]+", "").trim();
    }

    public enum ValueType {
        ITEM_ID,
        MATCH_KEY,
        DATE,
        LEAD_TIME,
        POSITIVE_INTEGER,
        TEXT,
        ORDER_TYPE
    }
}
