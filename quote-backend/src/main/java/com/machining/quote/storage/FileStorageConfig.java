package com.machining.quote.storage;

public record FileStorageConfig(
        Long id,
        String name,
        StorageType storageType,
        boolean active,
        boolean enabled,
        String rootDir,
        String endpoint,
        String bucket,
        String accessKey,
        String accessSecret,
        String region,
        boolean pathStyle,
        boolean publicReadEnabled,
        String publicBaseUrl
) {
    public static FileStorageConfig local(Long id, String name, String rootDir, String publicBaseUrl) {
        return new FileStorageConfig(
                id,
                isBlank(name) ? "Local storage" : name.trim(),
                StorageType.LOCAL,
                true,
                true,
                require(rootDir, "Local rootDir cannot be blank"),
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                normalizeOptional(publicBaseUrl)
        );
    }

    public static FileStorageConfig minio(
            Long id,
            String name,
            String endpoint,
            String bucket,
            String accessKey,
            String accessSecret,
            String region,
            boolean pathStyle,
            boolean publicReadEnabled,
            String publicBaseUrl) {
        return new FileStorageConfig(
                id,
                isBlank(name) ? "MinIO storage" : name.trim(),
                StorageType.MINIO,
                true,
                true,
                null,
                require(endpoint, "MinIO endpoint cannot be blank"),
                require(bucket, "MinIO bucket cannot be blank"),
                require(accessKey, "MinIO accessKey cannot be blank"),
                require(accessSecret, "MinIO accessSecret cannot be blank"),
                normalizeOptional(region),
                pathStyle,
                publicReadEnabled,
                normalizeOptional(publicBaseUrl)
        );
    }

    private static String require(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
