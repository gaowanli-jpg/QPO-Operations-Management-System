package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuoteOptimizedExportCalculator {
    private static final String CUSTOMER_WEIGHT = "CUSTOMER_WEIGHT";
    private static final String TAX = "TAX";
    private static final String ASSEMBLY = "ASSEMBLY";
    private static final String NON_HOURLY_CALC_TYPE = "非工时费用";
    private static final String ASSEMBLY_FEE_PROCESS_NAME = "组装费";
    private static final String FALLBACK_NON_HOURLY_PROCESS_NAME = "其他费用";
    private static final double HOUR_STEP = 0.1;
    private static final double EPSILON = 0.0000001;

    private QuoteOptimizedExportCalculator() {
    }

    static QuoteExportRowData calculate(ItemQuoteVersion item,
                                        Map<String, Object> materialData,
                                        List<Map<String, Object>> processRoutes,
                                        List<Map<String, Object>> feeRows) {
        List<Map<String, Object>> copiedRoutes = copyRows(processRoutes);
        List<Map<String, Object>> visibleFees = nonCustomerWeightFees(feeRows);
        double materialCost = firstNonNullDouble(materialData.get("cost"), 0.0);
        double originalProcessCost = sumProcessCost(copiedRoutes);
        double originalBaseCost = item.getBaseCost() == null ? materialCost + originalProcessCost : item.getBaseCost();
        double originalUnitPrice = item.getTaxIncludedUnitPrice() == null
                ? materialCost + originalProcessCost + sumCost(feeRows)
                : item.getTaxIncludedUnitPrice();
        double originalTotalPrice = item.getTaxIncludedTotalPrice() == null
                ? originalUnitPrice * safeInt(item.getQty())
                : item.getTaxIncludedTotalPrice();

        boolean hasCustomerWeightFee = feeRows.stream().anyMatch(QuoteOptimizedExportCalculator::isCustomerWeightFee);
        if (!hasCustomerWeightFee) {
            return new QuoteExportRowData(
                    copiedRoutes,
                    visibleFees,
                    originalBaseCost,
                    sumCost(visibleFees),
                    originalUnitPrice,
                    originalTotalPrice
            );
        }

        double customerWeightAmount = customerWeightAmount(feeRows, originalBaseCost);
        boolean assembly = ASSEMBLY.equalsIgnoreCase(safe(item.getQuoteMode()));
        if (assembly) {
            return calculateAssembly(item, materialData, copiedRoutes, visibleFees, customerWeightAmount, originalUnitPrice);
        }

        if (customerWeightAmount > EPSILON) {
            if (!allocateToHourlyRoutes(copiedRoutes, customerWeightAmount)) {
                allocateToNonHourlyRoutes(copiedRoutes, customerWeightAmount);
            }
        }
        double adjustedProcessCost = sumProcessCost(copiedRoutes);
        double adjustedBaseCost = materialCost + adjustedProcessCost;
        FeePricing visiblePricing = applyVisibleFees(adjustedBaseCost, visibleFees);
        double unitPrice = visiblePricing.taxIncludedAmount();
        return new QuoteExportRowData(
                copiedRoutes,
                visiblePricing.feeRows(),
                adjustedBaseCost,
                visiblePricing.markupCost(),
                unitPrice,
                unitPrice * safeInt(item.getQty())
        );
    }

    private static QuoteExportRowData calculateAssembly(ItemQuoteVersion item,
                                                        Map<String, Object> materialData,
                                                        List<Map<String, Object>> routes,
                                                        List<Map<String, Object>> visibleFees,
                                                        double customerWeightAmount,
                                                        double originalUnitPrice) {
        double childrenTotal = firstNonNullDouble(materialData.get("childrenTotal"), 0.0);
        double assemblyFee = firstNonNullDouble(materialData.get("assemblyFee"), item.getAssemblyFee());
        if (assemblyFee == 0.0) {
            assemblyFee = assemblyFeeFromRoutes(routes);
        }
        double adjustedAssemblyFee = assemblyFee + Math.max(0.0, customerWeightAmount);
        setAssemblyFeeRoute(routes, adjustedAssemblyFee);
        FeePricing visiblePricing = applyVisibleFees(adjustedAssemblyFee, visibleFees);
        double unitPrice = childrenTotal + visiblePricing.taxIncludedAmount();
        if (visibleFees.isEmpty() && Math.abs(customerWeightAmount) <= EPSILON) {
            unitPrice = originalUnitPrice;
        }
        return new QuoteExportRowData(
                routes,
                visiblePricing.feeRows(),
                childrenTotal + adjustedAssemblyFee,
                visiblePricing.markupCost(),
                unitPrice,
                unitPrice * safeInt(item.getQty())
        );
    }

    static boolean isCustomerWeightFee(Map<String, Object> row) {
        return CUSTOMER_WEIGHT.equalsIgnoreCase(safe(row.get("feeCategory")));
    }

    private static List<Map<String, Object>> nonCustomerWeightFees(List<Map<String, Object>> feeRows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : feeRows) {
            if (!isCustomerWeightFee(row)) {
                result.add(new LinkedHashMap<>(row));
            }
        }
        return result;
    }

    private static double customerWeightAmount(List<Map<String, Object>> feeRows, double baseAmount) {
        double amount = 0.0;
        for (Map<String, Object> row : feeRows) {
            if (!isCustomerWeightFee(row)) {
                continue;
            }
            Double cost = doubleValue(row.get("cost"));
            if (cost != null) {
                amount += cost;
                continue;
            }
            amount += baseAmount * firstNonNullDouble(row.get("ratePercent"), 0.0) / 100.0;
        }
        return amount;
    }

    private static boolean allocateToHourlyRoutes(List<Map<String, Object>> routes, double amount) {
        List<HourlyRoute> hourlyRoutes = new ArrayList<>();
        double totalHourlyCost = 0.0;
        for (int i = 0; i < routes.size(); i++) {
            HourlyRoute route = hourlyRoute(i, routes.get(i));
            if (route != null) {
                hourlyRoutes.add(route);
                totalHourlyCost += route.lineCost();
            }
        }
        if (hourlyRoutes.isEmpty() || totalHourlyCost <= EPSILON) {
            return false;
        }

        int[] units = new int[hourlyRoutes.size()];
        double addedCost = 0.0;
        for (int i = 0; i < hourlyRoutes.size(); i++) {
            HourlyRoute route = hourlyRoutes.get(i);
            double allocatedAmount = amount * route.lineCost() / totalHourlyCost;
            int floorUnits = Math.max(0, (int) Math.floor(allocatedAmount / route.hourlyRate() / HOUR_STEP + EPSILON));
            units[i] = floorUnits;
            addedCost += floorUnits * HOUR_STEP * route.hourlyRate();
        }

        while (true) {
            int bestIndex = -1;
            double currentGap = Math.abs(amount - addedCost);
            double bestGap = currentGap;
            double bestAddedCost = addedCost;
            for (int i = 0; i < hourlyRoutes.size(); i++) {
                double candidateAddedCost = addedCost + HOUR_STEP * hourlyRoutes.get(i).hourlyRate();
                double candidateGap = Math.abs(amount - candidateAddedCost);
                if (candidateGap + EPSILON < bestGap) {
                    bestGap = candidateGap;
                    bestIndex = i;
                    bestAddedCost = candidateAddedCost;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            units[bestIndex] += 1;
            addedCost = bestAddedCost;
        }

        for (int i = 0; i < hourlyRoutes.size(); i++) {
            HourlyRoute route = hourlyRoutes.get(i);
            Map<String, Object> row = routes.get(route.index());
            double adjustedHours = roundOneDecimal(route.hours() + units[i] * HOUR_STEP);
            double adjustedLineCost = adjustedHours * route.hourlyRate();
            row.put("calcType", safeText(row.get("calcType"), "工时"));
            row.put("inputVal", adjustedHours);
            if (row.containsKey("unitWorkHours")) {
                row.put("unitWorkHours", adjustedHours);
            }
            row.put("unitRate", route.hourlyRate());
            if (row.containsKey("hourlyRate")) {
                row.put("hourlyRate", route.hourlyRate());
            }
            row.put("lineCost", adjustedLineCost);
        }
        return true;
    }

    private static HourlyRoute hourlyRoute(int index, Map<String, Object> row) {
        if (isNonHourly(row)) {
            return null;
        }
        double hours = firstNonNullDouble(row.get("unitWorkHours"), row.get("inputVal"));
        Double hourlyRate = firstNonNullDoubleValue(row.get("hourlyRate"), row.get("unitRate"));
        double lineCost = processLineCost(row);
        if ((hourlyRate == null || hourlyRate <= EPSILON) && hours > EPSILON && lineCost > EPSILON) {
            hourlyRate = lineCost / hours;
        }
        if (hours <= EPSILON || hourlyRate == null || hourlyRate <= EPSILON) {
            return null;
        }
        return new HourlyRoute(index, hours, hourlyRate, lineCost);
    }

    private static void allocateToNonHourlyRoutes(List<Map<String, Object>> routes, double amount) {
        List<Integer> nonHourlyIndexes = new ArrayList<>();
        double totalCost = 0.0;
        for (int i = 0; i < routes.size(); i++) {
            Map<String, Object> route = routes.get(i);
            if (isNonHourly(route)) {
                nonHourlyIndexes.add(i);
                totalCost += Math.max(0.0, processLineCost(route));
            }
        }
        if (nonHourlyIndexes.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("processName", FALLBACK_NON_HOURLY_PROCESS_NAME);
            fallback.put("calcType", NON_HOURLY_CALC_TYPE);
            fallback.put("sourceType", "extra");
            fallback.put("sortNo", 999);
            fallback.put("inputVal", amount);
            fallback.put("lineCost", amount);
            routes.add(fallback);
            return;
        }
        double applied = 0.0;
        for (int i = 0; i < nonHourlyIndexes.size(); i++) {
            Map<String, Object> route = routes.get(nonHourlyIndexes.get(i));
            double share = i == nonHourlyIndexes.size() - 1
                    ? amount - applied
                    : amount * (totalCost <= EPSILON ? 1.0 / nonHourlyIndexes.size() : Math.max(0.0, processLineCost(route)) / totalCost);
            applied += share;
            addToNonHourlyRoute(route, share);
        }
    }

    private static void setAssemblyFeeRoute(List<Map<String, Object>> routes, double adjustedAssemblyFee) {
        Map<String, Object> target = null;
        for (Map<String, Object> route : routes) {
            if (ASSEMBLY_FEE_PROCESS_NAME.equals(safe(route.get("processName")))) {
                target = route;
                break;
            }
        }
        if (target == null) {
            for (Map<String, Object> route : routes) {
                if (isNonHourly(route)) {
                    target = route;
                    break;
                }
            }
        }
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("processName", ASSEMBLY_FEE_PROCESS_NAME);
            target.put("calcType", NON_HOURLY_CALC_TYPE);
            target.put("sourceType", "extra");
            target.put("sortNo", 10);
            routes.add(target);
        }
        target.put("processName", ASSEMBLY_FEE_PROCESS_NAME);
        target.put("calcType", NON_HOURLY_CALC_TYPE);
        target.put("sourceType", "extra");
        target.put("inputVal", adjustedAssemblyFee);
        target.put("lineCost", adjustedAssemblyFee);
    }

    private static double assemblyFeeFromRoutes(List<Map<String, Object>> routes) {
        for (Map<String, Object> route : routes) {
            if (ASSEMBLY_FEE_PROCESS_NAME.equals(safe(route.get("processName")))) {
                return processLineCost(route);
            }
        }
        return 0.0;
    }

    private static void addToNonHourlyRoute(Map<String, Object> route, double amount) {
        double inputVal = firstNonNullDouble(route.get("inputVal"), route.get("lineCost"));
        double lineCost = processLineCost(route);
        route.put("calcType", NON_HOURLY_CALC_TYPE);
        route.put("inputVal", inputVal + amount);
        route.put("lineCost", lineCost + amount);
    }

    private static FeePricing applyVisibleFees(double baseAmount, List<Map<String, Object>> visibleFees) {
        List<Map<String, Object>> result = new ArrayList<>();
        double untaxedAmount = applyFeeCategory(baseAmount, visibleFees, false, result, true, true);
        double taxIncludedAmount = applyFeeCategory(untaxedAmount, visibleFees, true, result, false, true);
        return new FeePricing(result, untaxedAmount, taxIncludedAmount, sumCost(result));
    }

    private static double applyFeeCategory(double baseAmount,
                                           List<Map<String, Object>> feeRows,
                                           boolean taxCategory,
                                           List<Map<String, Object>> result,
                                           boolean includedInUntaxedPrice,
                                           boolean includedInTaxIncludedPrice) {
        double categoryRate = 0.0;
        for (Map<String, Object> fee : feeRows) {
            if (isTaxFee(fee) == taxCategory) {
                double rate = firstNonNullDouble(fee.get("ratePercent"), 0.0);
                categoryRate += rate;
                Map<String, Object> row = new LinkedHashMap<>(fee);
                double cost = baseAmount * rate / 100.0;
                row.put("cost", cost);
                row.put("baseAmount", baseAmount);
                row.put("factor", 1.0 + rate / 100.0);
                row.put("amountAfter", baseAmount * (1.0 + categoryRate / 100.0));
                row.put("includedInUntaxedPrice", includedInUntaxedPrice);
                row.put("includedInTaxIncludedPrice", includedInTaxIncludedPrice);
                result.add(row);
            }
        }
        return baseAmount * (1.0 + categoryRate / 100.0);
    }

    private static boolean isTaxFee(Map<String, Object> row) {
        return TAX.equalsIgnoreCase(safe(row.get("feeCategory")));
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new LinkedHashMap<>(row));
        }
        return result;
    }

    private static double sumProcessCost(List<Map<String, Object>> routes) {
        double total = 0.0;
        for (Map<String, Object> route : routes) {
            total += processLineCost(route);
        }
        return total;
    }

    private static double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = firstNonNullDouble(route.get("unitWorkHours"), route.get("inputVal"));
        Double unitRate = firstNonNullDoubleValue(route.get("hourlyRate"), route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private static boolean isNonHourly(Map<String, Object> route) {
        String calcType = safe(route.get("calcType")).trim();
        String sourceType = safe(route.get("sourceType")).trim();
        return NON_HOURLY_CALC_TYPE.equals(calcType) || "extra".equalsIgnoreCase(sourceType);
    }

    private static double sumCost(List<Map<String, Object>> rows) {
        double total = 0.0;
        for (Map<String, Object> row : rows) {
            total += firstNonNullDouble(row.get("cost"), 0.0);
        }
        return total;
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

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String safeText(Object value, String fallback) {
        String text = safe(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record HourlyRoute(int index, double hours, double hourlyRate, double lineCost) {
    }

    private record FeePricing(List<Map<String, Object>> feeRows,
                              double untaxedAmount,
                              double taxIncludedAmount,
                              double markupCost) {
    }

    static final class QuoteExportRowData {
        private final List<Map<String, Object>> processRoutes;
        private final List<Map<String, Object>> feeRows;
        private final double baseCost;
        private final double markupCost;
        private final double taxIncludedUnitPrice;
        private final double taxIncludedTotalPrice;

        private QuoteExportRowData(List<Map<String, Object>> processRoutes,
                                   List<Map<String, Object>> feeRows,
                                   double baseCost,
                                   double markupCost,
                                   double taxIncludedUnitPrice,
                                   double taxIncludedTotalPrice) {
            this.processRoutes = processRoutes;
            this.feeRows = feeRows;
            this.baseCost = baseCost;
            this.markupCost = markupCost;
            this.taxIncludedUnitPrice = taxIncludedUnitPrice;
            this.taxIncludedTotalPrice = taxIncludedTotalPrice;
        }

        List<Map<String, Object>> processRoutes() {
            return processRoutes;
        }

        List<Map<String, Object>> feeRows() {
            return feeRows;
        }

        double baseCost() {
            return baseCost;
        }

        double markupCost() {
            return markupCost;
        }

        double taxIncludedUnitPrice() {
            return taxIncludedUnitPrice;
        }

        double taxIncludedTotalPrice() {
            return taxIncludedTotalPrice;
        }
    }
}
