package com.machining.quote.integration;

import java.util.Locale;

public enum InboundCallbackCode {
    MES_MATERIAL_CALLBACK(
            "MES",
            "MES物料号回调",
            "/api/integrations/mes/material-callback",
            "mes-material-callback"),
    SALES_ORDER_SYNC_QUERY(
            "EXTERNAL_SALES_ORDER_SYNC",
            "销售订单外部同步查询",
            "/api/integrations/sales-orders",
            "sales-order-sync-query");

    private final String sourceSystemCode;
    private final String displayName;
    private final String callbackPath;
    private final String apiCode;

    InboundCallbackCode(String sourceSystemCode, String displayName, String callbackPath, String apiCode) {
        this.sourceSystemCode = sourceSystemCode;
        this.displayName = displayName;
        this.callbackPath = callbackPath;
        this.apiCode = apiCode;
    }

    public String getSourceSystemCode() {
        return sourceSystemCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCallbackPath() {
        return callbackPath;
    }

    public String getApiCode() {
        return apiCode;
    }

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

    public static InboundCallbackCode fromApiCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Inbound callback interface code cannot be blank");
        }
        String normalized = value.trim();
        for (InboundCallbackCode code : values()) {
            if (code.apiCode.equalsIgnoreCase(normalized) || code.name().equalsIgnoreCase(normalized)) {
                return code;
            }
        }
        return fromCode(normalized.replace('-', '_'));
    }
}
