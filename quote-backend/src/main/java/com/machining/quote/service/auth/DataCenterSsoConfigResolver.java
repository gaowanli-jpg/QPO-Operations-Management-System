package com.machining.quote.service.auth;

import com.machining.quote.config.DataCenterSsoProperties;
import com.machining.quote.entity.SystemDataCenterSsoConfig;
import com.machining.quote.mapper.SystemDataCenterSsoConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DataCenterSsoConfigResolver {
    private final SystemDataCenterSsoConfigMapper configMapper;
    private final DataCenterSsoProperties properties;

    @Autowired
    public DataCenterSsoConfigResolver(SystemDataCenterSsoConfigMapper configMapper, DataCenterSsoProperties properties) {
        this.configMapper = configMapper;
        this.properties = properties;
    }

    public DataCenterSsoRuntimeConfig resolve() {
        SystemDataCenterSsoConfig active = configMapper == null ? null : configMapper.selectActive();
        if (active != null && Boolean.TRUE.equals(active.getEnabled()) && !isBlank(active.getSharedSecretCipher())) {
            return new DataCenterSsoRuntimeConfig(
                    "DB",
                    true,
                    defaultText(active.getAppCode(), "QPO"),
                    active.getSharedSecretCipher().trim(),
                    positive(active.getTimestampSkewSeconds(), 120),
                    positive(active.getNonceTtlMinutes(), 10),
                    defaultText(active.getDefaultRedirect(), "/dashboard"),
                    prefixesFromText(active.getAllowedRedirectPrefixes())
            );
        }
        return new DataCenterSsoRuntimeConfig(
                "ENV",
                properties != null && properties.isEnabled(),
                defaultText(properties == null ? null : properties.getAppCode(), "QPO"),
                defaultText(properties == null ? null : properties.getSharedSecret(), ""),
                positive(properties == null ? null : properties.getTimestampSkewSeconds(), 120),
                positive(properties == null ? null : properties.getNonceTtlMinutes(), 10),
                defaultText(properties == null ? null : properties.getDefaultRedirect(), "/dashboard"),
                prefixesFromList(properties == null ? null : properties.getAllowedRedirectPrefixes())
        );
    }

    private List<String> prefixesFromText(String value) {
        if (isBlank(value)) {
            return List.of("/", "/dashboard", "/orders");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> prefixesFromList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("/", "/dashboard", "/orders");
        }
        List<String> normalized = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("/", "/dashboard", "/orders") : normalized;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
