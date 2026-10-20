package com.machining.quote.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FileStorageRouter {
    private final FileStorageConfigService configService;
    private final Map<StorageType, FileStorageProvider> providers;

    @Autowired
    public FileStorageRouter(FileStorageConfigService configService, List<FileStorageProvider> providers) {
        this.configService = configService;
        this.providers = new EnumMap<>(StorageType.class);
        for (FileStorageProvider provider : providers) {
            this.providers.put(provider.storageType(), provider);
        }
    }

    public StoredFile save(FileSaveCommand command) {
        return provider(command.config().storageType()).save(command);
    }

    public StoredFile saveActive(String objectKey, java.io.InputStream inputStream, long sizeBytes, String contentType, String originalFilename) {
        FileStorageConfig config = configService.activeConfig();
        return save(new FileSaveCommand(config, objectKey, inputStream, sizeBytes, contentType, originalFilename));
    }

    public InputStreamResource open(StorageType storageType, Long storageConfigId, String objectKey) {
        FileStorageConfig config = config(storageType, storageConfigId);
        return provider(config.storageType()).open(config, objectKey);
    }

    public boolean exists(StorageType storageType, Long storageConfigId, String objectKey) {
        FileStorageConfig config = config(storageType, storageConfigId);
        return provider(config.storageType()).exists(config, objectKey);
    }

    public void delete(StorageType storageType, Long storageConfigId, String objectKey) {
        FileStorageConfig config = config(storageType, storageConfigId);
        provider(config.storageType()).delete(config, objectKey);
    }

    public StorageTestResult test(FileStorageConfig config) {
        return provider(config.storageType()).test(config);
    }

    public Optional<String> externalReadUrl(StorageType storageType, Long storageConfigId, String objectKey) {
        FileStorageConfig config = config(storageType, storageConfigId);
        return provider(config.storageType()).externalReadUrl(config, objectKey);
    }

    private FileStorageConfig config(StorageType expectedType, Long storageConfigId) {
        FileStorageConfig config = storageConfigId == null ? configService.activeConfig() : configService.configById(storageConfigId);
        if (expectedType != null && config.storageType() != expectedType) {
            throw new IllegalStateException("Storage config type mismatch expected=" + expectedType + " actual=" + config.storageType());
        }
        return config;
    }

    private FileStorageProvider provider(StorageType type) {
        FileStorageProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException("No file storage provider registered for " + type);
        }
        return provider;
    }
}
