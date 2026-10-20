package com.machining.quote.service.auth;

import com.machining.quote.dto.auth.DataCenterSsoExchangeReq;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class DataCenterSsoSignatureService {
    private static final String HMAC_SHA256 = "HmacSHA256";

    public String canonicalString(DataCenterSsoExchangeReq req, String defaultRedirect) {
        String redirect = normalizeRedirect(req == null ? null : req.getRedirect(), defaultRedirect);
        return String.join("\n",
                "appCode=" + trim(req == null ? null : req.getAppCode()),
                "nonce=" + trim(req == null ? null : req.getNonce()),
                "redirect=" + redirect,
                "timestamp=" + (req == null || req.getTimestamp() == null ? "" : req.getTimestamp()),
                "username=" + trim(req == null ? null : req.getUsername()));
    }

    public String sign(DataCenterSsoExchangeReq req, String sharedSecret, String defaultRedirect) {
        if (isBlank(sharedSecret)) {
            throw new IllegalArgumentException("shared secret cannot be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(sharedSecret.trim().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(canonicalString(req, defaultRedirect).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sign data center SSO request failed", e);
        }
    }

    public void validate(DataCenterSsoExchangeReq req, DataCenterSsoRuntimeConfig config, Instant now) {
        if (req == null) {
            throw new IllegalArgumentException("SSO request cannot be null");
        }
        if (config == null || !config.enabled()) {
            throw new IllegalArgumentException("SSO config is disabled");
        }
        requireText(req.getAppCode(), "appCode cannot be blank");
        requireText(req.getUsername(), "username cannot be blank");
        requireText(req.getNonce(), "nonce cannot be blank");
        requireText(req.getSignature(), "signature cannot be blank");
        if (req.getTimestamp() == null) {
            throw new IllegalArgumentException("timestamp cannot be blank");
        }
        if (!trim(req.getAppCode()).equals(trim(config.appCode()))) {
            throw new IllegalArgumentException("appCode mismatch");
        }
        validateTimestamp(req.getTimestamp(), now == null ? Instant.now() : now, config.timestampSkewSeconds());
        validateRedirect(normalizeRedirect(req.getRedirect(), config.defaultRedirect()), config.allowedRedirectPrefixes());
        String expected = sign(req, config.sharedSecret(), config.defaultRedirect());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), trim(req.getSignature()).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("signature mismatch");
        }
    }

    public String nonceHash(String appCode, String nonce) {
        String source = trim(appCode).toUpperCase(Locale.ROOT) + ":" + trim(nonce);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hash data center SSO nonce failed", e);
        }
    }

    private void validateTimestamp(Long timestamp, Instant now, int skewSeconds) {
        Instant requestAt;
        try {
            requestAt = Instant.ofEpochMilli(timestamp);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("timestamp invalid", e);
        }
        long delta = Math.abs(Duration.between(requestAt, now).toMillis());
        long allowed = Math.max(1, skewSeconds) * 1000L;
        if (delta > allowed) {
            throw new IllegalArgumentException("timestamp outside allowed window");
        }
    }

    private void validateRedirect(String redirect, List<String> allowedPrefixes) {
        if (isExternalRedirect(redirect)) {
            throw new IllegalArgumentException("redirect must be internal");
        }
        if (!redirect.startsWith("/")) {
            throw new IllegalArgumentException("redirect must start with /");
        }
        List<String> prefixes = allowedPrefixes == null || allowedPrefixes.isEmpty() ? List.of("/") : allowedPrefixes;
        boolean allowed = prefixes.stream()
                .map(prefix -> prefix == null ? "" : prefix.trim())
                .filter(prefix -> !prefix.isEmpty())
                .anyMatch(redirect::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("redirect not allowed");
        }
    }

    private String normalizeRedirect(String redirect, String defaultRedirect) {
        String normalized = trim(redirect);
        if (normalized.isEmpty()) {
            normalized = trim(defaultRedirect);
        }
        return normalized.isEmpty() ? "/dashboard" : normalized;
    }

    private boolean isExternalRedirect(String redirect) {
        String value = trim(redirect).toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//");
    }

    private String requireText(String value, String message) {
        String normalized = trim(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
