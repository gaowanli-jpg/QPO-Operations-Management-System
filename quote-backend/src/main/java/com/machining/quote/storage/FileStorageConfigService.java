package com.machining.quote.storage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.SystemFileStorageConfig;
import com.machining.quote.mapper.SystemFileStorageConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FileStorageConfigService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemFileStorageConfigMapper configMapper;

    @Autowired
    public FileStorageConfigService(SystemFileStorageConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public FileStorageConfig activeConfig() {
        QueryWrapper<SystemFileStorageConfig> query = new QueryWrapper<>();
        query.eq("active", true)
                .eq("enabled", true)
                .last("LIMIT 1");
        SystemFileStorageConfig entity = configMapper.selectOne(query);
        if (entity == null) {
            throw new IllegalStateException("No active file storage config found");
        }
        return toDomain(entity);
    }

    public FileStorageConfig configById(Long id) {
        if (id == null) {
            return activeConfig();
        }
        SystemFileStorageConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalStateException("File storage config not found id=" + id);
        }
        return toDomain(entity);
    }

    public FileStorageConfig toDomain(SystemFileStorageConfig entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Storage config entity cannot be null");
        }
        StorageType type = StorageType.fromCode(entity.getStorageType());
        Map<String, Object> config = configMap(entity.getConfigJson());
        if (type == StorageType.LOCAL) {
            return new FileStorageConfig(
                    entity.getId(),
                    entity.getName(),
                    StorageType.LOCAL,
                    Boolean.TRUE.equals(entity.getActive()),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    text(config, "rootDir", "/data/uploads"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    bool(config, "pathStyle", true),
                    bool(config, "publicReadEnabled", false),
                    text(config, "publicBaseUrl", null)
            );
        }
        return new FileStorageConfig(
                entity.getId(),
                entity.getName(),
                StorageType.MINIO,
                Boolean.TRUE.equals(entity.getActive()),
                Boolean.TRUE.equals(entity.getEnabled()),
                null,
                text(config, "endpoint", null),
                text(config, "bucket", null),
                text(config, "accessKey", null),
                text(config, "accessSecret", null),
                text(config, "region", null),
                bool(config, "pathStyle", true),
                bool(config, "publicReadEnabled", false),
                text(config, "publicBaseUrl", null)
        );
    }

    public Map<String, Object> redactedConfig(FileStorageConfig config) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", config.id());
        result.put("name", config.name());
        result.put("storageType", config.storageType().name());
        result.put("active", config.active());
        result.put("enabled", config.enabled());
        if (config.storageType() == StorageType.LOCAL) {
            result.put("rootDir", config.rootDir());
            result.put("publicBaseUrl", config.publicBaseUrl());
            return result;
        }
        result.put("endpoint", config.endpoint());
        result.put("bucket", config.bucket());
        result.put("accessKey", config.accessKey());
        result.put("accessSecret", isBlank(config.accessSecret()) ? null : "******");
        result.put("region", config.region());
        result.put("pathStyle", config.pathStyle());
        result.put("publicReadEnabled", config.publicReadEnabled());
        result.put("publicBaseUrl", config.publicBaseUrl());
        return result;
    }

    private Map<String, Object> configMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> raw) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value instanceof String text) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid storage config JSON", e);
            }
        }
        return OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
        });
    }

    private String text(Map<String, Object> config, String key, String fallback) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private boolean bool(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
