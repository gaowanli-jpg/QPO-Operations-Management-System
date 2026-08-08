package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class QuoteProcessExportSupport {
    static final List<String> PROCESS_VALUE_HEADERS = List.of("大类编码", "大类名称", "具体工艺", "工时", "小时价格", "金额");

    private QuoteProcessExportSupport() {
    }

    static List<String> collectProcessNames(List<ItemQuoteVersion> items,
                                            Function<Object, List<Map<String, Object>>> routeNormalizer) {
        List<String> names = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            for (String name : mergeProcessRoutes(routeNormalizer.apply(item.getProcessRoute())).keySet()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    static Map<String, MergedProcess> mergeProcessRoutes(List<Map<String, Object>> routes) {
        Map<String, ProcessAccumulator> accumulators = new LinkedHashMap<>();
        for (Map<String, Object> route : routes) {
            ProcessIdentity identity = processIdentity(route);
            if (identity.label().isEmpty()) {
                continue;
            }
            ProcessAccumulator accumulator = accumulators.computeIfAbsent(identity.key(), ignored -> new ProcessAccumulator(identity));
            String calcType = safe(route.get("calcType")).trim();
            if (accumulator.calcType.isEmpty() && !calcType.isEmpty()) {
                accumulator.calcType = calcType;
            }
            accumulator.inputVal += processWorkHours(route);
            accumulator.lineCost += processLineCost(route);
        }

        Map<String, MergedProcess> result = new LinkedHashMap<>();
        for (ProcessAccumulator accumulator : accumulators.values()) {
            String label = uniqueLabel(accumulator.identity.label(), accumulator.identity.key(), result);
            result.put(label, new MergedProcess(
                    label,
                    accumulator.identity.processCode,
                    accumulator.identity.categoryName,
                    accumulator.identity.detailName,
                    accumulator.calcType,
                    accumulator.inputVal,
                    accumulator.lineCost
            ));
        }
        return result;
    }

    private static ProcessIdentity processIdentity(Map<String, Object> route) {
        String processDetailId = safe(route.get("processDetailId")).trim();
        String processCode = safe(route.get("processCode")).trim();
        String categoryName = safe(route.get("categoryName")).trim();
        String detailName = firstText(route.get("detailName"), route.get("processName"), route.get("name"));
        String label = !processCode.isEmpty() && !detailName.isEmpty()
                ? processCode + " / " + detailName
                : firstText(detailName, processCode, categoryName);
        String key;
        if (!processDetailId.isEmpty()) {
            key = "id:" + processDetailId;
        } else if (!processCode.isEmpty() && !detailName.isEmpty()) {
            key = "snapshot:" + processCode + "::" + detailName;
        } else {
            key = "legacy:" + label;
        }
        return new ProcessIdentity(key, label, processCode, categoryName, detailName);
    }

    private static String uniqueLabel(String label, String key, Map<String, MergedProcess> result) {
        if (!result.containsKey(label)) {
            return label;
        }
        String suffix = key.startsWith("id:") ? key.substring(3) : key.replace(':', '-');
        String candidate = label + " #" + suffix;
        int index = 2;
        while (result.containsKey(candidate)) {
            candidate = label + " #" + suffix + "-" + index++;
        }
        return candidate;
    }

    private static double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = processWorkHours(route);
        Double unitRate = firstNonNullDoubleValue(route.get("hourlyRate"), route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private static double processWorkHours(Map<String, Object> route) {
        return firstNonNullDouble(route.get("unitWorkHours"), route.get("inputVal"));
    }

    private static double firstNonNullDouble(Object primary, Object fallback) {
        Double value = doubleValue(primary);
        if (value != null) {
            return value;
        }
        value = doubleValue(fallback);
        return value == null ? 0.0 : value;
    }

    private static Double firstNonNullDoubleValue(Object primary, Object fallback) {
        Double value = doubleValue(primary);
        return value != null ? value : doubleValue(fallback);
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = safe(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private record ProcessIdentity(String key, String label, String processCode, String categoryName, String detailName) {
    }

    static final class MergedProcess {
        private final String processName;
        private final String processCode;
        private final String categoryName;
        private final String detailName;
        private final String calcType;
        private final double inputVal;
        private final double lineCost;

        private MergedProcess(String processName, String processCode, String categoryName, String detailName,
                              String calcType, double inputVal, double lineCost) {
            this.processName = processName;
            this.processCode = processCode;
            this.categoryName = categoryName;
            this.detailName = detailName;
            this.calcType = calcType;
            this.inputVal = inputVal;
            this.lineCost = lineCost;
        }

        String processName() {
            return processName;
        }

        String processCode() {
            return processCode;
        }

        String categoryName() {
            return categoryName;
        }

        String detailName() {
            return detailName;
        }

        String calcType() {
            return calcType;
        }

        double inputVal() {
            return inputVal;
        }

        Object weightedUnitRate() {
            return inputVal == 0.0 ? "" : lineCost / inputVal;
        }

        double lineCost() {
            return lineCost;
        }
    }

    private static final class ProcessAccumulator {
        private final ProcessIdentity identity;
        private String calcType = "";
        private double inputVal;
        private double lineCost;

        private ProcessAccumulator(ProcessIdentity identity) {
            this.identity = identity;
        }
    }
}
