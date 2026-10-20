package com.machining.quote.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DrawingPartNoResolver {
    private DrawingPartNoResolver() {
    }

    public record DrawingIdentity(String partNo, String version) {
        public DrawingIdentity {
            partNo = normalizePartNo(partNo);
            version = normalizeVersion(version);
        }
    }

    public static String extractPartNo(String filename, Collection<String> knownPartNos) {
        String fallback = fallbackPartNo(filename);
        if (fallback.isEmpty() || knownPartNos == null || knownPartNos.isEmpty()) {
            return fallback;
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (String partNo : knownPartNos) {
            String normalized = normalizePartNo(partNo);
            if (!normalized.isEmpty()) {
                candidates.add(normalized);
            }
        }
        if (candidates.contains(fallback)) {
            return fallback;
        }

        List<String> longestFirst = candidates.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String candidate : longestFirst) {
            if (isPrefixMatch(fallback, candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    public static DrawingIdentity extractPartNoVersion(
            String filename,
            Collection<DrawingIdentity> knownIdentities) {
        String filenameBase = fallbackPartNo(filename);
        if (filenameBase.isEmpty() || knownIdentities == null || knownIdentities.isEmpty()) {
            return null;
        }

        Set<DrawingIdentity> candidates = new LinkedHashSet<>();
        for (DrawingIdentity identity : knownIdentities) {
            if (identity == null) {
                continue;
            }
            DrawingIdentity normalized = new DrawingIdentity(identity.partNo(), identity.version());
            if (!normalized.partNo().isEmpty() && !normalized.version().isEmpty()) {
                candidates.add(normalized);
            }
        }

        List<DrawingIdentity> longestFirst = candidates.stream()
                .sorted(Comparator.comparingInt((DrawingIdentity identity) -> identityKey(identity).length()).reversed())
                .toList();
        for (DrawingIdentity candidate : longestFirst) {
            if (isPrefixMatch(filenameBase, identityKey(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    public static String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    public static String normalizeVersion(String version) {
        return version == null ? "" : version.trim().toUpperCase();
    }

    private static String identityKey(DrawingIdentity identity) {
        return identity.partNo() + "_" + identity.version();
    }

    private static String fallbackPartNo(String filename) {
        String clean = cleanFilename(filename);
        if (clean.isEmpty()) {
            return "";
        }
        int dotIndex = clean.lastIndexOf('.');
        String baseName = dotIndex > 0 ? clean.substring(0, dotIndex) : clean;
        return normalizePartNo(baseName);
    }

    private static String cleanFilename(String filename) {
        if (filename == null) {
            return "";
        }
        return filename.replaceAll(".*[\\\\/]", "").trim();
    }

    private static boolean isPrefixMatch(String filenameBase, String partNo) {
        if (!filenameBase.startsWith(partNo)) {
            return false;
        }
        if (filenameBase.length() == partNo.length()) {
            return true;
        }
        char next = filenameBase.charAt(partNo.length());
        if (next == '-') {
            return false;
        }
        return !isAsciiLetterOrDigit(next);
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }
}
