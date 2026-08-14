package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return exchange(endpointCode, requestPayload, null, responseType, null);
    }

    public <T> T callGet(String endpointCode, Map<String, ?> queryParams, Class<T> responseType) {
        return exchange(endpointCode, null, queryParams, responseType, HttpMethod.GET);
    }

    private <T> T exchange(
            String endpointCode,
            Object requestPayload,
            Map<String, ?> queryParams,
            Class<T> responseType,
            HttpMethod forcedMethod) {
        String normalizedEndpointCode = registry.requireDefinition(endpointCode).endpointCode();
        SystemDataCenterConfig config = null;
        SystemDataCenterEndpointConfig endpoint = null;
        String token = null;
        try {
            config = dataCenterConfigService.activeConfig();
            endpoint = requireRuntimeEndpoint(normalizedEndpointCode);
            token = tokenService.fetchAccessToken(config);
            HttpMethod method = forcedMethod == null
                    ? HttpMethod.valueOf(normalizeMethod(endpoint.getMethod()))
                    : forcedMethod;
            String url = joinUrl(config.getBaseUrl(), endpoint.getPath());
            if (method == HttpMethod.GET) {
                url = appendQuery(url, queryParams);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = method == HttpMethod.GET
                    ? new HttpEntity<>(headers)
                    : new HttpEntity<>(requestPayload, headers);
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
        } catch (DataCenterCallException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            String message = "Data center call failed status=" + e.getStatusCode().value();
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} status={}",
                    endpointCode(endpoint, normalizedEndpointCode),
                    targetSystemCode(endpoint),
                    host(config == null ? null : config.getBaseUrl()),
                    path(endpoint),
                    e.getStatusCode().value()
            );
            throw failure(
                    normalizedEndpointCode,
                    DataCenterCallException.FailureCertainty.DEFINITE_FAILURE,
                    e.getStatusCode().value(),
                    message,
                    e
            );
        } catch (ResourceAccessException e) {
            DataCenterCallException.FailureCertainty certainty = isResultUnknownTransport(e)
                    ? DataCenterCallException.FailureCertainty.RESULT_UNKNOWN
                    : DataCenterCallException.FailureCertainty.DEFINITE_FAILURE;
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} certainty={} message=network error",
                    endpointCode(endpoint, normalizedEndpointCode),
                    targetSystemCode(endpoint),
                    host(config == null ? null : config.getBaseUrl()),
                    path(endpoint),
                    certainty
            );
            throw failure(normalizedEndpointCode, certainty, null, "Data center call failed message=network error", e);
        } catch (RuntimeException e) {
            String message = sanitize(e.getMessage(), token, config);
            log.warn(
                    "Data center call failed endpointCode={} targetSystemCode={} host={} path={} message={}",
                    endpointCode(endpoint, normalizedEndpointCode),
                    targetSystemCode(endpoint),
                    host(config == null ? null : config.getBaseUrl()),
                    path(endpoint),
                    message
            );
            throw failure(
                    normalizedEndpointCode,
                    DataCenterCallException.FailureCertainty.DEFINITE_FAILURE,
                    null,
                    "Data center call failed message=" + message,
                    e
            );
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
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                jsonConverter.setObjectMapper(JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build());
            }
        }
        return restTemplate;
    }

    private int resolveReadTimeout(SystemDataCenterConfig config, SystemDataCenterEndpointConfig endpoint) {
        int resolved;
        if (endpoint != null && endpoint.getTimeoutMs() != null && endpoint.getTimeoutMs() > 0) {
            resolved = endpoint.getTimeoutMs();
        } else {
            resolved = timeout(config == null ? null : config.getReadTimeoutMs(), 30000);
        }
        return isLongRunningEndpoint(endpoint) ? Math.max(resolved, 60000) : resolved;
    }

    private boolean isLongRunningEndpoint(SystemDataCenterEndpointConfig endpoint) {
        if (endpoint == null || endpoint.getEndpointCode() == null) {
            return false;
        }
        String code = endpoint.getEndpointCode().trim().toUpperCase(Locale.ROOT);
        return DataCenterEndpointRegistry.U9_SALES_ORDER_SYNC.equals(code)
                || DataCenterEndpointRegistry.U9_COMMON_ENTITY_QUERY.equals(code)
                || DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE.equals(code)
                || DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE.equals(code)
                || DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH.equals(code)
                || DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST.equals(code)
                || DataCenterEndpointRegistry.MES_MATERIAL_PAGE.equals(code);
    }

    private DataCenterCallException failure(String endpointCode,
                                            DataCenterCallException.FailureCertainty certainty,
                                            Integer httpStatus,
                                            String message,
                                            Throwable cause) {
        return new DataCenterCallException(endpointCode, certainty, httpStatus, message, cause);
    }

    private boolean isResultUnknownTransport(ResourceAccessException error) {
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("connect timed out")
                || message.contains("connection refused")
                || message.contains("connectexception")
                || message.contains("failed to connect")) {
            return false;
        }
        return message.contains("read timed out")
                || message.contains("unexpected end of file")
                || message.contains("connection reset")
                || message.contains("premature")
                || message.contains("broken pipe")
                || message.contains("timed out");
    }

    private String endpointCode(SystemDataCenterEndpointConfig endpoint, String fallback) {
        return endpoint == null ? fallback : endpoint.getEndpointCode();
    }

    private String targetSystemCode(SystemDataCenterEndpointConfig endpoint) {
        return endpoint == null ? null : endpoint.getTargetSystemCode();
    }

    private String path(SystemDataCenterEndpointConfig endpoint) {
        return endpoint == null ? null : endpoint.getPath();
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

    private String appendQuery(String url, Map<String, ?> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, ?> entry : queryParams.entrySet()) {
            String key = trimToNull(entry.getKey());
            Object rawValue = entry.getValue();
            if (key == null || rawValue == null) {
                continue;
            }
            String value = trimToNull(String.valueOf(rawValue));
            if (value == null) {
                continue;
            }
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encodeQueryPart(key)).append('=').append(encodeQueryPart(value));
        }
        if (query.isEmpty()) {
            return url;
        }
        String separator = url.contains("?")
                ? (url.endsWith("?") || url.endsWith("&") ? "" : "&")
                : "?";
        return url + separator + query;
    }

    private String encodeQueryPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
