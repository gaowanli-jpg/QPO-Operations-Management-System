package com.machining.quote.integration;

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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ExternalIntegrationTestService {

    public ExternalIntegrationTestResult test(ExternalIntegrationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("External integration config cannot be null");
        }
        return switch (config.systemCode()) {
            case MES -> postJson(config, config.materialPath(), mesProbePayload(), "MES");
            case WMS -> postJson(config, config.allocationPath(), wmsProbePayload(), "WMS");
            case U9 -> testU9(config);
        };
    }

    private ExternalIntegrationTestResult postJson(
            ExternalIntegrationConfig config,
            String path,
            Map<String, Object> payload,
            String systemName) {
        if (isBlank(config.baseUrl())) {
            return ExternalIntegrationTestResult.failed(systemName + " base URL is not configured", null);
        }
        String url = trimRight(config.baseUrl()) + normalizePath(path);
        try {
            ResponseEntity<String> response = restTemplate(timeout(config.timeoutMs(), 10000))
                    .exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers(config)), String.class);
            return ExternalIntegrationTestResult.success(
                    systemName + " endpoint reachable, httpStatus=" + response.getStatusCode().value(),
                    response.getStatusCode().value()
            );
        } catch (HttpStatusCodeException error) {
            return ExternalIntegrationTestResult.failed(
                    systemName + " endpoint test failed status=" + error.getStatusCode().value()
                            + " message=" + abbreviate(error.getResponseBodyAsString()),
                    error.getStatusCode().value()
            );
        } catch (ResourceAccessException error) {
            return ExternalIntegrationTestResult.failed(
                    systemName + " endpoint test failed message=" + abbreviate(error.getMessage()),
                    null
            );
        }
    }

    private ExternalIntegrationTestResult testU9(ExternalIntegrationConfig config) {
        if (isBlank(config.baseUrl())) {
            return ExternalIntegrationTestResult.failed("U9 base URL is not configured", null);
        }
        String url = UriComponentsBuilder.fromHttpUrl(trimRight(config.baseUrl()) + normalizePath(config.authLoginPath()))
                .queryParam("userCode", nullToEmpty(config.userCode()))
                .queryParam("entcode", nullToEmpty(config.entCode()))
                .queryParam("orgcode", nullToEmpty(config.orgCode()))
                .queryParam("clientid", nullToEmpty(config.clientId()))
                .queryParam("clientsecret", nullToEmpty(config.clientSecret()))
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate(timeout(config.connectTimeoutMs(), 5000), timeout(config.readTimeoutMs(), 30000))
                    .getForEntity(url, String.class);
            return ExternalIntegrationTestResult.success(
                    "U9 auth endpoint reachable, httpStatus=" + response.getStatusCode().value(),
                    response.getStatusCode().value()
            );
        } catch (HttpStatusCodeException error) {
            return ExternalIntegrationTestResult.failed(
                    "U9 auth endpoint test failed status=" + error.getStatusCode().value()
                            + " message=" + abbreviate(error.getResponseBodyAsString()),
                    error.getStatusCode().value()
            );
        } catch (ResourceAccessException error) {
            return ExternalIntegrationTestResult.failed(
                    "U9 auth endpoint test failed message=" + abbreviate(error.getMessage()),
                    null
            );
        }
    }

    private HttpHeaders headers(ExternalIntegrationConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!isBlank(config.authToken())) {
            headers.set("Authorization", "Bearer " + config.authToken().trim());
        }
        return headers;
    }

    private Map<String, Object> mesProbePayload() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 0);
        payload.put("rfqId", 0);
        payload.put("rfqNo", "QPO-CONFIG-TEST");
        payload.put("partNo", "QPO-CONFIG-TEST");
        payload.put("version", "TEST");
        payload.put("internalProjectNo", "QPO-CONFIG-TEST");
        return payload;
    }

    private Map<String, Object> wmsProbePayload() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 0);
        payload.put("rfqId", 0);
        payload.put("rfqNo", "QPO-CONFIG-TEST");
        payload.put("materialNo", "QPO-CONFIG-TEST");
        payload.put("partNo", "QPO-CONFIG-TEST");
        payload.put("version", "TEST");
        payload.put("internalProjectNo", "QPO-CONFIG-TEST");
        payload.put("demandQty", 0);
        return payload;
    }

    private RestTemplate restTemplate(int timeoutMs) {
        return restTemplate(timeoutMs, timeoutMs);
    }

    private RestTemplate restTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String trimRight(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
