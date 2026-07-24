package com.machining.quote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class U9TokenService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.u9.base-url:}")
    private String u9BaseUrl;
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
    private Integer connectTimeoutMs;
    @Value("${erp.u9.read-timeout-ms:30000}")
    private Integer readTimeoutMs;

    public String fetchToken() {
        validateConfiguration();
        SimpleClientHttpRequestFactory requestFactory = requestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        String url = UriComponentsBuilder.fromHttpUrl(trimRight(u9BaseUrl) + "/webapi/OAuth2/AuthLogin")
                .queryParam("userCode", u9UserCode)
                .queryParam("entcode", u9EntCode)
                .queryParam("orgcode", u9OrgCode)
                .queryParam("clientid", u9ClientId)
                .queryParam("clientsecret", u9ClientSecret)
                .toUriString();
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String token = extractToken(response.getBody());
        if (isBlank(token)) {
            throw new IllegalStateException("U9 token response does not contain token");
        }
        return token;
    }

    private void validateConfiguration() {
        require(u9BaseUrl, "ERP_U9_BASE_URL");
        require(u9UserCode, "ERP_U9_USER_CODE");
        require(u9EntCode, "ERP_U9_ENT_CODE");
        require(u9OrgCode, "ERP_U9_ORG_CODE");
        require(u9ClientId, "ERP_U9_CLIENT_ID");
        require(u9ClientSecret, "ERP_U9_CLIENT_SECRET");
    }

    private String extractToken(String body) {
        if (isBlank(body)) {
            return "";
        }
        String text = body.trim();
        if (!text.startsWith("{")) {
            return text;
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            for (String field : new String[]{"token", "Token", "access_token", "AccessToken"}) {
                JsonNode node = root.get(field);
                if (node != null && node.isTextual()) {
                    return node.asText();
                }
            }
            JsonNode data = root.get("Data");
            if (data != null) {
                if (data.isTextual()) {
                    return data.asText();
                }
                for (String field : new String[]{"token", "Token", "access_token", "AccessToken"}) {
                    JsonNode node = data.get(field);
                    if (node != null && node.isTextual()) {
                        return node.asText();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(connectTimeoutMs, 5000));
        requestFactory.setReadTimeout(timeout(readTimeoutMs, 30000));
        return requestFactory;
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private void require(String value, String envName) {
        if (isBlank(value)) {
            throw new IllegalStateException(envName + " is required for U9 sales order sync");
        }
    }

    private String trimRight(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
