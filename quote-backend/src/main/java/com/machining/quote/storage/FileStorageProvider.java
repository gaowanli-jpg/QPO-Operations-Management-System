package com.machining.quote.storage;

import org.springframework.core.io.InputStreamResource;

import java.util.Optional;

public interface FileStorageProvider {
    StorageType storageType();

    StoredFile save(FileSaveCommand command);

    InputStreamResource open(FileStorageConfig config, String objectKey);

    boolean exists(FileStorageConfig config, String objectKey);

    void delete(FileStorageConfig config, String objectKey);

    StorageTestResult test(FileStorageConfig config);

    Optional<String> externalReadUrl(FileStorageConfig config, String objectKey);
}
