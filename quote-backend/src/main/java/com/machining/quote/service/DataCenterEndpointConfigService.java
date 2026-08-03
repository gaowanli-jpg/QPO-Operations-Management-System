package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.dto.SystemDataCenterEndpointConfigDTO;
import com.machining.quote.dto.SystemDataCenterEndpointConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataCenterEndpointConfigService {
    private static final Logger log = LoggerFactory.getLogger(DataCenterEndpointConfigService.class);
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(client_secret|clientSecret|access_token|refresh_token|token|authorization)\\s*[:=]\\s*[^,;\\s&}]+"
    );

    @Autowired
    private SystemDataCenterEndpointConfigMapper endpointMapper;
    @Autowired
    private DataCenterEndpointRegistry registry;
    @Autowired
    private DataCenterConfigService dataCenterConfigService;
    @Autowired
    private DataCenterTokenService tokenService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataCenterProperties dataCenterProperties;

    public List<SystemDataCenterEndpointConfigDTO> listEndpoints() {
        QueryWrapper<SystemDataCenterEndpointConfig> query = new QueryWrapper<>();
        query.orderByAsc("target_system_code")
                .orderByAsc("endpoint_code");
        Map<String, SystemDataCenterEndpointConfig> existing = endpointMapper.selectList(query).stream()
                .collect(Collectors.toMap(
                        entity -> normalizeEndpointCode(entity.getEndpointCode()),
                        entity -> entity,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return registry.definitions().stream()
                .map(definition -> existing.getOrDefault(definition.endpointCode(), registry.newEndpoint(definition.endpointCode())))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public SystemDataCenterEndpointConfigDTO saveEndpoint(String endpointCode, SystemDataCenterEndpointConfigSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("Data center endpoint request cannot be null");
        }
        String normalizedCode = registry.requireDefinition(endpointCode).endpointCode();
        SystemDataCenterEndpointConfig entity = findEndpoint(normalizedCode);
        boolean create = entity == null;
        if (create) {
            entity = registry.newEndpoint(normalizedCode);
        }
        registry.applyFixedDefinition(entity);
        SystemDataCenterEndpointConfig beforeState = create ? null : copyOf(entity);
        Map<String, Object> beforeAudit = create ? null : auditSummary(beforeState, null, null, false);

        applyRequest(entity, req);
        registry.applyFixedDefinition(entity);
        boolean changed = beforeState == null || endpointChanged(beforeState, entity);
        if (changed) {
            entity.setLastTestStatus(null);
            entity.setLastTestMessage(null);
            entity.setLastTestAt(null);
        }
        entity.setUpdatedAt(LocalDateTime.now());
        if (create) {
            entity.setCreatedAt(entity.getUpdatedAt());
            endpointMapper.insert(entity);
        } else {
            endpointMapper.updateById(entity);
        }
        auditService.record(
                "SYSTEM_DATA_CENTER_ENDPOINT_UPDATE",
                "system_data_center_endpoint_config",
                entity.getId(),
                beforeAudit,
                auditSummary(entity, null, null, false)
        );
        log.info(
                "Data center endpoint updated endpointCode={} targetSystemCode={} path={} timeoutMs={} enabled={}",
                entity.getEndpointCode(),
                entity.getTargetSystemCode(),
                entity.getPath(),
                entity.getTimeoutMs(),
                entity.getEnabled()
        );
        return toDTO(entity);
    }

    @Transactional
    public Map<String, Object> testEndpoint(String endpointCode) {
        String normalizedCode = registry.requireDefinition(endpointCode).endpointCode();
        SystemDataCenterEndpointConfig endpoint = requireEndpoint(normalizedCode);
        SystemDataCenterConfig activeConfig = null;
        boolean success = false;
        String message;
        Integer timeoutMs = null;
        String path = resolvePath(endpoint);
        try {
            validateEndpointConfig(endpoint, path);
            activeConfig = dataCenterConfigService.activeConfig();
            timeoutMs = resolveTimeout(endpoint, activeConfig);
            tokenService.fetchAccessToken(activeConfig);
            success = true;
            message = "Endpoint config complete; token fetched; business endpoint not called";
        } catch (RuntimeException e) {
            message = sanitize(e.getMessage(), activeConfig);
        }

        String status = success ? "SUCCESS" : "FAILED";
        updateTestResult(endpoint.getId(), status, message);
        Map<String, Object> summary = auditSummary(endpoint, activeConfig, path, success);
        summary.put("message", abbreviate(message));
        summary.put("timeoutMs", timeoutMs == null ? endpoint.getTimeoutMs() : timeoutMs);
        if (success) {
            auditService.record(
                    "SYSTEM_DATA_CENTER_ENDPOINT_TEST",
                    "system_data_center_endpoint_config",
                    endpoint.getId(),
                    null,
                    summary
            );
            log.info(
                    "Data center endpoint test completed endpointCode={} targetSystemCode={} host={} path={} success=true",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    host(activeConfig == null ? null : activeConfig.getBaseUrl()),
                    path
            );
        } else {
            auditService.record(
                    "SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED",
                    "system_data_center_endpoint_config",
                    endpoint.getId(),
                    null,
                    summary,
                    "SYSTEM",
                    "FAILED",
                    AuditService.labelForAction("SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED"),
                    abbreviate(message)
            );
            log.warn(
                    "Data center endpoint test failed endpointCode={} targetSystemCode={} path={} message={}",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    path,
                    abbreviate(message)
            );
        }
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", abbreviate(message));
        response.put("data", summary);
        return response;
    }

    SystemDataCenterEndpointConfig requireRuntimeEndpoint(String endpointCode) {
        String normalizedCode = registry.requireDefinition(endpointCode).endpointCode();
        SystemDataCenterEndpointConfig endpoint = requireEndpoint(normalizedCode);
        registry.applyFixedDefinition(endpoint);
        endpoint.setPath(resolvePath(endpoint));
        validateEndpointConfig(endpoint, endpoint.getPath());
        return endpoint;
    }

    private void applyRequest(SystemDataCenterEndpointConfig entity, SystemDataCenterEndpointConfigSaveReq req) {
        entity.setMethod(resolveMethod(req.getMethod(), entity.getMethod()));
        entity.setPath(trimToNull(req.getPath()));
        entity.setEnabled(req.getEnabled() == null ? Boolean.TRUE.equals(entity.getEnabled()) : req.getEnabled());
        entity.setTimeoutMs(req.getTimeoutMs() == null || req.getTimeoutMs() <= 0 ? null : req.getTimeoutMs());
        entity.setRequestSchemaJson(req.getRequestSchemaJson() == null ? emptySchema(entity.getRequestSchemaJson()) : req.getRequestSchemaJson());
        entity.setResponseSchemaJson(req.getResponseSchemaJson() == null ? emptySchema(entity.getResponseSchemaJson()) : req.getResponseSchemaJson());
        entity.setRemark(trimToNull(req.getRemark()));
    }

    private Object emptySchema(Object value) {
        return value == null ? new LinkedHashMap<>() : value;
    }

    private SystemDataCenterEndpointConfig requireEndpoint(String endpointCode) {
        SystemDataCenterEndpointConfig entity = findEndpoint(endpointCode);
        if (entity == null) {
            throw new IllegalArgumentException("Data center endpoint config not found endpointCode=" + endpointCode);
        }
        return entity;
    }

    private SystemDataCenterEndpointConfig findEndpoint(String endpointCode) {
        QueryWrapper<SystemDataCenterEndpointConfig> query = new QueryWrapper<>();
        query.eq("endpoint_code", endpointCode)
                .last("limit 1");
        return endpointMapper.selectOne(query);
    }

    private void validateEndpointConfig(SystemDataCenterEndpointConfig endpoint, String path) {
        if (!Boolean.TRUE.equals(endpoint.getEnabled())) {
            throw new IllegalStateException("Data center endpoint is disabled");
        }
        if (isBlank(path)) {
            throw new IllegalStateException("Data center endpoint path cannot be blank");
        }
        resolveMethod(endpoint.getMethod(), registry.requireDefinition(endpoint.getEndpointCode()).defaultMethod());
        registry.requireDefinition(endpoint.getEndpointCode());
    }

    private void updateTestResult(Long id, String status, String message) {
        SystemDataCenterEndpointConfig update = new SystemDataCenterEndpointConfig();
        update.setId(id);
        update.setLastTestStatus(status);
        update.setLastTestMessage(abbreviate(message));
        update.setLastTestAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        endpointMapper.updateById(update);
    }

    private boolean endpointChanged(SystemDataCenterEndpointConfig before, SystemDataCenterEndpointConfig after) {
        return !Objects.equals(before.getMethod(), after.getMethod())
                || !Objects.equals(before.getPath(), after.getPath())
                || !Objects.equals(before.getEnabled(), after.getEnabled())
                || !Objects.equals(before.getTimeoutMs(), after.getTimeoutMs())
                || !Objects.equals(before.getRequestSchemaJson(), after.getRequestSchemaJson())
                || !Objects.equals(before.getResponseSchemaJson(), after.getResponseSchemaJson());
    }

    private SystemDataCenterEndpointConfig copyOf(SystemDataCenterEndpointConfig source) {
        SystemDataCenterEndpointConfig copy = new SystemDataCenterEndpointConfig();
        copy.setId(source.getId());
        copy.setEndpointCode(source.getEndpointCode());
        copy.setDisplayName(source.getDisplayName());
        copy.setTargetSystemCode(source.getTargetSystemCode());
        copy.setMethod(source.getMethod());
        copy.setPath(source.getPath());
        copy.setEnabled(source.getEnabled());
        copy.setTimeoutMs(source.getTimeoutMs());
        copy.setRequestAdapter(source.getRequestAdapter());
        copy.setResponseAdapter(source.getResponseAdapter());
        copy.setRequestSchemaJson(source.getRequestSchemaJson());
        copy.setResponseSchemaJson(source.getResponseSchemaJson());
        copy.setLastTestStatus(source.getLastTestStatus());
        copy.setLastTestMessage(source.getLastTestMessage());
        copy.setLastTestAt(source.getLastTestAt());
        copy.setRemark(source.getRemark());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private SystemDataCenterEndpointConfigDTO toDTO(SystemDataCenterEndpointConfig entity) {
        registry.applyFixedDefinition(entity);
        SystemDataCenterEndpointConfigDTO dto = new SystemDataCenterEndpointConfigDTO();
        dto.setId(entity.getId());
        dto.setEndpointCode(entity.getEndpointCode());
        dto.setDisplayName(entity.getDisplayName());
        dto.setTargetSystemCode(entity.getTargetSystemCode());
        dto.setMethod(resolveMethod(entity.getMethod(), registry.requireDefinition(entity.getEndpointCode()).defaultMethod()));
        dto.setPath(entity.getPath());
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setTimeoutMs(entity.getTimeoutMs());
        dto.setRequestAdapter(entity.getRequestAdapter());
        dto.setResponseAdapter(entity.getResponseAdapter());
        dto.setRequestSchemaJson(emptySchema(entity.getRequestSchemaJson()));
        dto.setResponseSchemaJson(emptySchema(entity.getResponseSchemaJson()));
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestAt(entity.getLastTestAt());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> auditSummary(
            SystemDataCenterEndpointConfig endpoint,
            SystemDataCenterConfig config,
            String effectivePath,
            boolean success) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", endpoint.getId());
        summary.put("endpointCode", endpoint.getEndpointCode());
        summary.put("displayName", endpoint.getDisplayName());
        summary.put("targetSystemCode", endpoint.getTargetSystemCode());
        summary.put("method", endpoint.getMethod());
        summary.put("path", effectivePath == null ? endpoint.getPath() : effectivePath);
        summary.put("enabled", Boolean.TRUE.equals(endpoint.getEnabled()));
        summary.put("timeoutMs", endpoint.getTimeoutMs());
        summary.put("requestAdapter", endpoint.getRequestAdapter());
        summary.put("responseAdapter", endpoint.getResponseAdapter());
        summary.put("lastTestStatus", endpoint.getLastTestStatus());
        summary.put("success", success);
        summary.put("host", host(config == null ? null : config.getBaseUrl()));
        summary.put("remark", endpoint.getRemark());
        return summary;
    }

    private String resolvePath(SystemDataCenterEndpointConfig endpoint) {
        String path = trimToNull(endpoint.getPath());
        if (path != null) {
            return path;
        }
        return registry.fallbackPath(endpoint.getEndpointCode(), dataCenterProperties);
    }

    private int resolveTimeout(SystemDataCenterEndpointConfig endpoint, SystemDataCenterConfig config) {
        if (endpoint.getTimeoutMs() != null && endpoint.getTimeoutMs() > 0) {
            return endpoint.getTimeoutMs();
        }
        return config == null || config.getReadTimeoutMs() == null || config.getReadTimeoutMs() <= 0
                ? 30000
                : config.getReadTimeoutMs();
    }

    private String resolveMethod(String value, String fallback) {
        String method = trimToNull(value);
        if (method == null) {
            method = isBlank(fallback) ? "POST" : fallback;
        }
        method = method.toUpperCase(Locale.ROOT);
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("Data center endpoint method only supports GET or POST");
        }
        return method;
    }

    private String normalizeEndpointCode(String value) {
        return registry.requireDefinition(value).endpointCode();
    }

    private String sanitize(String message, SystemDataCenterConfig config) {
        String text = isBlank(message) ? "Data center endpoint test failed" : message;
        if (config != null && !isBlank(config.getClientSecretCipher())) {
            text = text.replace(config.getClientSecretCipher().trim(), "******");
        }
        return SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("$1=******");
    }

    private String host(String baseUrl) {
        if (isBlank(baseUrl)) {
            return null;
        }
        try {
            return URI.create(baseUrl.trim()).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
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
