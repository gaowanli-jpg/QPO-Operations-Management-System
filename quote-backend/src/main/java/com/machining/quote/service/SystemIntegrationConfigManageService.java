package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SystemIntegrationConfigDTO;
import com.machining.quote.dto.SystemIntegrationConfigSaveReq;
import com.machining.quote.entity.SystemExternalIntegrationConfig;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalIntegrationTestResult;
import com.machining.quote.integration.ExternalIntegrationTestService;
import com.machining.quote.integration.ExternalSystemCode;
import com.machining.quote.mapper.SystemExternalIntegrationConfigMapper;
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
public class SystemIntegrationConfigManageService {
    private static final Logger log = LoggerFactory.getLogger(SystemIntegrationConfigManageService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MASKED_SECRET = "******";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(authToken|clientSecret|token|authorization|clientsecret)\\s*[:=]\\s*[^,;\\s]+"
    );

    @Autowired
    private SystemExternalIntegrationConfigMapper configMapper;
    @Autowired
    private ExternalIntegrationConfigService configService;
    @Autowired
    private ExternalIntegrationTestService testService;
    @Autowired
    private AuditService auditService;

    public List<SystemIntegrationConfigDTO> listConfigs(String systemCode) {
        QueryWrapper<SystemExternalIntegrationConfig> query = new QueryWrapper<>();
        if (!isBlank(systemCode)) {
            query.eq("system_code", ExternalSystemCode.fromCode(systemCode).name());
        }
        query.orderByAsc("system_code")
                .orderByDesc("active")
                .orderByDesc("enabled")
                .orderByDesc("updated_at")
                .orderByAsc("id");
        return configMapper.selectList(query).stream().map(configService::toDTO).toList();
    }

    public List<SystemIntegrationConfigDTO> activeConfigs() {
        QueryWrapper<SystemExternalIntegrationConfig> query = new QueryWrapper<>();
        query.eq("active", true)
                .eq("enabled", true)
                .orderByAsc("system_code")
                .orderByAsc("id");
        return configMapper.selectList(query).stream().map(configService::toDTO).toList();
    }

    @Transactional
    public SystemIntegrationConfigDTO saveConfig(Long id, SystemIntegrationConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Integration config request cannot be null");
        }
        boolean create = id == null;
        SystemExternalIntegrationConfig existing = create ? null : requireConfig(id);
        ExternalSystemCode systemCode = resolveSystemCode(existing, req);
        String name = requireName(req.getName());
        ExternalAuthType authType = resolveAuthType(systemCode, req);
        Map<String, Object> beforeAudit = create ? null : auditSummary(existing, false, false);
        LinkedHashMap<String, Object> oldConfigJson = create ? null : normalizeConfig(existing);
        LinkedHashMap<String, Object> configJson = buildConfigJson(systemCode, req, existing, create);
        boolean connectionChanged = existing != null && !Objects.equals(oldConfigJson, configJson);
        boolean authTokenChanged = hasNewSecret(req.getAuthToken());
        boolean clientSecretChanged = hasNewSecret(req.getClientSecret());

