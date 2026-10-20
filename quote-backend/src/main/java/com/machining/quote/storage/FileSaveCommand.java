package com.machining.quote.storage;

import java.io.InputStream;

public record FileSaveCommand(
        FileStorageConfig config,
        String objectKey,
        InputStream inputStream,
        long sizeBytes,
        String contentType,
        String originalFilename
) {
    public FileSaveCommand {
        if (config == null) {
            throw new IllegalArgumentException("File storage config cannot be null");
        }
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Object key cannot be blank");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
    }
}
