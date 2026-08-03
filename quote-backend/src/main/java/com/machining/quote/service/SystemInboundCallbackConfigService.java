package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SystemInboundCallbackConfigDTO;
import com.machining.quote.dto.SystemInboundCallbackConfigSaveReq;
import com.machining.quote.dto.SystemInboundCallbackTokenResp;
import com.machining.quote.entity.SystemInboundCallbackConfig;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.SystemInboundCallbackConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemInboundCallbackConfigService {
    private static final Logger log = LoggerFactory.getLogger(SystemInboundCallbackConfigService.class);
    private static final String SOURCE_SYSTEM_MES = "MES";
    private static final String MES_CALLBACK_NAME = "MES物料号回调";
    private static final String MES_CALLBACK_PATH = "/api/integrations/mes/material-callback";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private SystemInboundCallbackConfigMapper configMapper;
    @Autowired
    private AuditService auditService;

    @Value("${erp.public-base-url:http://localhost:8088}")
    private String fallbackPublicBaseUrl;
    @Value("${erp.mes.callback-token:}")
    private String fallbackCallbackToken;
    @Value("${erp.mes.callback-token-enabled:true}")
    private boolean fallbackCallbackTokenEnabled;

    public List<SystemInboundCallbackConfigDTO> listConfigs() {
        QueryWrapper<SystemInboundCallbackConfig> query = new QueryWrapper<>();
        query.orderByAsc("source_system_code")
                .orderByAsc("callback_code")
                .orderByDesc("updated_at")
                .orderByAsc("id");
        List<SystemInboundCallbackConfig> configs = configMapper.selectList(query);
        if (configs == null || configs.isEmpty()) {
            return List.of(defaultMesMaterialCallbackDTO());
        }
        return configs.stream().map(this::toDTO).toList();
    }

    public SystemInboundCallbackConfigDTO getMesMaterialCallbackConfig() {
        SystemInboundCallbackConfig entity = findConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
        return entity == null ? defaultMesMaterialCallbackDTO() : toDTO(entity);
    }

    @Transactional
    public SystemInboundCallbackConfigDTO saveMesMaterialCallbackConfig(SystemInboundCallbackConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Inbound callback config request cannot be null");
        }
        SystemInboundCallbackConfig existing = findConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
        Map<String, Object> beforeAudit = existing == null ? null : auditSummary(existing, false);
        SystemInboundCallbackConfig entity = existing == null ? defaultEntity() : existing;
        entity.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        entity.setTokenEnabled(req.getTokenEnabled() == null ? Boolean.TRUE : req.getTokenEnabled());
        entity.setPublicBaseUrl(trimToNull(req.getPublicBaseUrl()));
        entity.setRemark(trimToNull(req.getRemark()));
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            entity.setCreatedAt(entity.getUpdatedAt());
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        auditService.record(
                "SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE",
                "system_inbound_callback_config",
                entity.getId(),
                beforeAudit,
                auditSummary(entity, false)
        );
        log.info("Inbound callback config saved callbackCode={} enabled={} tokenEnabled={} tokenConfigured={}",
                entity.getCallbackCode(), entity.getEnabled(), entity.getTokenEnabled(), !isBlank(entity.getTokenHash()));
        return toDTO(entity);
    }

    @Transactional
    public SystemInboundCallbackTokenResp rotateMesMaterialCallbackToken() {
        SystemInboundCallbackConfig entity = ensureConfig();
        String token = generateToken();
        entity.setTokenHash(sha256Hex(token));
        entity.setTokenMask(maskToken(token));
        entity.setTokenUpdatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getTokenUpdatedAt());
        configMapper.updateById(entity);
        auditService.record(
                "SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE",
                "system_inbound_callback_config",
                entity.getId(),
                null,
                auditSummary(entity, true)
        );
        log.info("Inbound callback token rotated callbackCode={} tokenConfigured=true", entity.getCallbackCode());

        SystemInboundCallbackTokenResp resp = new SystemInboundCallbackTokenResp();
        resp.setCallbackCode(entity.getCallbackCode());
        resp.setToken(token);
        resp.setTokenMask(entity.getTokenMask());
        resp.setMessage("token 已生成，请立即复制并通过受控渠道交付 MES；关闭后无法再次查看明文。");
        return resp;
    }

    @Transactional
    public Map<String, Object> validateMesMaterialCallbackConfig() {
        SystemInboundCallbackConfig entity = ensureConfig();
        List<String> issues = validatePersistedConfig(entity);
        boolean success = issues.isEmpty();
        String message = success ? "入站回调配置完整" : String.join("；", issues);
        entity.setLastValidateStatus(success ? "SUCCESS" : "FAILED");
        entity.setLastValidateMessage(message);
        entity.setLastValidateAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getLastValidateAt());
        configMapper.updateById(entity);
        auditService.record(
                "SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE",
                "system_inbound_callback_config",
                entity.getId(),
                null,
                validationSummary(entity, success, message)
        );
        log.info("Inbound callback config validated callbackCode={} success={}", entity.getCallbackCode(), success);
        return response(success, message, toDTO(entity));
    }

    public boolean hasValidBearerToken(InboundCallbackCode callbackCode, String authorization) {
        if (callbackCode == null) {
            return false;
        }
        SystemInboundCallbackConfig entity = findConfig(callbackCode);
        boolean enabled = entity == null || entity.getEnabled() == null || Boolean.TRUE.equals(entity.getEnabled());
        if (!enabled) {
            return false;
        }
        boolean tokenEnabled = entity == null
                ? fallbackCallbackTokenEnabled
                : (entity.getTokenEnabled() == null ? fallbackCallbackTokenEnabled : Boolean.TRUE.equals(entity.getTokenEnabled()));
        if (!tokenEnabled) {
            return true;
        }
        String expectedHash = entity == null ? null : trimToNull(entity.getTokenHash());
        if (expectedHash == null && !isBlank(fallbackCallbackToken)) {
            expectedHash = sha256Hex(fallbackCallbackToken.trim());
        }
        String actualToken = bearerToken(authorization);
        if (isBlank(expectedHash) || isBlank(actualToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                sha256Hex(actualToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    private SystemInboundCallbackConfig ensureConfig() {
        SystemInboundCallbackConfig existing = findConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
        if (existing != null) {
            ensureFixedFields(existing);
            return existing;
        }
        SystemInboundCallbackConfig entity = defaultEntity();
        entity.setPublicBaseUrl(trimToNull(fallbackPublicBaseUrl));
        entity.setTokenEnabled(fallbackCallbackTokenEnabled);
        if (!isBlank(fallbackCallbackToken)) {
            entity.setTokenHash(sha256Hex(fallbackCallbackToken.trim()));
            entity.setTokenMask("已配置");
        }
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        configMapper.insert(entity);
        return entity;
    }

    private SystemInboundCallbackConfig findConfig(InboundCallbackCode code) {
        QueryWrapper<SystemInboundCallbackConfig> query = new QueryWrapper<>();
        query.eq("callback_code", code.name())
                .last("LIMIT 1");
        return configMapper.selectOne(query);
    }

    private SystemInboundCallbackConfig defaultEntity() {
        SystemInboundCallbackConfig entity = new SystemInboundCallbackConfig();
        ensureFixedFields(entity);
        entity.setEnabled(true);
        entity.setTokenEnabled(true);
        return entity;
    }

    private void ensureFixedFields(SystemInboundCallbackConfig entity) {
        entity.setCallbackCode(InboundCallbackCode.MES_MATERIAL_CALLBACK.name());
        entity.setSourceSystemCode(SOURCE_SYSTEM_MES);
        entity.setName(MES_CALLBACK_NAME);
        entity.setCallbackPath(MES_CALLBACK_PATH);
    }

    private SystemInboundCallbackConfigDTO defaultMesMaterialCallbackDTO() {
        SystemInboundCallbackConfigDTO dto = new SystemInboundCallbackConfigDTO();
        dto.setCallbackCode(InboundCallbackCode.MES_MATERIAL_CALLBACK.name());
        dto.setSourceSystemCode(SOURCE_SYSTEM_MES);
        dto.setName(MES_CALLBACK_NAME);
        dto.setEnabled(true);
        dto.setTokenEnabled(fallbackCallbackTokenEnabled);
        dto.setTokenConfigured(!isBlank(fallbackCallbackToken));
        dto.setTokenMask(isBlank(fallbackCallbackToken) ? null : "已配置");
        dto.setPublicBaseUrl(trimToNull(fallbackPublicBaseUrl));
        dto.setCallbackPath(MES_CALLBACK_PATH);
        dto.setCallbackUrl(callbackUrl(dto.getPublicBaseUrl(), dto.getCallbackPath()));
        dto.setConfigSource("ENV");
        return dto;
    }

    private SystemInboundCallbackConfigDTO toDTO(SystemInboundCallbackConfig entity) {
        String publicBaseUrl = trimToNull(entity.getPublicBaseUrl());
        boolean tokenHashConfigured = !isBlank(entity.getTokenHash());
        SystemInboundCallbackConfigDTO dto = new SystemInboundCallbackConfigDTO();
        dto.setId(entity.getId());
        dto.setCallbackCode(entity.getCallbackCode());
        dto.setSourceSystemCode(entity.getSourceSystemCode());
        dto.setName(entity.getName());
        dto.setEnabled(entity.getEnabled() == null ? Boolean.TRUE : entity.getEnabled());
        dto.setTokenEnabled(entity.getTokenEnabled() == null ? fallbackCallbackTokenEnabled : entity.getTokenEnabled());
        dto.setTokenConfigured(tokenHashConfigured || !isBlank(fallbackCallbackToken));
        dto.setTokenMask(tokenHashConfigured ? entity.getTokenMask() : (isBlank(fallbackCallbackToken) ? null : "已配置"));
        dto.setPublicBaseUrl(publicBaseUrl == null ? trimToNull(fallbackPublicBaseUrl) : publicBaseUrl);
        dto.setCallbackPath(defaultText(entity.getCallbackPath(), MES_CALLBACK_PATH));
        dto.setCallbackUrl(callbackUrl(dto.getPublicBaseUrl(), dto.getCallbackPath()));
        dto.setConfigSource(configSource(entity, publicBaseUrl, tokenHashConfigured));
        dto.setLastValidateStatus(entity.getLastValidateStatus());
        dto.setLastValidateMessage(entity.getLastValidateMessage());
        dto.setLastValidateAt(entity.getLastValidateAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String configSource(SystemInboundCallbackConfig entity, String publicBaseUrl, boolean tokenHashConfigured) {
        if (entity == null) {
            return "ENV";
        }
        if ((publicBaseUrl == null && !isBlank(fallbackPublicBaseUrl))
                || (!tokenHashConfigured && !isBlank(fallbackCallbackToken))) {
            return "MIXED";
        }
        return "DB";
    }

    private List<String> validatePersistedConfig(SystemInboundCallbackConfig entity) {
        List<String> issues = new ArrayList<>();
        if (isBlank(entity.getPublicBaseUrl())) {
            issues.add("publicBaseUrl 未配置");
        }
        if (isBlank(entity.getCallbackPath())) {
            issues.add("callbackPath 未配置");
        }
        if (Boolean.TRUE.equals(entity.getTokenEnabled()) && isBlank(entity.getTokenHash())) {
            issues.add("token 未生成");
        }
        return issues;
    }

    private Map<String, Object> auditSummary(SystemInboundCallbackConfig entity, boolean tokenRotated) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("callbackCode", entity.getCallbackCode());
        summary.put("sourceSystemCode", entity.getSourceSystemCode());
        summary.put("name", entity.getName());
        summary.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        summary.put("tokenEnabled", Boolean.TRUE.equals(entity.getTokenEnabled()));
        summary.put("tokenConfigured", !isBlank(entity.getTokenHash()));
        summary.put("tokenRotated", tokenRotated);
        summary.put("publicBaseUrl", entity.getPublicBaseUrl());
        summary.put("callbackPath", entity.getCallbackPath());
        summary.put("lastValidateStatus", entity.getLastValidateStatus());
        return summary;
    }

    private Map<String, Object> validationSummary(SystemInboundCallbackConfig entity, boolean success, String message) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>(auditSummary(entity, false));
        summary.put("success", success);
        summary.put("message", message);
        return summary;
    }

    private Map<String, Object> response(boolean success, String message, Object data) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash inbound callback token", e);
        }
    }

    private String maskToken(String token) {
        String value = trimToNull(token);
        if (value == null) {
            return null;
        }
        int keep = Math.min(4, value.length());
        return "***" + value.substring(value.length() - keep);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String callbackUrl(String publicBaseUrl, String callbackPath) {
        String base = trimToNull(publicBaseUrl);
        String path = defaultText(callbackPath, MES_CALLBACK_PATH);
        if (base == null) {
            return path;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
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
