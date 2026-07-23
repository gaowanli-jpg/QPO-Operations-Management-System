package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class QuoteProcessExportSupport {
    static final List<String> PROCESS_VALUE_HEADERS = List.of("类型", "工时/金额", "单价", "费用");

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
            String processName = safe(route.get("processName")).trim();
            if (processName.isEmpty()) {
                continue;
            }
            ProcessAccumulator accumulator = accumulators.computeIfAbsent(processName, ProcessAccumulator::new);
            String calcType = safe(route.get("calcType")).trim();
            if (accumulator.calcType.isEmpty() && !calcType.isEmpty()) {
                accumulator.calcType = calcType;
            }
            accumulator.inputVal += firstNonNullDouble(route.get("inputVal"), 0.0);
            accumulator.lineCost += processLineCost(route);
        }

        Map<String, MergedProcess> result = new LinkedHashMap<>();
        for (ProcessAccumulator accumulator : accumulators.values()) {
            result.put(accumulator.processName, new MergedProcess(
                    accumulator.processName,
                    accumulator.calcType,
                    accumulator.inputVal,
                    accumulator.lineCost
            ));
        }
        return result;
    }

    private static double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = firstNonNullDouble(route.get("inputVal"), 0.0);
        Double unitRate = doubleValue(route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private static double firstNonNullDouble(Object primary, Object fallback) {
        Double value = doubleValue(primary);
        if (value != null) {
            return value;
        }
        value = doubleValue(fallback);
        return value == null ? 0.0 : value;
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

    static final class MergedProcess {
        private final String processName;
        private final String calcType;
        private final double inputVal;
        private final double lineCost;

        private MergedProcess(String processName, String calcType, double inputVal, double lineCost) {
            this.processName = processName;
            this.calcType = calcType;
            this.inputVal = inputVal;
            this.lineCost = lineCost;
        }

        String processName() {
            return processName;
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
        private final String processName;
        private String calcType = "";
        private double inputVal;
        private double lineCost;

        private ProcessAccumulator(String processName) {
            this.processName = processName;
        }
    }
}