        SystemExternalIntegrationConfig entity = create ? new SystemExternalIntegrationConfig() : existing;
        entity.setName(name);
        entity.setSystemCode(systemCode.name());
        entity.setAuthType(authType.name());
        entity.setEnabled(req.getEnabled() == null ? (create || Boolean.TRUE.equals(existing.getEnabled())) : req.getEnabled());
        entity.setConfigJson(configJson);
        entity.setRemark(trimToNull(req.getRemark()));
        entity.setUpdatedAt(LocalDateTime.now());
        if (create) {
            entity.setActive(false);
            entity.setCreatedAt(entity.getUpdatedAt());
            configMapper.insert(entity);
            auditService.record(
                    "SYSTEM_INTEGRATION_CONFIG_CREATE",
                    "system_external_integration_config",
                    entity.getId(),
                    null,
                    auditSummary(entity, authTokenChanged, clientSecretChanged)
            );
            log.info("Integration config created id={} systemCode={} active={} enabled={}",
                    entity.getId(), entity.getSystemCode(), entity.getActive(), entity.getEnabled());
        } else {
            if (connectionChanged) {
                entity.setLastTestStatus(null);
                entity.setLastTestMessage(null);
                entity.setLastTestAt(null);
            }
            configMapper.updateById(entity);
            auditService.record(
                    "SYSTEM_INTEGRATION_CONFIG_UPDATE",
                    "system_external_integration_config",
                    entity.getId(),
                    beforeAudit,
                    auditSummary(entity, authTokenChanged, clientSecretChanged)
            );
            log.info("Integration config updated id={} systemCode={} active={} enabled={} connectionChanged={}",
                    entity.getId(), entity.getSystemCode(), entity.getActive(), entity.getEnabled(), connectionChanged);
        }
        return configService.toDTO(entity);
    }

    public Map<String, Object> testConfig(Long id) {
        SystemExternalIntegrationConfig entity = requireConfig(id);
        try {
            ExternalIntegrationConfig config = configService.toDomain(entity);
            ExternalIntegrationTestResult raw = testService.test(config);
            String status = raw.success() ? "SUCCESS" : "FAILED";
            ExternalIntegrationTestResult result = new ExternalIntegrationTestResult(
                    raw.success(),
                    sanitizeMessage(raw.message(), entity),
                    raw.httpStatus()
            );
            updateTestResult(entity.getId(), status, result.message());
            if (raw.success()) {
                auditService.record("SYSTEM_INTEGRATION_CONFIG_TEST", "system_external_integration_config", id, null, testSummary(entity, result));
            } else {
                auditService.record(
                        "SYSTEM_INTEGRATION_CONFIG_TEST_FAILED",
                        "system_external_integration_config",
                        id,
                        null,
                        testSummary(entity, result),
                        "SYSTEM",
                        "FAILED",
                        "Integration config test failed",
                        result.message()
                );
            }
            log.info("Integration config test completed id={} systemCode={} success={}", id, entity.getSystemCode(), raw.success());
            return response(raw.success(), result.message(), result);
        } catch (RuntimeException e) {
            String message = sanitizeMessage(e.getMessage(), entity);
            updateTestResult(entity.getId(), "FAILED", message);
            auditService.record(
                    "SYSTEM_INTEGRATION_CONFIG_TEST_FAILED",
                    "system_external_integration_config",
                    id,
                    null,
                    Map.of("id", id, "systemCode", entity.getSystemCode(), "message", message),
                    "SYSTEM",
                    "FAILED",
                    "Integration config test failed",
                    message
            );
            log.warn("Integration config test failed id={} systemCode={} message={}", id, entity.getSystemCode(), message);
            return response(false, message, null);
        }
    }

    @Transactional
    public SystemIntegrationConfigDTO activateConfig(Long id) {
        SystemExternalIntegrationConfig entity = requireConfig(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalStateException("Integration config is disabled");
        }
        if (!"SUCCESS".equalsIgnoreCase(nullToEmpty(entity.getLastTestStatus()))) {
            throw new IllegalStateException("Integration config must pass test before activation");
        }

        SystemExternalIntegrationConfig deactivate = new SystemExternalIntegrationConfig();
        deactivate.setActive(false);
        deactivate.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<SystemExternalIntegrationConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("system_code", entity.getSystemCode())
                .eq("active", true);
        configMapper.update(deactivate, wrapper);

        entity.setActive(true);
        entity.setEnabled(true);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_INTEGRATION_CONFIG_ACTIVATE", "system_external_integration_config", id, null, auditSummary(entity, false, false));
        log.info("Integration config activated id={} systemCode={}", id, entity.getSystemCode());
        return configService.toDTO(entity);
    }

    @Transactional
    public SystemIntegrationConfigDTO disableConfig(Long id) {
        SystemExternalIntegrationConfig entity = requireConfig(id);
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new IllegalStateException("Cannot disable active integration config");
        }
        entity.setEnabled(false);
        entity.setActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_INTEGRATION_CONFIG_DISABLE", "system_external_integration_config", id, null, auditSummary(entity, false, false));
        log.info("Integration config disabled id={} systemCode={}", id, entity.getSystemCode());
        return configService.toDTO(entity);
    }

    private SystemExternalIntegrationConfig requireConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Integration config id cannot be null");
        }
        SystemExternalIntegrationConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Integration config not found id=" + id);
        }
        return entity;
    }

    private ExternalSystemCode resolveSystemCode(SystemExternalIntegrationConfig existing, SystemIntegrationConfigSaveReq req) {
        if (existing != null) {
            ExternalSystemCode oldCode = ExternalSystemCode.fromCode(existing.getSystemCode());
            if (!isBlank(req.getSystemCode()) && ExternalSystemCode.fromCode(req.getSystemCode()) != oldCode) {
                throw new IllegalArgumentException("Integration system code cannot be changed");
            }
            return oldCode;
        }
        return ExternalSystemCode.fromCode(req.getSystemCode());
    }

    private ExternalAuthType resolveAuthType(ExternalSystemCode systemCode, SystemIntegrationConfigSaveReq req) {
        if (!isBlank(req.getAuthType())) {
            return ExternalAuthType.fromCode(req.getAuthType());
        }
        return systemCode == ExternalSystemCode.U9 ? ExternalAuthType.U9_AUTH_LOGIN : ExternalAuthType.NONE;
    }

    private String requireName(String value) {
        String name = trimToNull(value);
        if (name == null) {
            throw new IllegalArgumentException("Integration config name cannot be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Integration config name cannot exceed 100 characters");
        }
        return name;
    }

    private LinkedHashMap<String, Object> buildConfigJson(
            ExternalSystemCode systemCode,
            SystemIntegrationConfigSaveReq req,
            SystemExternalIntegrationConfig existing,
            boolean create) {
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("baseUrl", requireText(req.getBaseUrl(), systemCode.name() + " baseUrl cannot be blank"));
        switch (systemCode) {
            case MES -> {
                config.put("materialPath", defaultText(req.getMaterialPath(), "/api/mes/materials/resolve"));
                putIfPresent(config, "authToken", resolveOptionalSecret(req.getAuthToken(), existing, "authToken"));
                config.put("timeoutMs", positive(req.getTimeoutMs(), 10000));
            }
            case WMS -> {
                config.put("allocationPath", defaultText(req.getAllocationPath(), "/api/wms/inventory/allocate"));
                putIfPresent(config, "authToken", resolveOptionalSecret(req.getAuthToken(), existing, "authToken"));
                config.put("timeoutMs", positive(req.getTimeoutMs(), 10000));
            }
            case U9 -> {
                config.put("authLoginPath", defaultText(req.getAuthLoginPath(), "/webapi/OAuth2/AuthLogin"));
                config.put("salesOrderCreatePath", defaultText(req.getSalesOrderCreatePath(), "/webapi/SO/Create"));
                config.put("userCode", requireText(req.getUserCode(), "U9 userCode cannot be blank"));
                config.put("entCode", requireText(req.getEntCode(), "U9 entCode cannot be blank"));
                config.put("orgCode", requireText(req.getOrgCode(), "U9 orgCode cannot be blank"));
                config.put("clientId", requireText(req.getClientId(), "U9 clientId cannot be blank"));
                config.put("clientSecret", resolveRequiredSecret(req.getClientSecret(), existing, create, "clientSecret", "U9 clientSecret cannot be blank"));
                config.put("connectTimeoutMs", positive(req.getConnectTimeoutMs(), 5000));
                config.put("readTimeoutMs", positive(req.getReadTimeoutMs(), 30000));
            }
        }
        return config;
    }

    private LinkedHashMap<String, Object> normalizeConfig(SystemExternalIntegrationConfig entity) {
        ExternalIntegrationConfig config = configService.toDomain(entity);
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("baseUrl", config.baseUrl());
        switch (config.systemCode()) {
            case MES -> {
                normalized.put("materialPath", config.materialPath());
                putIfPresent(normalized, "authToken", config.authToken());
                normalized.put("timeoutMs", config.timeoutMs());
            }
            case WMS -> {
                normalized.put("allocationPath", config.allocationPath());
                putIfPresent(normalized, "authToken", config.authToken());
                normalized.put("timeoutMs", config.timeoutMs());
            }
            case U9 -> {
                normalized.put("authLoginPath", config.authLoginPath());
                normalized.put("salesOrderCreatePath", config.salesOrderCreatePath());
                normalized.put("userCode", config.userCode());
                normalized.put("entCode", config.entCode());
                normalized.put("orgCode", config.orgCode());
                normalized.put("clientId", config.clientId());
                normalized.put("clientSecret", config.clientSecret());
                normalized.put("connectTimeoutMs", config.connectTimeoutMs());
                normalized.put("readTimeoutMs", config.readTimeoutMs());
            }
        }
        return normalized;
    }

    private Map<String, Object> auditSummary(SystemExternalIntegrationConfig entity, boolean authTokenChanged, boolean clientSecretChanged) {
        ExternalIntegrationConfig config = configService.toDomain(entity);
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("name", entity.getName());
        summary.put("systemCode", entity.getSystemCode());
        summary.put("authType", entity.getAuthType());
        summary.put("active", Boolean.TRUE.equals(entity.getActive()));
        summary.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        summary.put("baseUrl", config.baseUrl());
        summary.put("authTokenChanged", authTokenChanged);
        summary.put("clientSecretChanged", clientSecretChanged);
        switch (config.systemCode()) {
            case MES -> {
                summary.put("materialPath", config.materialPath());
                summary.put("timeoutMs", config.timeoutMs());
            }
            case WMS -> {
                summary.put("allocationPath", config.allocationPath());
                summary.put("timeoutMs", config.timeoutMs());
            }
            case U9 -> {
                summary.put("authLoginPath", config.authLoginPath());
                summary.put("salesOrderCreatePath", config.salesOrderCreatePath());
                summary.put("userCode", config.userCode());
                summary.put("entCode", config.entCode());
                summary.put("orgCode", config.orgCode());
                summary.put("clientId", config.clientId());
                summary.put("connectTimeoutMs", config.connectTimeoutMs());
                summary.put("readTimeoutMs", config.readTimeoutMs());
            }
        }
        return summary;
    }

    private Map<String, Object> testSummary(SystemExternalIntegrationConfig entity, ExternalIntegrationTestResult result) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("systemCode", entity.getSystemCode());
        summary.put("success", result.success());
        summary.put("message", result.message());
        summary.put("httpStatus", result.httpStatus());
        return summary;
    }

    private void updateTestResult(Long id, String status, String message) {
        SystemExternalIntegrationConfig update = new SystemExternalIntegrationConfig();
        update.setId(id);
        update.setLastTestStatus(status);
        update.setLastTestMessage(message);
        update.setLastTestAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(update);
    }

    private Map<String, Object> response(boolean success, String message, ExternalIntegrationTestResult result) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        if (result != null) {
            response.put("data", result);
        }
        return response;
    }

    private String sanitizeMessage(String message, SystemExternalIntegrationConfig entity) {
        String text = trimToNull(message);
        if (text == null) {
            return "Integration config test failed";
        }
        try {
            ExternalIntegrationConfig config = configService.toDomain(entity);
            if (!isBlank(config.authToken())) {
                text = text.replace(config.authToken(), MASKED_SECRET);
            }
            if (!isBlank(config.clientSecret())) {
                text = text.replace(config.clientSecret(), MASKED_SECRET);
            }
        } catch (RuntimeException ignored) {
            // Keep the original message when the stored config itself is invalid.
        }
        return SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("$1=" + MASKED_SECRET);
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
                throw new IllegalArgumentException("Invalid external integration config JSON", e);
            }
        }
        return OBJECT_MAPPER.convertValue(value, new TypeReference<>() {
        });
    }

    private String resolveOptionalSecret(String candidate, SystemExternalIntegrationConfig existing, String key) {
        if (hasNewSecret(candidate)) {
            return candidate.trim();
        }
        if (existing == null) {
            return null;
        }
        Object old = configMap(existing.getConfigJson()).get(key);
        return old == null ? null : trimToNull(String.valueOf(old));
    }

    private String resolveRequiredSecret(String candidate, SystemExternalIntegrationConfig existing, boolean create, String key, String message) {
        if (hasNewSecret(candidate)) {
            return candidate.trim();
        }
        if (!create && existing != null) {
            Object old = configMap(existing.getConfigJson()).get(key);
            String normalized = old == null ? null : trimToNull(String.valueOf(old));
            if (normalized != null) {
                return normalized;
            }
        }
        throw new IllegalArgumentException(message);
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

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private Integer positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean hasNewSecret(String value) {
        return !isBlank(value) && !MASKED_SECRET.equals(value.trim());
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
