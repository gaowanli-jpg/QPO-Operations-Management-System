package com.machining.quote.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class ObjectKeySupport {
    private ObjectKeySupport() {
    }

    static Path safeRelativePath(String objectKey) {
        String normalized = safeObjectKey(objectKey);
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("Object key is outside storage root");
        }
        return path;
    }

    static String safeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Object key cannot be blank");
        }
        String normalized = objectKey.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("/..")) {
            throw new IllegalArgumentException("Object key is outside storage root");
        }
        return normalized;
    }

    static String joinUrl(String baseUrl, String objectKey) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + encodePath(safeObjectKey(objectKey));
    }

    private static String encodePath(String objectKey) {
        String[] segments = objectKey.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }
}
