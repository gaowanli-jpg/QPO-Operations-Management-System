package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DataCenterIntegrationClient {
    private static final Logger log = LoggerFactory.getLogger(DataCenterIntegrationClient.class);
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
    @Autowired(required = false)
    private DataCenterProperties dataCenterProperties;

    public <T> T call(String endpointCode, Object requestPayload, Class<T> responseType) {
        SystemDataCenterConfig config = dataCenterConfigService.activeConfig();
        SystemDataCenterEndpointConfig endpoint = requireRuntimeEndpoint(endpointCode);
        String token = tokenService.fetchAccessToken(config);
        String url = joinUrl(config.getBaseUrl(), endpoint.getPath());
        HttpMethod method = HttpMethod.valueOf(normalizeMethod(endpoint.getMethod()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> entity = method == HttpMethod.GET
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(requestPayload, headers);
        try {
            ResponseEntity<T> response = restTemplate(config, endpoint).exchange(url, method, entity, responseType);
            log.info(
                    "Data center call completed endpointCode={} targetSystemCode={} host={} path={} status={}",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    host(config.getBaseUrl()),
                    endpoint.getPath(),
                    response.getStatusCode().value()
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            String message = "Data center call failed status=" + e.getStatusCode().value();
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} status={}",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    host(config.getBaseUrl()),
                    endpoint.getPath(),
                    e.getStatusCode().value()
            );
            throw new IllegalStateException(message, e);
        } catch (ResourceAccessException e) {
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} message=network error",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    host(config.getBaseUrl()),
                    endpoint.getPath()
            );
            throw new IllegalStateException("Data center call failed message=network error", e);
        } catch (RuntimeException e) {
            String message = sanitize(e.getMessage(), token, config);
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} message={}",
                    endpoint.getEndpointCode(),
                    endpoint.getTargetSystemCode(),
                    host(config.getBaseUrl()),
                    endpoint.getPath(),
                    message
            );
            throw new IllegalStateException("Data center call failed message=" + message, e);
        }
    }

    private SystemDataCenterEndpointConfig requireRuntimeEndpoint(String endpointCode) {
        String normalizedCode = registry.requireDefinition(endpointCode).endpointCode();
        QueryWrapper<SystemDataCenterEndpointConfig> query = new QueryWrapper<>();
        query.eq("endpoint_code", normalizedCode)
                .last("limit 1");
        SystemDataCenterEndpointConfig endpoint = endpointMapper.selectOne(query);
        if (endpoint == null) {
            throw new IllegalArgumentException("Data center endpoint config not found endpointCode=" + normalizedCode);
        }
        registry.applyFixedDefinition(endpoint);
        endpoint.setMethod(normalizeMethod(endpoint.getMethod()));
        endpoint.setPath(resolvePath(endpoint));
        if (!Boolean.TRUE.equals(endpoint.getEnabled())) {
            throw new IllegalStateException("Data center endpoint is disabled endpointCode=" + normalizedCode);
        }
        if (isBlank(endpoint.getPath())) {
            throw new IllegalStateException("Data center endpoint path cannot be blank endpointCode=" + normalizedCode);
        }
        return endpoint;
    }

    private RestTemplate restTemplate(SystemDataCenterConfig config, SystemDataCenterEndpointConfig endpoint) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(config == null ? null : config.getConnectTimeoutMs(), 5000));
        requestFactory.setReadTimeout(resolveReadTimeout(config, endpoint));
        return new RestTemplate(requestFactory);
    }

    private int resolveReadTimeout(SystemDataCenterConfig config, SystemDataCenterEndpointConfig endpoint) {
        if (endpoint != null && endpoint.getTimeoutMs() != null && endpoint.getTimeoutMs() > 0) {
            return endpoint.getTimeoutMs();
        }
        return timeout(config == null ? null : config.getReadTimeoutMs(), 30000);
    }

    private String resolvePath(SystemDataCenterEndpointConfig endpoint) {
        String path = trimToNull(endpoint.getPath());
        if (path != null) {
            return path;
        }
        return registry.fallbackPath(endpoint.getEndpointCode(), dataCenterProperties);
    }

    private String joinUrl(String baseUrl, String path) {
        String base = requireText(baseUrl, "Data center baseUrl cannot be blank");
        String endpointPath = requireText(path, "Data center endpoint path cannot be blank");
        if (endpointPath.toLowerCase(Locale.ROOT).startsWith("http://")
                || endpointPath.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return endpointPath;
        }
        if (base.endsWith("/") && endpointPath.startsWith("/")) {
            return base.substring(0, base.length() - 1) + endpointPath;
        }
        if (!base.endsWith("/") && !endpointPath.startsWith("/")) {
            return base + "/" + endpointPath;
        }
        return base + endpointPath;
    }

    private String normalizeMethod(String value) {
        String method = isBlank(value) ? "POST" : value.trim().toUpperCase(Locale.ROOT);
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("Data center endpoint method only supports GET or POST");
        }
        return method;
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
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

    private String sanitize(String message, String token, SystemDataCenterConfig config) {
        String text = isBlank(message) ? "Data center call failed" : message;
        if (!isBlank(token)) {
            text = text.replace(token.trim(), "******");
        }
        if (config != null && !isBlank(config.getClientSecretCipher())) {
            text = text.replace(config.getClientSecretCipher().trim(), "******");
        }
        return SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("$1=******");
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
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
