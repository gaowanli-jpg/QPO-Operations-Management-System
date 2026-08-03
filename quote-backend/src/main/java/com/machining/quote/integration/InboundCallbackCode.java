package com.machining.quote.integration;

import java.util.Locale;

public enum InboundCallbackCode {
    MES_MATERIAL_CALLBACK;

    public static InboundCallbackCode fromCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Inbound callback code cannot be blank");
        }
        try {
            return InboundCallbackCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported inbound callback code: " + value, e);
        }
    }
}
