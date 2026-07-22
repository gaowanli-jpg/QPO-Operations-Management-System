package com.machining.quote.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DrawingPartNoResolver {
    private DrawingPartNoResolver() {
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

    public static String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
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
        return !isAsciiLetterOrDigit(next);
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }
}
