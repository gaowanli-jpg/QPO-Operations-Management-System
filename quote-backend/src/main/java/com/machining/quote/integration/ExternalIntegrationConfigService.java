package com.machining.quote.integration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SystemIntegrationConfigDTO;
import com.machining.quote.entity.SystemExternalIntegrationConfig;
import com.machining.quote.mapper.SystemExternalIntegrationConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ExternalIntegrationConfigService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED_SECRET = "******";

    private final SystemExternalIntegrationConfigMapper configMapper;

    @Value("${erp.mes.base-url:}")
    private String mesBaseUrl;
    @Value("${erp.mes.auth-token:}")
    private String mesAuthToken;
    @Value("${erp.mes.material-path:/api/mes/materials/resolve}")
    private String mesMaterialPath;
    @Value("${erp.mes.timeout-ms:10000}")
    private Integer mesTimeoutMs;

    @Value("${erp.u9.base-url:}")
    private String u9BaseUrl;
    @Value("${erp.u9.auth-login-path:/webapi/OAuth2/AuthLogin}")
    private String u9AuthLoginPath;
    @Value("${erp.u9.sales-order-create-path:/webapi/SO/Create}")
    private String u9SalesOrderCreatePath;
    @Value("${erp.u9.user-code:}")
    private String u9UserCode;
    @Value("${erp.u9.ent-code:}")
    private String u9EntCode;
    @Value("${erp.u9.org-code:}")
    private String u9OrgCode;
    @Value("${erp.u9.client-id:}")
    private String u9ClientId;
    @Value("${erp.u9.client-secret:}")
    private String u9ClientSecret;
    @Value("${erp.u9.connect-timeout-ms:5000}")
    private Integer u9ConnectTimeoutMs;
    @Value("${erp.u9.read-timeout-ms:30000}")
    private Integer u9ReadTimeoutMs;

    @Autowired
    public ExternalIntegrationConfigService(SystemExternalIntegrationConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public ExternalIntegrationConfig activeConfig(String systemCode) {
        return activeConfig(ExternalSystemCode.fromCode(systemCode));
    }

    public ExternalIntegrationConfig activeConfig(ExternalSystemCode systemCode) {
        SystemExternalIntegrationConfig entity = activeEntity(systemCode);
        if (entity != null) {
            return toDomain(entity);
        }
        return fallbackConfig(systemCode);
    }

    public ExternalIntegrationConfig toDomain(SystemExternalIntegrationConfig entity) {
        if (entity == null) {
            throw new IllegalArgumentException("External integration config entity cannot be null");
        }
        ExternalSystemCode systemCode = ExternalSystemCode.fromCode(entity.getSystemCode());
        ExternalAuthType authType = ExternalAuthType.fromCode(entity.getAuthType());
        Map<String, Object> config = configMap(entity.getConfigJson());
        return switch (systemCode) {
            case MES -> new ExternalIntegrationConfig(
                    entity.getId(),
                    entity.getName(),
                    ExternalSystemCode.MES,
                    Boolean.TRUE.equals(entity.getActive()),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    authType,
                    trimRight(text(config, "baseUrl", null)),
                    normalizePath(text(config, "materialPath", "/api/mes/materials/resolve")),
                    null,
                    text(config, "authToken", null),
                    integer(config, "timeoutMs", 10000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    entity.getRemark()
            );
            case WMS -> new ExternalIntegrationConfig(
                    entity.getId(),
                    entity.getName(),
                    ExternalSystemCode.WMS,
                    Boolean.TRUE.equals(entity.getActive()),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    authType,
                    trimRight(text(config, "baseUrl", null)),
                    null,
                    normalizePath(text(config, "allocationPath", null)),
                    text(config, "authToken", null),
                    integer(config, "timeoutMs", 10000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    entity.getRemark()
            );
            case U9 -> new ExternalIntegrationConfig(
                    entity.getId(),
                    entity.getName(),
                    ExternalSystemCode.U9,
                    Boolean.TRUE.equals(entity.getActive()),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    authType,
                    trimRight(text(config, "baseUrl", null)),
                    null,
                    null,
                    null,
                    null,
                    normalizePath(text(config, "authLoginPath", "/webapi/OAuth2/AuthLogin")),
                    normalizePath(text(config, "salesOrderCreatePath", "/webapi/SO/Create")),
                    text(config, "userCode", null),
                    text(config, "entCode", null),
                    text(config, "orgCode", null),
                    text(config, "clientId", null),
                    text(config, "clientSecret", null),
                    integer(config, "connectTimeoutMs", 5000),
                    integer(config, "readTimeoutMs", 30000),
                    entity.getRemark()
            );
        };
    }

    public SystemIntegrationConfigDTO toDTO(SystemExternalIntegrationConfig entity) {
        ExternalIntegrationConfig config = toDomain(entity);
        SystemIntegrationConfigDTO dto = new SystemIntegrationConfigDTO();
        dto.setId(entity.getId());
        dto.setSystemCode(config.systemCode().name());
        dto.setName(entity.getName());
        dto.setActive(Boolean.TRUE.equals(entity.getActive()));
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setAuthType(config.authType().name());
        dto.setBaseUrl(config.baseUrl());
        dto.setMaterialPath(config.materialPath());
        dto.setAllocationPath(config.allocationPath());
        dto.setAuthToken(redact(config.authToken()));
        dto.setTimeoutMs(config.timeoutMs());
        dto.setAuthLoginPath(config.authLoginPath());
        dto.setSalesOrderCreatePath(config.salesOrderCreatePath());
        dto.setUserCode(config.userCode());
        dto.setEntCode(config.entCode());
        dto.setOrgCode(config.orgCode());
        dto.setClientId(config.clientId());
        dto.setClientSecret(redact(config.clientSecret()));
        dto.setConnectTimeoutMs(config.connectTimeoutMs());
        dto.setReadTimeoutMs(config.readTimeoutMs());
        dto.setConfigJson(redactedConfig(configMap(entity.getConfigJson())));
        dto.setRemark(entity.getRemark());
        dto.setLastTestStatus(entity.getLastTestStatus());
        dto.setLastTestMessage(entity.getLastTestMessage());
        dto.setLastTestAt(entity.getLastTestAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private SystemExternalIntegrationConfig activeEntity(ExternalSystemCode systemCode) {
        if (configMapper == null) {
            return null;
        }
        QueryWrapper<SystemExternalIntegrationConfig> query = new QueryWrapper<>();
        query.eq("system_code", systemCode.name())
                .eq("active", true)
                .eq("enabled", true)
                .last("LIMIT 1");
        return configMapper.selectOne(query);
    }

    private ExternalIntegrationConfig fallbackConfig(ExternalSystemCode systemCode) {
        return switch (systemCode) {
            case MES -> new ExternalIntegrationConfig(
                    null,
                    "MES environment fallback",
                    ExternalSystemCode.MES,
                    false,
                    true,
                    isBlank(mesAuthToken) ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                    trimRight(mesBaseUrl),
                    normalizePath(defaultText(mesMaterialPath, "/api/mes/materials/resolve")),
                    null,
                    blankToNull(mesAuthToken),
                    positive(mesTimeoutMs, 10000),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Loaded from erp.env fallback"
            );
            case WMS -> new ExternalIntegrationConfig(
                    null,
                    "WMS data-center only",
                    ExternalSystemCode.WMS,
                    false,
                    false,
                    ExternalAuthType.NONE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "WMS inventory allocation is configured through Data Center endpoints"
            );
            case U9 -> new ExternalIntegrationConfig(
                    null,
                    "U9 environment fallback",
                    ExternalSystemCode.U9,
                    false,
                    true,
                    ExternalAuthType.U9_AUTH_LOGIN,
                    trimRight(u9BaseUrl),
                    null,
                    null,
                    null,
                    null,
                    normalizePath(defaultText(u9AuthLoginPath, "/webapi/OAuth2/AuthLogin")),
                    normalizePath(defaultText(u9SalesOrderCreatePath, "/webapi/SO/Create")),
                    blankToNull(u9UserCode),
                    blankToNull(u9EntCode),
                    blankToNull(u9OrgCode),
                    blankToNull(u9ClientId),
                    blankToNull(u9ClientSecret),
                    positive(u9ConnectTimeoutMs, 5000),
                    positive(u9ReadTimeoutMs, 30000),
                    "Loaded from erp.env fallback"
            );
        };
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

    private Map<String, Object> redactedConfig(Map<String, Object> config) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(config);
        redactInPlace(result, "authToken");
        redactInPlace(result, "clientSecret");
        return result;
    }

    private void redactInPlace(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            config.put(key, REDACTED_SECRET);
        }
    }

    private String text(Map<String, Object> config, String key, String fallback) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private Integer integer(Map<String, Object> config, String key, int fallback) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        if (value instanceof Number number) {
            return positive(number.intValue(), fallback);
        }
        try {
            return positive(Integer.parseInt(String.valueOf(value).trim()), fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Integer positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String normalizePath(String value) {
        if (isBlank(value)) {
            return null;
        }
        String path = value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String trimRight(String value) {
        if (isBlank(value)) {
            return null;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String redact(String value) {
        return isBlank(value) ? null : REDACTED_SECRET;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
