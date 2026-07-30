package com.machining.quote.storage;

import java.util.Locale;

public enum StorageType {
    LOCAL,
    MINIO;

    public static StorageType fromCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Storage type cannot be blank");
        }
        try {
            return StorageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported storage type: " + value, e);
        }
    }
}
