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
    private static final String OPEN_API_TOKEN_CODE = "QPO_OPEN_API_TOKEN";
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
        Map<String, SystemInboundCallbackConfig> byCode = new LinkedHashMap<>();
        if (configs != null) {
            for (SystemInboundCallbackConfig config : configs) {
                if (config != null && !isBlank(config.getCallbackCode())) {
                    byCode.putIfAbsent(config.getCallbackCode(), config);
                }
            }
        }

        List<SystemInboundCallbackConfigDTO> result = new ArrayList<>();
        for (InboundCallbackCode code : InboundCallbackCode.values()) {
            SystemInboundCallbackConfig entity = byCode.remove(code.name());
            result.add(entity == null ? defaultDTO(code) : toDTO(entity, code));
        }
        for (SystemInboundCallbackConfig entity : byCode.values()) {
            result.add(toDTO(entity, null));
        }
        return result;
    }

    public SystemInboundCallbackConfigDTO getInboundCallbackConfig(InboundCallbackCode callbackCode) {
        SystemInboundCallbackConfig entity = findConfig(callbackCode);
        return entity == null ? defaultDTO(callbackCode) : toDTO(entity, callbackCode);
    }

    public SystemInboundCallbackConfigDTO getMesMaterialCallbackConfig() {
        return getInboundCallbackConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
    }

    @Transactional
    public SystemInboundCallbackConfigDTO saveInboundCallbackConfig(InboundCallbackCode callbackCode, SystemInboundCallbackConfigSaveReq req) {
        if (callbackCode == null) {
            throw new IllegalArgumentException("Inbound callback code cannot be null");
        }
        if (req == null) {
            throw new IllegalArgumentException("Inbound callback config request cannot be null");
        }
        SystemInboundCallbackConfig existing = findConfig(callbackCode);
        Map<String, Object> beforeAudit = existing == null ? null : auditSummary(existing, false);
        SystemInboundCallbackConfig entity = existing == null ? defaultEntity(callbackCode) : existing;
        ensureFixedFields(entity, callbackCode);
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
        log.info("Inbound callback config saved callbackCode={} enabled={} tokenEnabled={}",
                entity.getCallbackCode(), entity.getEnabled(), entity.getTokenEnabled());
        return toDTO(entity, callbackCode);
    }

    @Transactional
    public SystemInboundCallbackConfigDTO saveMesMaterialCallbackConfig(SystemInboundCallbackConfigSaveReq req) {
        return saveInboundCallbackConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK, req);
    }

    @Transactional
    public SystemInboundCallbackTokenResp rotateMesMaterialCallbackToken() {
        return rotateToken("MES_MATERIAL_CALLBACK", "token 已生成，请立即复制并通过受控渠道交付数据中心；关闭后无法再次查看明文。");
    }

    public Map<String, Object> getOpenApiTokenStatus() {
        TokenSnapshot token = tokenSnapshot();
        LinkedHashMap<String, Object> status = new LinkedHashMap<>();
        status.put("tokenConfigured", token.configured());
        status.put("tokenMask", token.mask());
        status.put("tokenUpdatedAt", token.updatedAt());
        status.put("message", token.configured()
                ? "统一 token 已配置；明文不可再次查看"
                : "统一 token 未配置，请先生成 QPO_OPEN_API_TOKEN");
        return status;
    }

    @Transactional
    public SystemInboundCallbackTokenResp rotateOpenApiToken() {
        return rotateToken(OPEN_API_TOKEN_CODE, "QPO 入站开放接口统一 token 已生成，请立即复制并通过受控渠道交付外部调用方；关闭后无法再次查看明文。");
    }

    private SystemInboundCallbackTokenResp rotateToken(String responseCode, String message) {
        SystemInboundCallbackConfig entity = ensureConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
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
        log.info("Inbound callback token rotated callbackCode={} tokenConfigured=true", responseCode);

        SystemInboundCallbackTokenResp resp = new SystemInboundCallbackTokenResp();
        resp.setCallbackCode(responseCode);
        resp.setToken(token);
        resp.setTokenMask(entity.getTokenMask());
        resp.setMessage(message);
        return resp;
    }

    @Transactional
    public Map<String, Object> validateInboundCallbackConfig(InboundCallbackCode callbackCode) {
        if (callbackCode == null) {
            throw new IllegalArgumentException("Inbound callback code cannot be null");
        }
        SystemInboundCallbackConfig entity = ensureConfig(callbackCode);
        List<String> issues = validatePersistedConfig(entity);
        boolean success = issues.isEmpty();
        String message = success ? "入站接口配置完整" : String.join("；", issues);
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
        return response(success, message, toDTO(entity, callbackCode));
    }

    @Transactional
    public Map<String, Object> validateMesMaterialCallbackConfig() {
        return validateInboundCallbackConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
    }

    public boolean hasValidBearerToken(InboundCallbackCode callbackCode, String authorization) {
        if (callbackCode == null) {
            return false;
        }
        SystemInboundCallbackConfig interfaceConfig = findConfig(callbackCode);
        boolean enabled = interfaceConfig == null
                || interfaceConfig.getEnabled() == null
                || Boolean.TRUE.equals(interfaceConfig.getEnabled());
        if (!enabled) {
            return false;
        }
        boolean tokenEnabled = interfaceConfig == null
                ? fallbackCallbackTokenEnabled
                : (interfaceConfig.getTokenEnabled() == null ? fallbackCallbackTokenEnabled : Boolean.TRUE.equals(interfaceConfig.getTokenEnabled()));
        if (!tokenEnabled) {
            log.warn("Inbound open API token verification disabled callbackCode={} configSource={}",
                    callbackCode.name(), interfaceConfig == null ? "ENV" : "DB");
            return true;
        }

        TokenSnapshot token = tokenSnapshot();
        String actualToken = bearerToken(authorization);
        if (isBlank(token.hash()) || isBlank(actualToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                token.hash().getBytes(StandardCharsets.UTF_8),
                sha256Hex(actualToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean hasValidOpenApiBearerToken(InboundCallbackCode callbackCode, String authorization) {
        return hasValidBearerToken(callbackCode, authorization);
    }

    public boolean hasValidOpenApiBearerToken(String authorization) {
        return hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, authorization);
    }

    private SystemInboundCallbackConfig ensureConfig(InboundCallbackCode callbackCode) {
        SystemInboundCallbackConfig existing = findConfig(callbackCode);
        if (existing != null) {
            ensureFixedFields(existing, callbackCode);
            return existing;
        }
        SystemInboundCallbackConfig entity = defaultEntity(callbackCode);
        entity.setPublicBaseUrl(trimToNull(fallbackPublicBaseUrl));
        entity.setTokenEnabled(fallbackCallbackTokenEnabled);
        if (callbackCode == InboundCallbackCode.MES_MATERIAL_CALLBACK && !isBlank(fallbackCallbackToken)) {
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

    private SystemInboundCallbackConfig defaultEntity(InboundCallbackCode callbackCode) {
        SystemInboundCallbackConfig entity = new SystemInboundCallbackConfig();
        ensureFixedFields(entity, callbackCode);
        entity.setEnabled(true);
        entity.setTokenEnabled(true);
        return entity;
    }

    private void ensureFixedFields(SystemInboundCallbackConfig entity, InboundCallbackCode callbackCode) {
        entity.setCallbackCode(callbackCode.name());
        entity.setSourceSystemCode(callbackCode.getSourceSystemCode());
        entity.setName(callbackCode.getDisplayName());
        entity.setCallbackPath(callbackCode.getCallbackPath());
    }

    private SystemInboundCallbackConfigDTO defaultDTO(InboundCallbackCode callbackCode) {
        TokenSnapshot token = tokenSnapshot();
        SystemInboundCallbackConfigDTO dto = new SystemInboundCallbackConfigDTO();
        dto.setCallbackCode(callbackCode.name());
        dto.setSourceSystemCode(callbackCode.getSourceSystemCode());
        dto.setName(callbackCode.getDisplayName());
        dto.setEnabled(true);
        dto.setTokenEnabled(fallbackCallbackTokenEnabled);
        dto.setTokenConfigured(token.configured());
        dto.setTokenMask(token.mask());
        dto.setTokenUpdatedAt(token.updatedAt());
        dto.setPublicBaseUrl(trimToNull(fallbackPublicBaseUrl));
        dto.setCallbackPath(callbackCode.getCallbackPath());
        dto.setCallbackUrl(callbackUrl(dto.getPublicBaseUrl(), dto.getCallbackPath()));
        dto.setConfigSource("ENV");
        return dto;
    }

    private SystemInboundCallbackConfigDTO toDTO(SystemInboundCallbackConfig entity, InboundCallbackCode definition) {
        InboundCallbackCode callbackCode = definition;
        if (callbackCode == null) {
            try {
                callbackCode = InboundCallbackCode.fromCode(entity.getCallbackCode());
            } catch (IllegalArgumentException ignored) {
                callbackCode = null;
            }
        }
        String publicBaseUrl = trimToNull(entity.getPublicBaseUrl());
        String callbackPath = callbackCode == null ? trimToNull(entity.getCallbackPath()) : callbackCode.getCallbackPath();
        TokenSnapshot token = tokenSnapshot();
        SystemInboundCallbackConfigDTO dto = new SystemInboundCallbackConfigDTO();
        dto.setId(entity.getId());
        dto.setCallbackCode(callbackCode == null ? trimToNull(entity.getCallbackCode()) : callbackCode.name());
        dto.setSourceSystemCode(callbackCode == null ? trimToNull(entity.getSourceSystemCode()) : callbackCode.getSourceSystemCode());
        dto.setName(callbackCode == null ? trimToNull(entity.getName()) : callbackCode.getDisplayName());
        dto.setEnabled(entity.getEnabled() == null ? Boolean.TRUE : entity.getEnabled());
        dto.setTokenEnabled(entity.getTokenEnabled() == null ? fallbackCallbackTokenEnabled : entity.getTokenEnabled());
        dto.setTokenConfigured(token.configured());
        dto.setTokenMask(token.mask());
        dto.setTokenUpdatedAt(token.updatedAt());
        dto.setPublicBaseUrl(publicBaseUrl == null ? trimToNull(fallbackPublicBaseUrl) : publicBaseUrl);
        dto.setCallbackPath(callbackPath);
        dto.setCallbackUrl(callbackUrl(dto.getPublicBaseUrl(), callbackPath));
        dto.setConfigSource(configSource(entity, publicBaseUrl, token.dbConfigured()));
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
        boolean tokenEnabled = entity.getTokenEnabled() == null ? fallbackCallbackTokenEnabled : Boolean.TRUE.equals(entity.getTokenEnabled());
        if (tokenEnabled && !tokenSnapshot().configured()) {
            issues.add("token 未生成");
        }
        return issues;
    }

    private TokenSnapshot tokenSnapshot() {
        SystemInboundCallbackConfig tokenConfig = findConfig(InboundCallbackCode.MES_MATERIAL_CALLBACK);
        boolean dbConfigured = tokenConfig != null && !isBlank(tokenConfig.getTokenHash());
        boolean fallbackConfigured = !isBlank(fallbackCallbackToken);
        String hash = dbConfigured ? trimToNull(tokenConfig.getTokenHash()) : (fallbackConfigured ? sha256Hex(fallbackCallbackToken.trim()) : null);
        String mask = dbConfigured ? tokenConfig.getTokenMask() : (fallbackConfigured ? "已配置" : null);
        LocalDateTime updatedAt = tokenConfig == null ? null : tokenConfig.getTokenUpdatedAt();
        return new TokenSnapshot(dbConfigured || fallbackConfigured, dbConfigured, hash, mask, updatedAt);
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
        String path = defaultText(callbackPath, "");
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

    private record TokenSnapshot(boolean configured, boolean dbConfigured, String hash, String mask, LocalDateTime updatedAt) {
    }
}
