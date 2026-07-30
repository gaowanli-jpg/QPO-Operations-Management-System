package com.machining.quote.integration;

import java.util.Locale;

public enum ExternalSystemCode {
    U9,
    MES,
    WMS;

    public static ExternalSystemCode fromCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("External system code cannot be blank");
        }
        try {
            return ExternalSystemCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported external system code: " + value, e);
        }
    }
}
