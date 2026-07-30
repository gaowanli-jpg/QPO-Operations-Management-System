package com.machining.quote.integration;

import java.util.Locale;

public enum ExternalAuthType {
    NONE,
    BEARER_TOKEN,
    U9_AUTH_LOGIN;

    public static ExternalAuthType fromCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        try {
            return ExternalAuthType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported external auth type: " + value, e);
        }
    }
}
