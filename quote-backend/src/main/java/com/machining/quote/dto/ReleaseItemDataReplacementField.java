package com.machining.quote.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum ReleaseItemDataReplacementField {
    PART_NO("图号", "partNo", ValueType.MATCH_KEY, 100, false),
    VERSION("版本", "version", ValueType.MATCH_KEY, 20, false),
    INTERNAL_TARGET_LEAD_TIME("内部目标交期", "internalTargetLeadTime", ValueType.DATE, null, true),
    ACCEPTED_QTY("通过数量", "acceptedQty", ValueType.POSITIVE_INTEGER, null, false),
    MATERIAL_NAME("材料名称", "materialName", ValueType.TEXT, 500, true),
    SURFACE_TREATMENT("表面处理", "surfaceTreatment", ValueType.TEXT, 200, true),
    REMARK("备注", "remark", ValueType.TEXT, 500, true),
    SALES_ORDER_DATE("下单日期", "salesOrderDate", ValueType.DATE, null, true),
    CUSTOMER_ORDER_NO("客户订单号", "customerOrderNo", ValueType.TEXT, 120, true),
    CUSTOMER_PROJECT_NO("客户项目号", "customerProjectNo", ValueType.TEXT, 120, true),
    CUSTOMER_ORDER_TITLE("客户下单抬头", "customerOrderTitle", ValueType.TEXT, 255, true),
    ORDER_TYPE("订单类型", "orderType", ValueType.ORDER_TYPE, 20, true),
    INTERNAL_PROJECT_NO("内部项目号", "internalProjectNo", ValueType.TEXT, 120, true);

    private static final List<ReleaseItemDataReplacementField> REQUIRED_MATCH_KEYS =
            List.of(PART_NO, VERSION);
    private static final List<ReleaseItemDataReplacementField> REPLACEABLE_FIELDS = Arrays.stream(values())
            .filter(ReleaseItemDataReplacementField::isReplaceable)
            .toList();
    private static final List<ReleaseItemDataReplacementField> PERSISTENT_FIELDS = Arrays.stream(values())
            .filter(ReleaseItemDataReplacementField::isPersistent)
            .toList();
    private static final Map<String, ReleaseItemDataReplacementField> BY_NORMALIZED_HEADER;

    static {
        Map<String, ReleaseItemDataReplacementField> fieldsByHeader = new LinkedHashMap<>();
        for (ReleaseItemDataReplacementField field : values()) {
            fieldsByHeader.put(normalizeHeader(field.header), field);
        }
        BY_NORMALIZED_HEADER = Collections.unmodifiableMap(fieldsByHeader);
    }

    private final String header;
    private final String propertyName;
    private final ValueType valueType;
    private final Integer maxLength;
    private final boolean persistent;

    ReleaseItemDataReplacementField(
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

    public boolean isMatchKey() {
        return valueType == ValueType.MATCH_KEY;
    }

    public boolean isReplaceable() {
        return !isMatchKey();
    }

    public static Optional<ReleaseItemDataReplacementField> fromHeader(String header) {
        return Optional.ofNullable(BY_NORMALIZED_HEADER.get(normalizeHeader(header)));
    }

    public static List<ReleaseItemDataReplacementField> requiredMatchKeys() {
        return REQUIRED_MATCH_KEYS;
    }

    public static List<ReleaseItemDataReplacementField> replaceableFields() {
        return REPLACEABLE_FIELDS;
    }

    public static List<ReleaseItemDataReplacementField> persistentFields() {
        return PERSISTENT_FIELDS;
    }

    public static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[\\s\\u00A0\\u3000]+", "").trim();
    }

    public enum ValueType {
        MATCH_KEY,
        DATE,
        POSITIVE_INTEGER,
        TEXT,
        ORDER_TYPE
    }
}
