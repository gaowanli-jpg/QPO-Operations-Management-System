package com.machining.quote.storage;

public record StorageTestResult(boolean success, String message, String objectKey) {
    public static StorageTestResult success(String message, String objectKey) {
        return new StorageTestResult(true, message, objectKey);
    }

    public static StorageTestResult failed(String message, String objectKey) {
        return new StorageTestResult(false, message, objectKey);
    }
}
