package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.dto.SystemDataCenterConfigDTO;
import com.machining.quote.dto.SystemDataCenterConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.mapper.SystemDataCenterConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DataCenterConfigService {
    private static final Logger log = LoggerFactory.getLogger(DataCenterConfigService.class);
    private static final String MASKED_SECRET = "******";

    @Autowired
    private SystemDataCenterConfigMapper configMapper;
    @Autowired
    private DataCenterProperties dataCenterProperties;
    @Autowired
    private DataCenterTokenService tokenService;
    @Autowired
    private AuditService auditService;

    public List<SystemDataCenterConfigDTO> listConfigs() {
        QueryWrapper<SystemDataCenterConfig> query = new QueryWrapper<>();
        query.orderByDesc("active")
                .orderByDesc("enabled")
                .orderByDesc("updated_at")
                .orderByAsc("id");
        return configMapper.selectList(query).stream().map(this::toDTO).toList();
    }

    public SystemDataCenterConfig activeConfig() {
        QueryWrapper<SystemDataCenterConfig> query = new QueryWrapper<>();
        query.eq("active", true)
                .eq("enabled", true)
                .last("limit 1");
        SystemDataCenterConfig active = configMapper.selectOne(query);
        return active == null ? fallbackConfig() : active;
    }

    @Transactional
    public SystemDataCenterConfigDTO saveConfig(Long id, SystemDataCenterConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Data center config request cannot be null");
        }
        boolean create = id == null;
        SystemDataCenterConfig existing = create ? null : requireConfig(id);
        SystemDataCenterConfig beforeState = create ? null : copyOf(existing);
        Map<String, Object> beforeAudit = create ? null : auditSummary(beforeState, false);
        SystemDataCenterConfig entity = create ? new SystemDataCenterConfig() : existing;
        applyRequest(entity, req, existing, create);

        boolean connectionChanged = beforeState != null && connectionChanged(beforeState, entity);
        if (connectionChanged) {
            entity.setLastTestStatus(null);
            entity.setLastTestMessage(null);
            entity.setLastTestAt(null);
        }

        if (create) {
            entity.setActive(false);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            configMapper.insert(entity);
            auditService.record(
                    "SYSTEM_DATA_CENTER_CONFIG_CREATE",
                    "system_data_center_config",
                    entity.getId(),
                    null,
                    auditSummary(entity, hasNewSecret(req.getClientSecret()))
            );
            log.info("Data center config created id={} active={} enabled={}",
                    entity.getId(), entity.getActive(), entity.getEnabled());
        } else {
            entity.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(entity);
            auditService.record(
                    "SYSTEM_DATA_CENTER_CONFIG_UPDATE",
                    "system_data_center_config",
                    entity.getId(),
                    beforeAudit,
                    auditSummary(entity, hasNewSecret(req.getClientSecret()))
            );
            log.info("Data center config updated id={} active={} enabled={} connectionChanged={}",
                    entity.getId(), entity.getActive(), entity.getEnabled(), connectionChanged);
        }
        return toDTO(entity);
    }

    @Transactional
    public Map<String, Object> testToken(Long id) {
        SystemDataCenterConfig entity = requireConfig(id);
        DataCenterTokenService.TokenTestResult result = tokenService.testToken(entity);
        updateTestResult(entity.getId(), result.success() ? "SUCCESS" : "FAILED", result.message());
        Map<String, Object> summary = testSummary(entity, result);
        if (result.success()) {
            auditService.record("SYSTEM_DATA_CENTER_CONFIG_TEST", "system_data_center_config", id, null, summary);
            log.info("Data center token test completed id={} success=true httpStatus={}", id, result.httpStatus());
        } else {
            auditService.record(
                    "SYSTEM_DATA_CENTER_CONFIG_TEST_FAILED",
                    "system_data_center_config",
                    id,
                    null,
                    summary,
                    "SYSTEM",
                    "FAILED",
                    AuditService.labelForAction("SYSTEM_DATA_CENTER_CONFIG_TEST_FAILED"),
                    result.message()
            );
            log.warn("Data center token test failed id={} message={}", id, result.message());
        }
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("message", result.message());
        response.put("data", summary);
        return response;
    }

    @Transactional
    public SystemDataCenterConfigDTO activateConfig(Long id) {
        SystemDataCenterConfig entity = requireConfig(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalStateException("Data center config is disabled");
        }
        if (!"SUCCESS".equalsIgnoreCase(nullToEmpty(entity.getLastTestStatus()))) {
            throw new IllegalStateException("Data center config must pass token test before activation");
        }

        SystemDataCenterConfig deactivate = new SystemDataCenterConfig();
        deactivate.setActive(false);
        deactivate.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<SystemDataCenterConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("active", true);
        configMapper.update(deactivate, wrapper);

        entity.setActive(true);
        entity.setEnabled(true);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_DATA_CENTER_CONFIG_ACTIVATE", "system_data_center_config", id, null, auditSummary(entity, false));
        log.info("Data center config activated id={}", id);
        return toDTO(entity);
    }

    @Transactional
    public SystemDataCenterConfigDTO disableConfig(Long id) {
        SystemDataCenterConfig entity = requireConfig(id);
        Map<String, Object> beforeAudit = auditSummary(entity, false);
        entity.setActive(false);
        entity.setEnabled(false);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_DATA_CENTER_CONFIG_DISABLE", "system_data_center_config", id, beforeAudit, auditSummary(entity, false));
        log.info("Data center config disabled id={}", id);
        return toDTO(entity);
    }

    private void applyRequest(
            SystemDataCenterConfig entity,
            SystemDataCenterConfigSaveReq req,
            SystemDataCenterConfig existing,
            boolean create) {
        entity.setName(requireText(req.getName(), "Data center config name cannot be blank"));
        entity.setEnabled(req.getEnabled() == null ? create || Boolean.TRUE.equals(existing.getEnabled()) : req.getEnabled());
        entity.setBaseUrl(requireText(req.getBaseUrl(), "Data center baseUrl cannot be blank"));
        entity.setTokenUrl(requireText(req.getTokenUrl(), "Data center tokenUrl cannot be blank"));
        entity.setClientId(requireText(req.getClientId(), "Data center clientId cannot be blank"));
        entity.setClientSecretCipher(resolveSecret(req.getClientSecret(), existing, create));
        entity.setGrantType(resolveGrantType(req.getGrantType()));
        entity.setConnectTimeoutMs(positive(req.getConnectTimeoutMs(), 5000));
        entity.setReadTimeoutMs(positive(req.getReadTimeoutMs(), 30000));
        entity.setTokenExpireSkewSeconds(positive(req.getTokenExpireSkewSeconds(), 60));
        entity.setRemark(trimToNull(req.getRemark()));
        if (create) {
            entity.setActive(false);
        }
    }

    private boolean connectionChanged(SystemDataCenterConfig before, SystemDataCenterConfig after) {
        return !Objects.equals(before.getBaseUrl(), after.getBaseUrl())
                || !Objects.equals(before.getTokenUrl(), after.getTokenUrl())
                || !Objects.equals(before.getClientId(), after.getClientId())
                || !Objects.equals(before.getClientSecretCipher(), after.getClientSecretCipher())
                || !Objects.equals(before.getGrantType(), after.getGrantType())
                || !Objects.equals(before.getConnectTimeoutMs(), after.getConnectTimeoutMs())
                || !Objects.equals(before.getReadTimeoutMs(), after.getReadTimeoutMs())
                || !Objects.equals(before.getTokenExpireSkewSeconds(), after.getTokenExpireSkewSeconds());
    }

    private SystemDataCenterConfig copyOf(SystemDataCenterConfig source) {
        SystemDataCenterConfig copy = new SystemDataCenterConfig();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setActive(source.getActive());
        copy.setEnabled(source.getEnabled());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setTokenUrl(source.getTokenUrl());
        copy.setClientId(source.getClientId());
        copy.setClientSecretCipher(source.getClientSecretCipher());
        copy.setGrantType(source.getGrantType());
        copy.setConnectTimeoutMs(source.getConnectTimeoutMs());
        copy.setReadTimeoutMs(source.getReadTimeoutMs());
        copy.setTokenExpireSkewSeconds(source.getTokenExpireSkewSeconds());
        copy.setLastTestStatus(source.getLastTestStatus());
        copy.setLastTestMessage(source.getLastTestMessage());
        copy.setLastTestAt(source.getLastTestAt());
        copy.setRemark(source.getRemark());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private SystemDataCenterConfig requireConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Data center config id cannot be null");
        }
        SystemDataCenterConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Data center config not found id=" + id);
        }
        return entity;
    }

    private void updateTestResult(Long id, String status, String message) {
        SystemDataCenterConfig update = new SystemDataCenterConfig();
        update.setId(id);
        update.setLastTestStatus(status);
        update.setLastTestMessage(abbreviate(message));
        update.setLastTestAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(update);
    }

    private SystemDataCenterConfig fallbackConfig() {
        SystemDataCenterConfig entity = new SystemDataCenterConfig();
        entity.setId(null);
        entity.setName("erp.env data center fallback");
        entity.setActive(true);
        entity.setEnabled(dataCenterProperties.isEnabled());
        entity.setBaseUrl(trimToNull(dataCenterProperties.getBaseUrl()));
        entity.setTokenUrl(trimToNull(dataCenterProperties.getTokenUrl()));
        entity.setClientId(trimToNull(dataCenterProperties.getClientId()));
        entity.setClientSecretCipher(trimToNull(dataCenterProperties.getClientSecret()));
        entity.setGrantType(resolveGrantType(dataCenterProperties.getGrantType()));
        entity.setConnectTimeoutMs(positive(dataCenterProperties.getConnectTimeoutMs(), 5000));
        entity.setReadTimeoutMs(positive(dataCenterProperties.getReadTimeoutMs(), 30000));
        entity.setTokenExpireSkewSeconds(positive(dataCenterProperties.getTokenExpireSkewSeconds(), 60));
        entity.setRemark("fallback from erp.env");
        return entity;
    }

    private SystemDataCenterConfigDTO toDTO(SystemDataCenterConfig entity) {
        SystemDataCenterConfigDTO dto = new SystemDataCenterConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setActive(Boolean.TRUE.equals(entity.getActive()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setTokenUrl(entity.getTokenUrl());
        dto.setClientId(entity.getClientId());
        dto.setClientSecretConfigured(!isBlank(entity.getClientSecretCipher()));
        dto.setClientSecret(!isBlank(entity.getClientSecretCipher()) ? MASKED_SECRET : null);
        dto.setGrantType(entity.getGrantType());
        dto.setConnectTimeoutMs(entity.getConnectTimeoutMs());
        dto.setReadTimeoutMs(entity.getReadTimeoutMs());
        dto.setTokenExpireSkewSeconds(entity.getTokenExpireSkewSeconds());
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestAt(entity.getLastTestAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> auditSummary(SystemDataCenterConfig entity, boolean clientSecretChanged) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("name", entity.getName());
        summary.put("active", Boolean.TRUE.equals(entity.getActive()));
        summary.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        summary.put("baseUrl", entity.getBaseUrl());
        summary.put("tokenUrl", entity.getTokenUrl());
        summary.put("clientId", entity.getClientId());
        summary.put("clientSecretConfigured", !isBlank(entity.getClientSecretCipher()));
        summary.put("clientSecretChanged", clientSecretChanged);
        summary.put("grantType", entity.getGrantType());
        summary.put("connectTimeoutMs", entity.getConnectTimeoutMs());
        summary.put("readTimeoutMs", entity.getReadTimeoutMs());
        summary.put("tokenExpireSkewSeconds", entity.getTokenExpireSkewSeconds());
        summary.put("lastTestStatus", entity.getLastTestStatus());
        summary.put("remark", entity.getRemark());
        return summary;
    }

    private Map<String, Object> testSummary(SystemDataCenterConfig entity, DataCenterTokenService.TokenTestResult result) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("success", result.success());
        summary.put("message", result.message());
        summary.put("httpStatus", result.httpStatus());
        summary.put("expiresInSeconds", result.expiresInSeconds());
        return summary;
    }

    private String resolveSecret(String candidate, SystemDataCenterConfig existing, boolean create) {
        if (hasNewSecret(candidate)) {
            return candidate.trim();
        }
        if (!create && existing != null && !isBlank(existing.getClientSecretCipher())) {
            return existing.getClientSecretCipher().trim();
        }
        throw new IllegalArgumentException("Data center clientSecret cannot be blank");
    }

    private String resolveGrantType(String value) {
        String grantType = trimToNull(value);
        if (grantType == null) {
            return "client_credentials";
        }
        if (!"client_credentials".equalsIgnoreCase(grantType)) {
            throw new IllegalArgumentException("Data center grantType only supports client_credentials");
        }
        return "client_credentials";
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean hasNewSecret(String value) {
        return !isBlank(value) && !MASKED_SECRET.equals(value.trim());
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
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
