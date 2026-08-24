package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigDTO;
import com.machining.quote.dto.system.SystemDataCenterSsoConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterSsoConfig;
import com.machining.quote.mapper.SystemDataCenterSsoConfigMapper;
import com.machining.quote.service.auth.DataCenterSsoRuntimeConfig;
import com.machining.quote.service.auth.DataCenterSsoSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SystemDataCenterSsoConfigService {
    private static final Logger log = LoggerFactory.getLogger(SystemDataCenterSsoConfigService.class);
    private static final String MASKED_SECRET = "******";

    @Autowired
    private SystemDataCenterSsoConfigMapper configMapper;
    @Autowired(required = false)
    private DataCenterSsoSignatureService signatureService;
    @Autowired
    private AuditService auditService;

    public List<SystemDataCenterSsoConfigDTO> listConfigs() {
        QueryWrapper<SystemDataCenterSsoConfig> query = new QueryWrapper<>();
        query.orderByDesc("active")
                .orderByDesc("enabled")
                .orderByDesc("updated_at")
                .orderByAsc("id");
        return configMapper.selectList(query).stream().map(this::toDTO).toList();
    }

    @Transactional
    public SystemDataCenterSsoConfigDTO saveConfig(Long id, SystemDataCenterSsoConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Data center SSO config request cannot be null");
        }
        boolean create = id == null;
        SystemDataCenterSsoConfig existing = create ? null : requireConfig(id);
        SystemDataCenterSsoConfig before = create ? null : copyOf(existing);
        Map<String, Object> beforeAudit = create ? null : auditSummary(before, false);
        SystemDataCenterSsoConfig entity = create ? new SystemDataCenterSsoConfig() : existing;
        applyRequest(entity, req, existing, create);

        boolean connectionChanged = before != null && connectionChanged(before, entity);
        if (connectionChanged) {
            entity.setLastTestStatus(null);
            entity.setLastTestMessage(null);
            entity.setLastTestAt(null);
        }

        boolean secretChanged = hasNewSecret(req.getSharedSecret());
        if (create) {
            entity.setActive(false);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(entity.getCreatedAt());
            configMapper.insert(entity);
            auditService.record(
                    "SYSTEM_DATACENTER_SSO_CONFIG_CREATE",
                    "system_data_center_sso_config",
                    entity.getId(),
                    null,
                    auditSummary(entity, secretChanged)
            );
            log.info("Data center SSO config created id={} active={} enabled={}",
                    entity.getId(), entity.getActive(), entity.getEnabled());
        } else {
            entity.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(entity);
            auditService.record(
                    "SYSTEM_DATACENTER_SSO_CONFIG_UPDATE",
                    "system_data_center_sso_config",
                    entity.getId(),
                    beforeAudit,
                    auditSummary(entity, secretChanged)
            );
            log.info("Data center SSO config updated id={} active={} enabled={} connectionChanged={}",
                    entity.getId(), entity.getActive(), entity.getEnabled(), connectionChanged);
        }
        return toDTO(entity);
    }

    @Transactional
    public Map<String, Object> testConfig(Long id) {
        SystemDataCenterSsoConfig entity = requireConfig(id);
        Map<String, Object> result;
        try {
            validateConfig(entity);
            runSampleSignature(entity);
            updateTestResult(entity.getId(), "SUCCESS", "Data center SSO config test passed");
            result = Map.of("success", true, "message", "Data center SSO config test passed");
            auditService.record("SYSTEM_DATACENTER_SSO_CONFIG_TEST", "system_data_center_sso_config", id, null, testSummary(entity, true, null));
            log.info("Data center SSO config test completed id={} success=true", id);
        } catch (RuntimeException e) {
            String message = abbreviate(e.getMessage());
            updateTestResult(entity.getId(), "FAILED", message);
            result = Map.of("success", false, "message", message);
            auditService.record(
                    "SYSTEM_DATACENTER_SSO_CONFIG_TEST_FAILED",
                    "system_data_center_sso_config",
                    id,
                    null,
                    testSummary(entity, false, message),
                    "SYSTEM",
                    "FAILED",
                    AuditService.labelForAction("SYSTEM_DATACENTER_SSO_CONFIG_TEST_FAILED"),
                    message
            );
            log.warn("Data center SSO config test failed id={} message={}", id, message);
        }
        return result;
    }

    @Transactional
    public SystemDataCenterSsoConfigDTO activateConfig(Long id) {
        SystemDataCenterSsoConfig entity = requireConfig(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalStateException("Data center SSO config is disabled");
        }
        if (isBlank(entity.getSharedSecretCipher())) {
            throw new IllegalStateException("Data center SSO shared secret is not configured");
        }
        if (!"SUCCESS".equalsIgnoreCase(nullToEmpty(entity.getLastTestStatus()))) {
            throw new IllegalStateException("Data center SSO config must pass test before activation");
        }

        SystemDataCenterSsoConfig deactivate = new SystemDataCenterSsoConfig();
        deactivate.setActive(false);
        deactivate.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<SystemDataCenterSsoConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("active", true);
        configMapper.update(deactivate, wrapper);

        entity.setActive(true);
        entity.setEnabled(true);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_DATACENTER_SSO_CONFIG_ACTIVATE", "system_data_center_sso_config", id, null, auditSummary(entity, false));
        log.info("Data center SSO config activated id={}", id);
        return toDTO(entity);
    }

    @Transactional
    public SystemDataCenterSsoConfigDTO disableConfig(Long id) {
        SystemDataCenterSsoConfig entity = requireConfig(id);
        Map<String, Object> beforeAudit = auditSummary(entity, false);
        entity.setActive(false);
        entity.setEnabled(false);
        entity.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(entity);
        auditService.record("SYSTEM_DATACENTER_SSO_CONFIG_DISABLE", "system_data_center_sso_config", id, beforeAudit, auditSummary(entity, false));
        log.info("Data center SSO config disabled id={}", id);
        return toDTO(entity);
    }

    private void applyRequest(
            SystemDataCenterSsoConfig entity,
            SystemDataCenterSsoConfigSaveReq req,
            SystemDataCenterSsoConfig existing,
            boolean create) {
        entity.setConfigName(requireText(req.getConfigName(), "Data center SSO config name cannot be blank"));
        entity.setEnabled(req.getEnabled() == null ? create || Boolean.TRUE.equals(existing.getEnabled()) : req.getEnabled());
        entity.setAppCode(defaultText(req.getAppCode(), "QPO"));
        entity.setSharedSecretCipher(resolveSecret(req.getSharedSecret(), existing, create));
        entity.setTimestampSkewSeconds(positive(req.getTimestampSkewSeconds(), 120));
        entity.setNonceTtlMinutes(positive(req.getNonceTtlMinutes(), 10));
        entity.setDefaultRedirect(defaultText(req.getDefaultRedirect(), "/dashboard"));
        entity.setAllowedRedirectPrefixes(defaultText(req.getAllowedRedirectPrefixes(), "/,/dashboard,/orders"));
        entity.setRemark(trimToNull(req.getRemark()));
        if (create) {
            entity.setActive(false);
        }
    }

    private void validateConfig(SystemDataCenterSsoConfig entity) {
        requireText(entity.getConfigName(), "Data center SSO config name cannot be blank");
        requireText(entity.getAppCode(), "Data center SSO appCode cannot be blank");
        requireText(entity.getSharedSecretCipher(), "Data center SSO shared secret cannot be blank");
        requireInternalRedirect(entity.getDefaultRedirect());
        if (positive(entity.getTimestampSkewSeconds(), 0) < 30) {
            throw new IllegalArgumentException("Data center SSO timestamp skew must be at least 30 seconds");
        }
        if (positive(entity.getNonceTtlMinutes(), 0) < 1) {
            throw new IllegalArgumentException("Data center SSO nonce TTL must be positive");
        }
    }

    private void runSampleSignature(SystemDataCenterSsoConfig entity) {
        DataCenterSsoSignatureService service = signatureService == null ? new DataCenterSsoSignatureService() : signatureService;
        long now = Instant.now().toEpochMilli();
        DataCenterSsoExchangeReq sample = new DataCenterSsoExchangeReq();
        sample.setAppCode(entity.getAppCode());
        sample.setUsername("admin");
        sample.setTimestamp(now);
        sample.setNonce("config-test-nonce");
        sample.setRedirect(entity.getDefaultRedirect());
        sample.setSignature(service.sign(sample, entity.getSharedSecretCipher(), entity.getDefaultRedirect()));
        service.validate(sample, new DataCenterSsoRuntimeConfig(
                "DB",
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getAppCode(),
                entity.getSharedSecretCipher(),
                entity.getTimestampSkewSeconds(),
                entity.getNonceTtlMinutes(),
                entity.getDefaultRedirect(),
                parsePrefixes(entity.getAllowedRedirectPrefixes())
        ), Instant.ofEpochMilli(now));
    }

    private SystemDataCenterSsoConfig requireConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Data center SSO config id cannot be null");
        }
        SystemDataCenterSsoConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Data center SSO config not found id=" + id);
        }
        return entity;
    }

    private void updateTestResult(Long id, String status, String message) {
        SystemDataCenterSsoConfig update = new SystemDataCenterSsoConfig();
        update.setId(id);
        update.setLastTestStatus(status);
        update.setLastTestMessage(abbreviate(message));
        update.setLastTestAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(update);
    }

    private SystemDataCenterSsoConfigDTO toDTO(SystemDataCenterSsoConfig entity) {
        SystemDataCenterSsoConfigDTO dto = new SystemDataCenterSsoConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigName(entity.getConfigName());
        dto.setActive(Boolean.TRUE.equals(entity.getActive()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setAppCode(entity.getAppCode());
        dto.setSharedSecretConfigured(!isBlank(entity.getSharedSecretCipher()));
        dto.setSharedSecret(!isBlank(entity.getSharedSecretCipher()) ? MASKED_SECRET : null);
        dto.setTimestampSkewSeconds(entity.getTimestampSkewSeconds());
        dto.setNonceTtlMinutes(entity.getNonceTtlMinutes());
        dto.setDefaultRedirect(entity.getDefaultRedirect());
        dto.setAllowedRedirectPrefixes(entity.getAllowedRedirectPrefixes());
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestAt(entity.getLastTestAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> auditSummary(SystemDataCenterSsoConfig entity, boolean secretChanged) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("configName", entity.getConfigName());
        summary.put("active", Boolean.TRUE.equals(entity.getActive()));
        summary.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        summary.put("appCode", entity.getAppCode());
        summary.put("secretConfigured", !isBlank(entity.getSharedSecretCipher()));
        summary.put("secretChanged", secretChanged);
        summary.put("timestampSkewSeconds", entity.getTimestampSkewSeconds());
        summary.put("nonceTtlMinutes", entity.getNonceTtlMinutes());
        summary.put("defaultRedirect", entity.getDefaultRedirect());
        summary.put("allowedRedirectPrefixes", entity.getAllowedRedirectPrefixes());
        summary.put("lastTestStatus", entity.getLastTestStatus());
        summary.put("remark", entity.getRemark());
        return summary;
    }

    private Map<String, Object> testSummary(SystemDataCenterSsoConfig entity, boolean success, String message) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("configName", entity.getConfigName());
        summary.put("success", success);
        summary.put("message", message);
        summary.put("secretConfigured", !isBlank(entity.getSharedSecretCipher()));
        summary.put("timestampSkewSeconds", entity.getTimestampSkewSeconds());
        summary.put("nonceTtlMinutes", entity.getNonceTtlMinutes());
        return summary;
    }

    private boolean connectionChanged(SystemDataCenterSsoConfig before, SystemDataCenterSsoConfig after) {
        return !Objects.equals(before.getAppCode(), after.getAppCode())
                || !Objects.equals(before.getSharedSecretCipher(), after.getSharedSecretCipher())
                || !Objects.equals(before.getTimestampSkewSeconds(), after.getTimestampSkewSeconds())
                || !Objects.equals(before.getNonceTtlMinutes(), after.getNonceTtlMinutes())
                || !Objects.equals(before.getDefaultRedirect(), after.getDefaultRedirect())
                || !Objects.equals(before.getAllowedRedirectPrefixes(), after.getAllowedRedirectPrefixes());
    }

    private SystemDataCenterSsoConfig copyOf(SystemDataCenterSsoConfig source) {
        SystemDataCenterSsoConfig copy = new SystemDataCenterSsoConfig();
        copy.setId(source.getId());
        copy.setConfigName(source.getConfigName());
        copy.setActive(source.getActive());
        copy.setEnabled(source.getEnabled());
        copy.setAppCode(source.getAppCode());
        copy.setSharedSecretCipher(source.getSharedSecretCipher());
        copy.setTimestampSkewSeconds(source.getTimestampSkewSeconds());
        copy.setNonceTtlMinutes(source.getNonceTtlMinutes());
        copy.setDefaultRedirect(source.getDefaultRedirect());
        copy.setAllowedRedirectPrefixes(source.getAllowedRedirectPrefixes());
        copy.setLastTestStatus(source.getLastTestStatus());
        copy.setLastTestMessage(source.getLastTestMessage());
        copy.setLastTestAt(source.getLastTestAt());
        copy.setRemark(source.getRemark());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private List<String> parsePrefixes(String value) {
        String text = defaultText(value, "/");
        return List.of(text.split(",")).stream()
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .toList();
    }

    private String resolveSecret(String candidate, SystemDataCenterSsoConfig existing, boolean create) {
        if (hasNewSecret(candidate)) {
            return candidate.trim();
        }
        if (!create && existing != null && !isBlank(existing.getSharedSecretCipher())) {
            return existing.getSharedSecretCipher().trim();
        }
        throw new IllegalArgumentException("Data center SSO shared secret cannot be blank");
    }

    private void requireInternalRedirect(String redirect) {
        String value = requireText(redirect, "Data center SSO default redirect cannot be blank");
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("http://") || value.startsWith("https://")) {
            throw new IllegalArgumentException("Data center SSO default redirect must be internal");
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
