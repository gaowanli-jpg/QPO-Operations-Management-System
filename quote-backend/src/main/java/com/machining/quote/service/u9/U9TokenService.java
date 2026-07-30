package com.machining.quote.service.u9;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class U9TokenService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.u9.base-url:}")
    private String u9BaseUrl;
    @Value("${erp.u9.auth-login-path:/webapi/OAuth2/AuthLogin}")
    private String u9AuthLoginPath;
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

    @Autowired(required = false)
    private ExternalIntegrationConfigService externalIntegrationConfigService;

    public String fetchToken() {
        ExternalIntegrationConfig config = runtimeConfig();
        validateConfiguration(config);
        SimpleClientHttpRequestFactory requestFactory = requestFactory(config);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        String url = UriComponentsBuilder.fromHttpUrl(trimRight(config.baseUrl()) + normalizePath(config.authLoginPath()))
                .queryParam("userCode", config.userCode())
                .queryParam("entcode", config.entCode())
                .queryParam("orgcode", config.orgCode())
                .queryParam("clientid", config.clientId())
                .queryParam("clientsecret", config.clientSecret())
                .toUriString();
        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("U9 token fetch failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()));
        } catch (ResourceAccessException error) {
            throw new RuntimeException("U9 token fetch failed message=network error");
        }
        String token = extractToken(response.getBody());
        if (isBlank(token)) {
            throw new IllegalStateException("U9 token response does not contain token");
        }
        return token;
    }

    private void validateConfiguration(ExternalIntegrationConfig config) {
        require(config.baseUrl(), "ERP_U9_BASE_URL");
        require(config.userCode(), "ERP_U9_USER_CODE");
        require(config.entCode(), "ERP_U9_ENT_CODE");
        require(config.orgCode(), "ERP_U9_ORG_CODE");
        require(config.clientId(), "ERP_U9_CLIENT_ID");
        require(config.clientSecret(), "ERP_U9_CLIENT_SECRET");
    }

    private ExternalIntegrationConfig runtimeConfig() {
        if (externalIntegrationConfigService != null) {
            return externalIntegrationConfigService.activeConfig(ExternalSystemCode.U9);
        }
        return new ExternalIntegrationConfig(
                null,
                "U9 legacy fields",
                ExternalSystemCode.U9,
                false,
                true,
                ExternalAuthType.U9_AUTH_LOGIN,
                trimRight(u9BaseUrl),
                null,
                null,
                null,
                null,
                normalizePath(u9AuthLoginPath),
                null,
                blankToNull(u9UserCode),
                blankToNull(u9EntCode),
                blankToNull(u9OrgCode),
                blankToNull(u9ClientId),
                blankToNull(u9ClientSecret),
                timeout(connectTimeoutMs, 5000),
                timeout(readTimeoutMs, 30000),
                null
        );
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

    private SimpleClientHttpRequestFactory requestFactory(ExternalIntegrationConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(config.connectTimeoutMs(), 5000));
        requestFactory.setReadTimeout(timeout(config.readTimeoutMs(), 30000));
        return requestFactory;
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("(?i)(clientsecret|clientSecret|token)=([^&\\s]+)", "$1=******")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private void require(String value, String envName) {
        if (isBlank(value)) {
            throw new IllegalStateException(envName + " is required for U9 sales order sync");
        }
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/webapi/OAuth2/AuthLogin" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
