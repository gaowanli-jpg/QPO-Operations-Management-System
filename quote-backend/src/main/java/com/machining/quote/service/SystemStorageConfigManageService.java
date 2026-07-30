package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SystemStorageConfigDTO;
import com.machining.quote.dto.SystemStorageConfigSaveReq;
import com.machining.quote.entity.SystemFileStorageConfig;
import com.machining.quote.mapper.SystemFileStorageConfigMapper;
import com.machining.quote.storage.FileStorageConfig;
import com.machining.quote.storage.FileStorageConfigService;
import com.machining.quote.storage.FileStorageTestService;
import com.machining.quote.storage.StorageTestResult;
import com.machining.quote.storage.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class SystemStorageConfigManageService {
    private static final Logger log = LoggerFactory.getLogger(SystemStorageConfigManageService.class);
    private static final String MASKED_SECRET = "******";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(accessSecret|secretKey|minioSecret|token|authorization)\\s*[:=]\\s*[^,;\\s]+"
    );

    @Autowired
    private SystemFileStorageConfigMapper configMapper;
    @Autowired
    private FileStorageConfigService configService;
    @Autowired
    private FileStorageTestService testService;
    @Autowired
    private AuditService auditService;

    public List<SystemStorageConfigDTO> listConfigs() {
        QueryWrapper<SystemFileStorageConfig> query = new QueryWrapper<>();
        query.orderByDesc("active")
                .orderByDesc("enabled")
                .orderByDesc("updated_at")
                .orderByAsc("id");
        return configMapper.selectList(query).stream().map(this::toDTO).toList();
    }

    public SystemStorageConfigDTO activeConfig() {
        QueryWrapper<SystemFileStorageConfig> query = new QueryWrapper<>();
        query.eq("active", true)
                .eq("enabled", true)
                .last("LIMIT 1");
        SystemFileStorageConfig entity = configMapper.selectOne(query);
        if (entity == null) {
            log.warn("No active file storage config found");
            return null;
        }
        return toDTO(entity);
    }

    @Transactional
    public SystemStorageConfigDTO saveConfig(Long id, SystemStorageConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Storage config request cannot be null");
        }
        boolean create = id == null;
        SystemFileStorageConfig existing = create ? null : requireConfig(id);
        StorageType storageType = resolveStorageType(existing, req);
        String name = requireName(req.getName());
        Map<String, Object> beforeAudit = create ? null : auditSummary(existing, false);
        LinkedHashMap<String, Object> oldConfigJson = create ? null : normalizeConfig(existing);
        LinkedHashMap<String, Object> configJson = buildConfigJson(storageType, req, existing, create);
        boolean connectionChanged = existing != null && !Objects.equals(oldConfigJson, configJson);
        boolean secretChanged = storageType == StorageType.MINIO && !isBlank(req.getAccessSecret()) && !isMaskedSecret(req.getAccessSecret());

        SystemFileStorageConfig entity = create ? new SystemFileStorageConfig() : existing;
        entity.setName(name);
        entity.setStorageType(storageType.name());
        entity.setEnabled(req.getEnabled() == null ? (create || Boolean.TRUE.equals(existing.getEnabled())) : req.getEnabled());
        entity.setConfigJson(configJson);
        entity.setRemark(trimToNull(req.getRemark()));
        entity.setUpdatedAt(LocalDateTime.now());
        if (create) {
            entity.setActive(false);
            entity.setCreatedAt(entity.getUpdatedAt());
            configMapper.insert(entity);
            auditService.record("SYSTEM_STORAGE_CONFIG_CREATE", "system_file_storage_config", entity.getId(), null, auditSummary(entity, secretChanged));
            log.info("Storage config created id={} storageType={} active={} enabled={}",
                    entity.getId(), entity.getStorageType(), entity.getActive(), entity.getEnabled());
        } else {
            if (connectionChanged) {
                entity.setLastTestStatus(null);
                entity.setLastTestMessage(null);
                entity.setLastTestAt(null);
            }
            configMapper.updateById(entity);
            auditService.record("SYSTEM_STORAGE_CONFIG_UPDATE", "system_file_storage_config", entity.getId(), beforeAudit, auditSummary(entity, secretChanged));
            log.info("Storage config updated id={} storageType={} active={} enabled={} connectionChanged={}",
                    entity.getId(), entity.getStorageType(), entity.getActive(), entity.getEnabled(), connectionChanged);
        }
        return toDTO(entity);
    }

    public Map<String, Object> testConfig(Long id) {
        SystemFileStorageConfig entity = requireConfig(id);
        try {
            FileStorageConfig config = configService.toDomain(entity);
            StorageTestResult raw = testService.test(config);
            String status = raw.success() ? "SUCCESS" : "FAILED";
            StorageTestResult result = new StorageTestResult(
                    raw.success(),
                    sanitizeMessage(raw.message(), entity),
                    sanitizeMessage(raw.objectKey(), entity)
            );
            updateTestResult(entity.getId(), status, result.message());
            if (raw.success()) {
                auditService.record("SYSTEM_STORAGE_CONFIG_TEST", "system_file_storage_config", id, null, testSummary(entity, result));
            } else {
                auditService.record(
                        "SYSTEM_STORAGE_CONFIG_TEST_FAILED",
                        "system_file_storage_config",
                        id,
                        null,
                        testSummary(entity, result),
                        "SYSTEM",
                        "FAILED",
                        "Storage config test failed",
                        result.message()
                );
            }
            log.info("Storage config test completed id={} storageType={} success={}", id, entity.getStorageType(), raw.success());
            return response(raw.success(), result.message(), result);
        } catch (RuntimeException e) {
            String message = sanitizeMessage(e.getMessage(), entity);
            updateTestResult(entity.getId(), "FAILED", message);
            auditService.record(
                    "SYSTEM_STORAGE_CONFIG_TEST_FAILED",
                    "system_file_storage_config",
                    id,
                    null,
                    Map.of("id", id, "storageType", entity.getStorageType(), "message", message),
                    "SYSTEM",
                    "FAILED",
                    "Storage config test failed",
                    message
            );
            log.warn("Storage config test failed id={} storageType={} message={}", id, entity.getStorageType(), message);
            return response(false, message, null);
        }
    }

    @Transactional
    public SystemStorageConfigDTO activateConfig(Long id) {
        SystemFileStorageConfig entity = requireConfig(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalStateException("Storage config is disabled");
        }
        if (!"SUCCESS".equalsIgnoreCase(nullToEmpty(entity.getLastTestStatus()))) {
            throw new IllegalStateException("Storage config must pass test before activation");
        }

        SystemFileStorageConfig deactivate = new SystemFileStorageConfig();
        deactivate.setActive(false);
        deactivate.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<SystemFileStorageConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("active", true);
        configMapper.update(deactivate, wrapper);

        entity.setActive(true);
        entity.setEnabled(true);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_STORAGE_CONFIG_ACTIVATE", "system_file_storage_config", id, null, auditSummary(entity, false));
        log.info("Storage config activated id={} storageType={}", id, entity.getStorageType());
        return toDTO(entity);
    }

    @Transactional
    public SystemStorageConfigDTO disableConfig(Long id) {
        SystemFileStorageConfig entity = requireConfig(id);
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("Cannot disable active storage config");
        }
        entity.setEnabled(false);
        entity.setActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_STORAGE_CONFIG_DISABLE", "system_file_storage_config", id, null, auditSummary(entity, false));
        log.info("Storage config disabled id={} storageType={}", id, entity.getStorageType());
        return toDTO(entity);
    }

    private SystemStorageConfigDTO toDTO(SystemFileStorageConfig entity) {
        FileStorageConfig config = configService.toDomain(entity);
        SystemStorageConfigDTO dto = new SystemStorageConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setStorageType(entity.getStorageType());
        dto.setActive(Boolean.TRUE.equals(entity.getActive()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setRootDir(config.rootDir());
        dto.setEndpoint(config.endpoint());
        dto.setBucket(config.bucket());
        dto.setAccessKey(config.accessKey());
        dto.setAccessSecret(isBlank(config.accessSecret()) ? null : MASKED_SECRET);
        dto.setRegion(config.region());
        dto.setPathStyle(config.pathStyle());
        dto.setPublicReadEnabled(config.publicReadEnabled());
        dto.setPublicBaseUrl(config.publicBaseUrl());
        dto.setRemark(entity.getRemark());
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestAt(entity.getLastTestAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private SystemFileStorageConfig requireConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Storage config id cannot be null");
        }
        SystemFileStorageConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Storage config not found id=" + id);
        }
        return entity;
    }

    private StorageType resolveStorageType(SystemFileStorageConfig existing, SystemStorageConfigSaveReq req) {
        if (existing != null) {
            StorageType oldType = StorageType.fromCode(existing.getStorageType());
            if (!isBlank(req.getStorageType()) && StorageType.fromCode(req.getStorageType()) != oldType) {
                throw new IllegalArgumentException("Storage type cannot be changed");
            }
            return oldType;
        }
        return StorageType.fromCode(req.getStorageType());
    }

    private String requireName(String value) {
        String name = trimToNull(value);
        if (name == null) {
            throw new IllegalArgumentException("Storage config name cannot be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Storage config name cannot exceed 100 characters");
        }
        return name;
    }

    private LinkedHashMap<String, Object> buildConfigJson(
            StorageType storageType,
            SystemStorageConfigSaveReq req,
            SystemFileStorageConfig existing,
            boolean create) {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        if (storageType == StorageType.LOCAL) {
            config.put("rootDir", requireText(req.getRootDir(), "Local rootDir cannot be blank"));
            config.put("pathStyle", valueOrDefault(req.getPathStyle(), true));
            config.put("publicReadEnabled", valueOrDefault(req.getPublicReadEnabled(), false));
            putIfPresent(config, "publicBaseUrl", req.getPublicBaseUrl());
            return config;
        }

        config.put("endpoint", requireText(req.getEndpoint(), "MinIO endpoint cannot be blank"));
        config.put("bucket", requireText(req.getBucket(), "MinIO bucket cannot be blank"));
        config.put("accessKey", requireText(req.getAccessKey(), "MinIO accessKey cannot be blank"));
        String secret = resolveMinioSecret(req, existing, create);
        config.put("accessSecret", secret);
        putIfPresent(config, "region", req.getRegion());
        config.put("pathStyle", valueOrDefault(req.getPathStyle(), true));
        config.put("publicReadEnabled", valueOrDefault(req.getPublicReadEnabled(), false));
        putIfPresent(config, "publicBaseUrl", req.getPublicBaseUrl());
        return config;
    }

    private String resolveMinioSecret(SystemStorageConfigSaveReq req, SystemFileStorageConfig existing, boolean create) {
        if (!isBlank(req.getAccessSecret()) && !isMaskedSecret(req.getAccessSecret())) {
            return req.getAccessSecret().trim();
        }
        if (!create && existing != null) {
            String oldSecret = configService.toDomain(existing).accessSecret();
            if (!isBlank(oldSecret)) {
                return oldSecret;
            }
        }
        throw new IllegalArgumentException("MinIO accessSecret cannot be blank");
    }

    private LinkedHashMap<String, Object> normalizeConfig(SystemFileStorageConfig entity) {
        FileStorageConfig config = configService.toDomain(entity);
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (config.storageType() == StorageType.LOCAL) {
            normalized.put("rootDir", config.rootDir());
            normalized.put("pathStyle", config.pathStyle());
            normalized.put("publicReadEnabled", config.publicReadEnabled());
            putIfPresent(normalized, "publicBaseUrl", config.publicBaseUrl());
            return normalized;
        }
        normalized.put("endpoint", config.endpoint());
        normalized.put("bucket", config.bucket());
        normalized.put("accessKey", config.accessKey());
        normalized.put("accessSecret", config.accessSecret());
        putIfPresent(normalized, "region", config.region());
        normalized.put("pathStyle", config.pathStyle());
        normalized.put("publicReadEnabled", config.publicReadEnabled());
        putIfPresent(normalized, "publicBaseUrl", config.publicBaseUrl());
        return normalized;
    }

    private Map<String, Object> auditSummary(SystemFileStorageConfig entity, boolean secretChanged) {
        FileStorageConfig config = configService.toDomain(entity);
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("name", entity.getName());
        summary.put("storageType", entity.getStorageType());
        summary.put("active", Boolean.TRUE.equals(entity.getActive()));
        summary.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        if (config.storageType() == StorageType.LOCAL) {
            summary.put("rootDir", config.rootDir());
        } else {
            summary.put("endpoint", config.endpoint());
            summary.put("bucket", config.bucket());
            summary.put("accessKey", config.accessKey());
            summary.put("secretChanged", secretChanged);
            summary.put("publicReadEnabled", config.publicReadEnabled());
            summary.put("publicBaseUrl", config.publicBaseUrl());
        }
        return summary;
    }

    private Map<String, Object> testSummary(SystemFileStorageConfig entity, StorageTestResult result) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("storageType", entity.getStorageType());
        summary.put("success", result.success());
        summary.put("message", result.message());
        summary.put("objectKey", result.objectKey());
        return summary;
    }

    private void updateTestResult(Long id, String status, String message) {
        SystemFileStorageConfig update = new SystemFileStorageConfig();
        update.setId(id);
        update.setLastTestStatus(status);
        update.setLastTestMessage(message);
        update.setLastTestAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(update);
    }

    private Map<String, Object> response(boolean success, String message, StorageTestResult result) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (result != null) {
            response.put("data", result);
        }
        return response;
    }

    private String sanitizeMessage(String message, SystemFileStorageConfig entity) {
        String text = trimToNull(message);
        if (text == null) {
            return "Storage config test failed";
        }
        try {
            FileStorageConfig config = configService.toDomain(entity);
            if (!isBlank(config.accessSecret())) {
                text = text.replace(config.accessSecret(), MASKED_SECRET);
            }
        } catch (RuntimeException ignored) {
            // Keep the original message when the stored config itself is invalid.
        }
        return SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("$1=" + MASKED_SECRET);
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private boolean isMaskedSecret(String value) {
        return MASKED_SECRET.equals(value == null ? null : value.trim());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
