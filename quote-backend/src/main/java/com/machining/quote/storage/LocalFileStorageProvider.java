package com.machining.quote.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Service
public class LocalFileStorageProvider implements FileStorageProvider {
    @Override
    public StorageType storageType() {
        return StorageType.LOCAL;
    }

    @Override
    public StoredFile save(FileSaveCommand command) {
        FileStorageConfig config = requireType(command.config());
        String objectKey = ObjectKeySupport.safeObjectKey(command.objectKey());
        Path file = resolve(config, objectKey);
        try {
            Files.createDirectories(file.getParent());
            try (InputStream input = command.inputStream()) {
                Files.copy(input, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(file);
            return new StoredFile(
                    StorageType.LOCAL,
                    config.id(),
                    objectKey,
                    command.originalFilename(),
                    command.contentType(),
                    size,
                    externalReadUrl(config, objectKey).orElse(null)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Local file save failed objectKey=" + objectKey, e);
        }
    }

    @Override
    public InputStreamResource open(FileStorageConfig config, String objectKey) {
        Path file = resolve(requireType(config), objectKey);
        try {
            return new InputStreamResource(Files.newInputStream(file));
        } catch (IOException e) {
            throw new IllegalStateException("Local file open failed objectKey=" + objectKey, e);
        }
    }

    @Override
    public boolean exists(FileStorageConfig config, String objectKey) {
        return Files.isRegularFile(resolve(requireType(config), objectKey));
    }

    @Override
    public void delete(FileStorageConfig config, String objectKey) {
        try {
            Files.deleteIfExists(resolve(requireType(config), objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("Local file delete failed objectKey=" + objectKey, e);
        }
    }

    @Override
    public StorageTestResult test(FileStorageConfig config) {
        FileStorageConfig local = requireType(config);
        String objectKey = "_storage-test/" + UUID.randomUUID() + ".txt";
        byte[] payload = "qpo-local-storage-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            StoredFile stored = save(new FileSaveCommand(
                    local,
                    objectKey,
                    new java.io.ByteArrayInputStream(payload),
                    payload.length,
                    "text/plain",
                    "qpo-local-storage-test.txt"
            ));
            if (!exists(local, stored.objectKey())) {
                return StorageTestResult.failed("Local test file was not readable after save", objectKey);
            }
            delete(local, objectKey);
            return StorageTestResult.success("Local storage test completed", objectKey);
        } catch (RuntimeException e) {
            return StorageTestResult.failed(e.getMessage(), objectKey);
        }
    }

    @Override
    public Optional<String> externalReadUrl(FileStorageConfig config, String objectKey) {
        return Optional.ofNullable(ObjectKeySupport.joinUrl(requireType(config).publicBaseUrl(), objectKey));
    }

    private Path resolve(FileStorageConfig config, String objectKey) {
        Path root = Path.of(config.rootDir()).toAbsolutePath().normalize();
        Path file = root.resolve(ObjectKeySupport.safeRelativePath(objectKey)).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Object key is outside local storage root");
        }
        return file;
    }

    private FileStorageConfig requireType(FileStorageConfig config) {
        if (config == null || config.storageType() != StorageType.LOCAL) {
            throw new IllegalArgumentException("LOCAL storage config required");
        }
        return config;
    }
}
