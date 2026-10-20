package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteOptimizedExportCalculatorTest {
    @Test
    void hidesCustomerWeightAndAllocatesItIntoRoundedHourlyWorkHours() {
        ItemQuoteVersion item = quotedItem("NORMAL", 1);
        item.setBaseCost(65.0);
        item.setUntaxedUnitPrice(85.8);
        item.setTaxIncludedUnitPrice(96.954);

        QuoteOptimizedExportCalculator.QuoteExportRowData result = QuoteOptimizedExportCalculator.calculate(
                item,
                Map.of("cost", 25.0, "weight", 1.0),
                List.of(
                        route("下料", "工时", 1.0, 10.0, 10.0, 10),
                        route("CNC", "工时", 2.0, 15.0, 30.0, 20)
                ),
                List.of(
                        fee("客户权重", "CUSTOMER_WEIGHT", 10.0, 6.5),
                        fee("运输", "BASIC_PERCENT", 5.0, 3.575),
                        fee("利润", "BASIC_PERCENT", 15.0, 10.725),
                        fee("税", "TAX", 13.0, 10.439)
                )
        );

        assertThat(result.feeRows())
                .extracting(row -> row.get("feeCategory"))
                .containsExactly("BASIC_PERCENT", "BASIC_PERCENT", "TAX");
        assertThat(result.processRoutes().get(0))
                .containsEntry("inputVal", 1.2)
                .containsEntry("unitWorkHours", 1.2)
                .containsEntry("lineCost", 12.0);
        assertThat(result.processRoutes().get(1))
                .containsEntry("inputVal", 2.3)
                .containsEntry("unitWorkHours", 2.3)
                .containsEntry("lineCost", 34.5);
        assertThat(result.baseCost()).isEqualTo(71.5);
        assertThat(result.markupCost()).isEqualTo(25.454);
        assertThat(result.taxIncludedUnitPrice()).isEqualTo(96.954);
    }

    @Test
    void customerWeightIsDetectedByCategoryNotByName() {
        ItemQuoteVersion item = quotedItem("NORMAL", 1);
        item.setBaseCost(100.0);
        item.setTaxIncludedUnitPrice(100.0);

        QuoteOptimizedExportCalculator.QuoteExportRowData result = QuoteOptimizedExportCalculator.calculate(
                item,
                Map.of("cost", 100.0),
                List.of(),
                List.of(
                        fee("客户权重样式但不是客户权重", "BASIC_PERCENT", 5.0, 5.0),
                        fee("真实权重但名称不含权重", "CUSTOMER_WEIGHT", 10.0, 10.0)
                )
        );

        assertThat(result.feeRows())
                .extracting(row -> row.get("feeName"))
                .containsExactly("客户权重样式但不是客户权重");
        assertThat(result.taxIncludedUnitPrice()).isEqualTo(115.5);
    }

    @Test
    void fallsBackToNonHourlyFeeWhenNoHourlyProcessIsAvailable() {
        ItemQuoteVersion item = quotedItem("NORMAL", 1);
        item.setBaseCost(30.0);
        item.setTaxIncludedUnitPrice(39.1545);

        QuoteOptimizedExportCalculator.QuoteExportRowData result = QuoteOptimizedExportCalculator.calculate(
                item,
                Map.of("cost", 20.0),
                List.of(nonHourlyRoute("包装费", 10.0, 10.0, 10)),
                List.of(
                        fee("客户权重", "CUSTOMER_WEIGHT", 10.0, 3.0),
                        fee("运输", "BASIC_PERCENT", 5.0, 1.65),
                        fee("税", "TAX", 13.0, 4.5045)
                )
        );

        assertThat(result.processRoutes().get(0))
                .containsEntry("inputVal", 13.0)
                .containsEntry("lineCost", 13.0);
        assertThat(result.baseCost()).isEqualTo(33.0);
        assertThat(result.taxIncludedUnitPrice()).isCloseTo(39.1545, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void assemblyCustomerWeightIsAddedIntoAssemblyFeeOnly() {
        ItemQuoteVersion item = quotedItem("ASSEMBLY", 1);
        item.setAssemblyFee(50.0);
        item.setBaseCost(250.0);
        item.setTaxIncludedUnitPrice(265.2575);

        QuoteOptimizedExportCalculator.QuoteExportRowData result = QuoteOptimizedExportCalculator.calculate(
                item,
                Map.of("childrenTotal", 200.0, "assemblyFee", 50.0, "cost", 0.0),
                List.of(nonHourlyRoute("组装费", 50.0, 50.0, 10)),
                List.of(
                        fee("客户权重", "CUSTOMER_WEIGHT", 10.0, 5.0),
                        fee("利润", "BASIC_PERCENT", 5.0, 2.75),
                        fee("税", "TAX", 13.0, 7.5075)
                )
        );

        assertThat(result.processRoutes().get(0))
                .containsEntry("processName", "组装费")
                .containsEntry("inputVal", 55.0)
                .containsEntry("lineCost", 55.0);
        assertThat(result.baseCost()).isEqualTo(255.0);
        assertThat(result.taxIncludedUnitPrice()).isEqualTo(265.2575);
    }

    private ItemQuoteVersion quotedItem(String quoteMode, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setQuoteMode(quoteMode);
        item.setQty(qty);
        return item;
    }

    private Map<String, Object> route(String processName, String calcType, double inputVal, double unitRate, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processName", processName);
        route.put("calcType", calcType);
        route.put("inputVal", inputVal);
        route.put("unitRate", unitRate);
        route.put("unitWorkHours", inputVal);
        route.put("hourlyRate", unitRate);
        route.put("lineCost", lineCost);
        route.put("sortNo", sortNo);
        return route;
    }

    private Map<String, Object> nonHourlyRoute(String processName, double inputVal, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processName", processName);
        route.put("calcType", "非工时费用");
        route.put("sourceType", "extra");
        route.put("inputVal", inputVal);
        route.put("lineCost", lineCost);
        route.put("sortNo", sortNo);
        return route;
    }

    private Map<String, Object> fee(String feeName, String feeCategory, double ratePercent, double cost) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", feeName);
        fee.put("feeCategory", feeCategory);
        fee.put("ratePercent", ratePercent);
        fee.put("cost", cost);
        return fee;
    }
}
