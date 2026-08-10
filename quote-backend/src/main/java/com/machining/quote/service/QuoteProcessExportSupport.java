package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class QuoteProcessExportSupport {
    static final List<String> QUOTE_PROCESS_VALUE_HEADERS = List.of("类型", "工时/金额", "单价", "费用");
    static final List<String> PROCESS_SHEET_VALUE_HEADERS = List.of("大类编码", "大类名称", "具体工艺", "工时", "小时价格", "金额");

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
            accumulateRoute(accumulators, route, false);
        }
        for (Map<String, Object> route : routes) {
            accumulateRoute(accumulators, route, true);
        }

        Map<String, MergedProcess> result = new LinkedHashMap<>();
        for (ProcessAccumulator accumulator : accumulators.values()) {
            result.put(accumulator.identity.label(), new MergedProcess(
                    accumulator.identity.label(),
                    accumulator.identity.processCode,
                    accumulator.identity.categoryName,
                    accumulator.identity.detailName,
                    accumulator.calcType,
                    accumulator.inputVal,
                    accumulator.lineCost,
                    accumulator.identity.nonHourly
            ));
        }
        return result;
    }

    private static void accumulateRoute(Map<String, ProcessAccumulator> accumulators, Map<String, Object> route, boolean nonHourlyPass) {
        boolean nonHourly = isNonHourly(route);
        if (nonHourly != nonHourlyPass) {
            return;
        }
        ProcessIdentity identity = processIdentity(route);
        if (identity.label().isEmpty()) {
            return;
        }
        ProcessAccumulator accumulator = accumulators.computeIfAbsent(identity.key(), ignored -> new ProcessAccumulator(identity));
        String calcType = safe(route.get("calcType")).trim();
        if (accumulator.calcType.isEmpty() && !calcType.isEmpty()) {
            accumulator.calcType = calcType;
        }
        if (accumulator.calcType.isEmpty()) {
            accumulator.calcType = nonHourly ? "非工时费用" : "工时";
        }
        accumulator.inputVal += processInputValue(route, nonHourly);
        accumulator.lineCost += processLineCost(route);
    }

    private static ProcessIdentity processIdentity(Map<String, Object> route) {
        boolean nonHourly = isNonHourly(route);
        String processCode = safe(route.get("processCode")).trim();
        String categoryName = safe(route.get("categoryName")).trim();
        String detailName = firstText(route.get("detailName"), route.get("processName"), route.get("name"));
        String label = nonHourly ? firstText(detailName, categoryName, processCode) : firstText(categoryName, detailName, processCode);
        String key;
        if (nonHourly) {
            key = "non-hourly:" + label;
        } else if (!processCode.isEmpty()) {
            key = "hourly:" + processCode;
        } else {
            key = "hourly-legacy:" + label;
        }
        return new ProcessIdentity(key, label, processCode, categoryName, detailName, nonHourly);
    }

    private static double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = processInputValue(route, isNonHourly(route));
        Double unitRate = firstNonNullDoubleValue(route.get("hourlyRate"), route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private static double processInputValue(Map<String, Object> route, boolean nonHourly) {
        if (nonHourly) {
            return firstNonNullDouble(route.get("inputVal"), route.get("lineCost"));
        }
        return firstNonNullDouble(route.get("unitWorkHours"), route.get("inputVal"));
    }

    private static boolean isNonHourly(Map<String, Object> route) {
        String calcType = safe(route.get("calcType")).trim();
        String sourceType = safe(route.get("sourceType")).trim();
        return "非工时费用".equals(calcType) || "extra".equalsIgnoreCase(sourceType);
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

    private record ProcessIdentity(String key, String label, String processCode, String categoryName, String detailName,
                                   boolean nonHourly) {
    }

    static final class MergedProcess {
        private final String processName;
        private final String processCode;
        private final String categoryName;
        private final String detailName;
        private final String calcType;
        private final double inputVal;
        private final double lineCost;
        private final boolean nonHourly;

        private MergedProcess(String processName, String processCode, String categoryName, String detailName,
                              String calcType, double inputVal, double lineCost, boolean nonHourly) {
            this.processName = processName;
            this.processCode = processCode;
            this.categoryName = categoryName;
            this.detailName = detailName;
            this.calcType = calcType;
            this.inputVal = inputVal;
            this.lineCost = lineCost;
            this.nonHourly = nonHourly;
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
            if (nonHourly) {
                return "";
            }
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
