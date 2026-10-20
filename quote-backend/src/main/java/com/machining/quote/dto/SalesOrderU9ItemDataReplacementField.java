package com.machining.quote.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum SalesOrderU9ItemDataReplacementField {
    PART_NO("图号", "partNo", ValueType.TEXT, 100, false),
    VERSION("版本", "version", ValueType.TEXT, 20, false),
    LINE_NO("行号", "lineNo", ValueType.POSITIVE_INTEGER, null, false),
    QTY("数量", "qty", ValueType.POSITIVE_INTEGER, null, true, "qty"),
    CUSTOMER_DELIVERY_DATE("客户交期", "customerDeliveryDate", ValueType.DATE, null, true),
    INTERNAL_DELIVERY_DATE("内部交期", "internalDeliveryDate", ValueType.DATE, null, true),
    UNTAXED_UNIT_PRICE("未税单价", "untaxedUnitPrice", ValueType.NON_NEGATIVE_DECIMAL, null, true),
    TAX_INCLUDED_UNIT_PRICE("含税单价", "taxIncludedUnitPrice", ValueType.NON_NEGATIVE_DECIMAL, null, true, "VAT单价-含税"),
    CUSTOMER_ORDER_NO("客户订单号", "customerOrderNo", ValueType.TEXT, 120, true, "客户采购订单号"),
    CUSTOMER_PROJECT_NO("客户项目号", "customerProjectNo", ValueType.TEXT, 120, true),
    INTERNAL_PROJECT_NO("内部项目号", "internalProjectNo", ValueType.TEXT, 120, true),
    REMARK("备注", "remark", ValueType.TEXT, 500, true);

    private static final List<SalesOrderU9ItemDataReplacementField> REQUIRED_MATCH_KEYS = List.of(PART_NO, VERSION);
    private static final List<SalesOrderU9ItemDataReplacementField> REPLACEABLE_FIELDS = Arrays.stream(values())
            .filter(SalesOrderU9ItemDataReplacementField::isReplaceable)
            .toList();
    private static final Map<String, SalesOrderU9ItemDataReplacementField> BY_NORMALIZED_HEADER;

    static {
        Map<String, SalesOrderU9ItemDataReplacementField> fieldsByHeader = new LinkedHashMap<>();
        for (SalesOrderU9ItemDataReplacementField field : values()) {
            fieldsByHeader.put(normalizeHeader(field.header), field);
            for (String alias : field.aliases) {
                fieldsByHeader.put(normalizeHeader(alias), field);
            }
        }
        BY_NORMALIZED_HEADER = Collections.unmodifiableMap(fieldsByHeader);
    }

    private final String header;
    private final String propertyName;
    private final ValueType valueType;
    private final Integer maxLength;
    private final boolean replaceable;
    private final List<String> aliases;

    SalesOrderU9ItemDataReplacementField(
            String header,
            String propertyName,
            ValueType valueType,
            Integer maxLength,
            boolean replaceable,
            String... aliases) {
        this.header = header;
        this.propertyName = propertyName;
        this.valueType = valueType;
        this.maxLength = maxLength;
        this.replaceable = replaceable;
        this.aliases = aliases == null ? List.of() : List.of(aliases);
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

    public boolean isReplaceable() {
        return replaceable;
    }

    public static Optional<SalesOrderU9ItemDataReplacementField> fromHeader(String header) {
        return Optional.ofNullable(BY_NORMALIZED_HEADER.get(normalizeHeader(header)));
    }

    public static List<SalesOrderU9ItemDataReplacementField> requiredMatchKeys() {
        return REQUIRED_MATCH_KEYS;
    }

    public static List<SalesOrderU9ItemDataReplacementField> replaceableFields() {
        return REPLACEABLE_FIELDS;
    }

    public static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[\\s\\u00A0\\u3000]+", "").trim();
    }

    public enum ValueType {
        TEXT,
        DATE,
        POSITIVE_INTEGER,
        NON_NEGATIVE_DECIMAL
    }
}
