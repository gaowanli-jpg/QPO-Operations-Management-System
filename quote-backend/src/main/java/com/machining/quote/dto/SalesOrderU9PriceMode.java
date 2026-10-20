package com.machining.quote.dto;

import java.util.Locale;

public enum SalesOrderU9PriceMode {
    UNTAXED(false, "未税单价", "untaxedUnitPrice"),
    TAX_INCLUDED(true, "含税单价", "taxIncludedUnitPrice");

    private final boolean priceIncludeTax;
    private final String priceFieldLabel;
    private final String priceField;

    SalesOrderU9PriceMode(boolean priceIncludeTax, String priceFieldLabel, String priceField) {
        this.priceIncludeTax = priceIncludeTax;
        this.priceFieldLabel = priceFieldLabel;
        this.priceField = priceField;
    }

    public boolean isPriceIncludeTax() {
        return priceIncludeTax;
    }

    public String priceFieldLabel() {
        return priceFieldLabel;
    }

    public String priceField() {
        return priceField;
    }

    public static SalesOrderU9PriceMode fromCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TAX_INCLUDED;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        for (SalesOrderU9PriceMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("U9价格口径不支持：" + value);
    }

    public static SalesOrderU9PriceMode defaultMode(SalesOrderU9PriceMode mode) {
        return mode == null ? TAX_INCLUDED : mode;
    }
}
