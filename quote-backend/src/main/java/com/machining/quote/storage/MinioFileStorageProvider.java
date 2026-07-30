package com.machining.quote.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class MinioFileStorageProvider implements FileStorageProvider {
    @Override
    public StorageType storageType() {
        return StorageType.MINIO;
    }

    @Override
    public StoredFile save(FileSaveCommand command) {
        FileStorageConfig config = requireType(command.config());
        String objectKey = ObjectKeySupport.safeObjectKey(command.objectKey());
        try {
            client(config).putObject(PutObjectArgs.builder()
                    .bucket(config.bucket())
                    .object(objectKey)
                    .stream(command.inputStream(), command.sizeBytes(), -1L)
                    .contentType(command.contentType() == null ? "application/octet-stream" : command.contentType())
                    .build());
            return new StoredFile(
                    StorageType.MINIO,
                    config.id(),
                    objectKey,
                    command.originalFilename(),
                    command.contentType(),
                    command.sizeBytes(),
                    externalReadUrl(config, objectKey).orElse(null)
            );
        } catch (Exception e) {
            throw new IllegalStateException("MinIO file save failed objectKey=" + objectKey, e);
        }
    }

    @Override
    public InputStreamResource open(FileStorageConfig config, String objectKey) {
        FileStorageConfig minio = requireType(config);
        String safeKey = ObjectKeySupport.safeObjectKey(objectKey);
        try {
            return new InputStreamResource(client(minio).getObject(GetObjectArgs.builder()
                    .bucket(minio.bucket())
                    .object(safeKey)
                    .build()));
        } catch (Exception e) {
            throw new IllegalStateException("MinIO file open failed objectKey=" + safeKey, e);
        }
    }

    @Override
    public boolean exists(FileStorageConfig config, String objectKey) {
        FileStorageConfig minio = requireType(config);
        String safeKey = ObjectKeySupport.safeObjectKey(objectKey);
        try {
            client(minio).statObject(StatObjectArgs.builder()
                    .bucket(minio.bucket())
                    .object(safeKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("MinIO file stat failed objectKey=" + safeKey, e);
        }
    }

    @Override
    public void delete(FileStorageConfig config, String objectKey) {
        FileStorageConfig minio = requireType(config);
        String safeKey = ObjectKeySupport.safeObjectKey(objectKey);
        try {
            client(minio).removeObject(RemoveObjectArgs.builder()
                    .bucket(minio.bucket())
                    .object(safeKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO file delete failed objectKey=" + safeKey, e);
        }
    }

    @Override
    public StorageTestResult test(FileStorageConfig config) {
        FileStorageConfig minio = requireType(config);
        String objectKey = "_storage-test/" + UUID.randomUUID() + ".txt";
        byte[] payload = "qpo-minio-storage-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            MinioClient client = client(minio);
            boolean bucketExists = client.bucketExists(BucketExistsArgs.builder().bucket(minio.bucket()).build());
            if (!bucketExists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(minio.bucket()).build());
            }
            save(new FileSaveCommand(
                    minio,
                    objectKey,
                    new java.io.ByteArrayInputStream(payload),
                    payload.length,
                    "text/plain",
                    "qpo-minio-storage-test.txt"
            ));
            boolean saved = exists(minio, objectKey);
            delete(minio, objectKey);
            return saved
                    ? StorageTestResult.success("MinIO storage test completed", objectKey)
                    : StorageTestResult.failed("MinIO test file was not readable after save", objectKey);
        } catch (RuntimeException e) {
            return StorageTestResult.failed(e.getMessage(), objectKey);
        } catch (Exception e) {
            return StorageTestResult.failed("MinIO storage test failed: " + e.getMessage(), objectKey);
        }
    }

    @Override
    public Optional<String> externalReadUrl(FileStorageConfig config, String objectKey) {
        FileStorageConfig minio = requireType(config);
        String base = minio.publicBaseUrl();
        if (base == null || base.isBlank()) {
            base = minio.endpoint() + "/" + minio.bucket();
        }
        return Optional.ofNullable(ObjectKeySupport.joinUrl(base, objectKey));
    }

    private MinioClient client(FileStorageConfig config) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(config.endpoint())
                .credentials(config.accessKey(), config.accessSecret());
        if (config.region() != null && !config.region().isBlank()) {
            builder.region(config.region());
        }
        return builder.build();
    }

    private FileStorageConfig requireType(FileStorageConfig config) {
        if (config == null || config.storageType() != StorageType.MINIO) {
            throw new IllegalArgumentException("MINIO storage config required");
        }
        return config;
    }
}
