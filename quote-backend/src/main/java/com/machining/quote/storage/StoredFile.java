package com.machining.quote.storage;

import java.util.Optional;

public record StoredFile(
        StorageType storageType,
        Long storageConfigId,
        String objectKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String externalUrl
) {
    public Optional<String> externalUrlOptional() {
        return Optional.ofNullable(externalUrl).filter(value -> !value.isBlank());
    }
}
