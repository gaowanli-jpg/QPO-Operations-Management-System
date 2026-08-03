package com.machining.quote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.SystemDataCenterConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class DataCenterTokenService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern ACCESS_TOKEN_FIELD = Pattern.compile("(?i)(access_token|refresh_token|token)\\s*[:=]\\s*\"?[^\",;\\s}]+\"?");
    private static final Pattern AUTHORIZATION_FIELD = Pattern.compile("(?i)authorization\\s*[:=]\\s*Bearer\\s+[^,;\\s]+");
    private static final Pattern CLIENT_SECRET_FIELD = Pattern.compile("(?i)(client_secret|clientSecret|clientsecret)\\s*[:=]\\s*[^,;\\s&}]+");

    @Autowired(required = false)
    private ObjectProvider<DataCenterConfigService> configServiceProvider;

    private volatile CachedToken cachedToken;

    public String fetchAccessToken() {
        DataCenterConfigService service = configServiceProvider == null ? null : configServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Data center config service is not available");
        }
        return fetchAccessToken(service.activeConfig());
    }

    public synchronized String fetchAccessToken(SystemDataCenterConfig config) {
        validate(config);
        String cacheKey = cacheKey(config);
        Instant now = Instant.now();
        CachedToken current = cachedToken;
        if (current != null && current.matches(cacheKey) && current.isUsable(now)) {
            return current.accessToken();
        }
        TokenResponse response = requestToken(config);
        int expiresIn = response.expiresInSeconds() <= 0 ? 300 : response.expiresInSeconds();
        int skew = Math.max(0, timeout(config.getTokenExpireSkewSeconds(), 60));
        Instant usableUntil = now.plusSeconds(Math.max(1, expiresIn - skew));
        cachedToken = new CachedToken(cacheKey, response.accessToken(), usableUntil);
        return response.accessToken();
    }

    public TokenTestResult testToken(SystemDataCenterConfig config) {
        try {
            TokenResponse response = requestToken(config);
            String message = "Data center token fetched, tokenType=" + nullToDefault(response.tokenType(), "Bearer")
                    + ", expiresIn=" + response.expiresInSeconds();
            return new TokenTestResult(true, message, response.httpStatus(), response.expiresInSeconds());
        } catch (RuntimeException e) {
            return new TokenTestResult(false, sanitizeMessage(e.getMessage(), config), null, null);
        }
    }

    private TokenResponse requestToken(SystemDataCenterConfig config) {
        validate(config);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", trim(config.getClientId()));
        body.add("client_secret", trim(config.getClientSecretCipher()));
        body.add("grant_type", nullToDefault(trim(config.getGrantType()), "client_credentials"));

        try {
            ResponseEntity<String> response = restTemplate(config).exchange(
                    trim(config.getTokenUrl()),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            TokenResponse token = parseTokenResponse(response.getBody(), response.getStatusCode().value());
            if (isBlank(token.accessToken())) {
                throw new IllegalStateException("Data center token response does not contain access_token");
            }
            return token;
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Data center token fetch failed status=" + e.getStatusCode().value()
                    + " message=" + sanitizeMessage(e.getResponseBodyAsString(), config), e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Data center token fetch failed message=network error", e);
        } catch (RuntimeException e) {
            String sanitized = sanitizeMessage(e.getMessage(), config);
            if (sanitized != null && sanitized.startsWith("Data center token fetch failed")) {
                throw e;
            }
            throw new IllegalStateException("Data center token fetch failed message=" + sanitized, e);
        }
    }

    private TokenResponse parseTokenResponse(String body, int httpStatus) {
        if (isBlank(body)) {
            return new TokenResponse(null, null, 0, httpStatus);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String accessToken = text(root, "access_token");
            String tokenType = text(root, "token_type");
            int expiresIn = integer(root, "expires_in");
            return new TokenResponse(accessToken, tokenType, expiresIn, httpStatus);
        } catch (Exception e) {
            throw new IllegalStateException("Data center token response is not valid JSON");
        }
    }

    private RestTemplate restTemplate(SystemDataCenterConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(config.getConnectTimeoutMs(), 5000));
        requestFactory.setReadTimeout(timeout(config.getReadTimeoutMs(), 30000));
        return new RestTemplate(requestFactory);
    }

    private void validate(SystemDataCenterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Data center config cannot be null");
        }
        require(config.getTokenUrl(), "Data center tokenUrl cannot be blank");
        require(config.getClientId(), "Data center clientId cannot be blank");
        require(config.getClientSecretCipher(), "Data center clientSecret cannot be blank");
        String grantType = nullToDefault(config.getGrantType(), "client_credentials");
        if (!"client_credentials".equalsIgnoreCase(grantType.trim())) {
            throw new IllegalArgumentException("Data center grantType only supports client_credentials");
        }
    }

    private String sanitizeMessage(String message, SystemDataCenterConfig config) {
        String text = isBlank(message) ? "Data center token fetch failed" : message;
        if (config != null && !isBlank(config.getClientSecretCipher())) {
            text = text.replace(config.getClientSecretCipher().trim(), "******");
        }
        return AUTHORIZATION_FIELD.matcher(
                ACCESS_TOKEN_FIELD.matcher(
                        CLIENT_SECRET_FIELD.matcher(text).replaceAll("$1=******")
                ).replaceAll("$1=******")
        ).replaceAll("authorization=******");
    }

    private String cacheKey(SystemDataCenterConfig config) {
        return String.join("|",
                String.valueOf(config.getId()),
                trim(config.getTokenUrl()),
                trim(config.getClientId()),
                trim(config.getClientSecretCipher()),
                nullToDefault(trim(config.getGrantType()), "client_credentials"));
    }

    private void require(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private int integer(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        return node != null && node.canConvertToInt() ? node.asInt() : 0;
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String nullToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record TokenTestResult(boolean success, String message, Integer httpStatus, Integer expiresInSeconds) {
    }

    private record TokenResponse(String accessToken, String tokenType, int expiresInSeconds, int httpStatus) {
    }

    private record CachedToken(String cacheKey, String accessToken, Instant usableUntil) {
        boolean matches(String otherKey) {
            return Objects.equals(cacheKey, otherKey);
        }

        boolean isUsable(Instant now) {
            return !isBlankStatic(accessToken) && usableUntil != null && usableUntil.isAfter(now);
        }

        private static boolean isBlankStatic(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
