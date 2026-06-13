package com.machining.apssystem.service;

import com.machining.apssystem.config.ApsAuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ApsJwtService {
    @Value("${aps.auth.jwt-secret}")
    private String secret;

    @Value("${aps.auth.jwt-ttl-minutes:720}")
    private long ttlMinutes;

    public String createToken(ApsAuthContext.SessionUser user) {
        long now = Instant.now().getEpochSecond();
        long exp = now + ttlMinutes * 60;
        String payloadJson = toJson(Map.of(
                "sub", user.username(),
                "displayName", user.displayName() == null ? "" : user.displayName(),
                "role", user.role(),
                "uid", user.id(),
                "iat", now,
                "exp", exp
        ));
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payloadJson);
        String signature = base64Url(hmacSha256(header + "." + payload));
        return header + "." + payload + "." + signature;
    }

    public ApsAuthContext.SessionUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = base64Url(hmacSha256(signingInput));
        if (!expected.equals(parts[2])) {
            return null;
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> map = parseJson(payload);
        long exp = longValue(map.get("exp"));
        if (Instant.now().getEpochSecond() > exp) {
            return null;
        }
        return new ApsAuthContext.SessionUser(
                Long.valueOf(String.valueOf(map.get("uid"))),
                String.valueOf(map.get("sub")),
                emptyToNull(String.valueOf(map.get("displayName"))),
                String.valueOf(map.get("role"))
        );
    }

    private byte[] hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT signing failed.", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number) {
                builder.append(value);
            } else {
                builder.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (trimmed.length() < 2) {
            return map;
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return map;
        }
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = stripQuotes(kv[0].trim());
            String raw = kv[1].trim();
            map.put(key, raw.startsWith("\"") && raw.endsWith("\"") ? stripQuotes(raw) : raw);
        }
        return map;
    }

    private String stripQuotes(String value) {
        String text = value;
        if (text.startsWith("\"")) {
            text = text.substring(1);
        }
        if (text.endsWith("\"")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.replace("\\\"", "\"");
    }

    private long longValue(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
