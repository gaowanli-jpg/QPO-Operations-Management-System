package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.PricingFeeCategory;
import com.machining.quote.domain.PricingScope;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PricingFeeCalculator {
    @Autowired
    private DictPricingMarkupFeeMapper pricingMarkupFeeMapper;

    public PricingResult calculate(double baseAmount, String pricingScope) {
        return calculate(baseAmount, PricingScope.valueOf(pricingScope));
    }

    public PricingResult calculate(double baseAmount, PricingScope pricingScope) {
        double safeBaseAmount = finiteOrZero(baseAmount);
        List<DictPricingMarkupFee> fees = loadEnabledFees(pricingScope);
        List<Map<String, Object>> feeRows = new ArrayList<>();

        CategoryApplication customerWeight = applyCategory(
                pricingScope,
                feeRows,
                safeBaseAmount,
                feesByCategory(fees, PricingFeeCategory.CUSTOMER_WEIGHT),
                true,
                true
        );
        double untaxedAmount = customerWeight.amountAfter;
        double taxIncludedAmount = untaxedAmount;

        CategoryApplication basicPercent = applyCategory(
                pricingScope,
                feeRows,
                untaxedAmount,
                feesByCategory(fees, PricingFeeCategory.BASIC_PERCENT),
                true,
                true
        );
        untaxedAmount = basicPercent.amountAfter;
        taxIncludedAmount = untaxedAmount;

        CategoryApplication tax = applyCategory(
                pricingScope,
                feeRows,
                taxIncludedAmount,
                feesByCategory(fees, PricingFeeCategory.TAX),
                false,
                true
        );
        taxIncludedAmount = tax.amountAfter;

        return new PricingResult(
                safeBaseAmount,
                untaxedAmount,
                taxIncludedAmount,
                feeRows,
                customerWeight.factor,
                basicPercent.factor,
                tax.factor
        );
    }

    private CategoryApplication applyCategory(
            PricingScope pricingScope,
            List<Map<String, Object>> feeRows,
            double categoryBaseAmount,
            List<DictPricingMarkupFee> categoryFees,
            boolean includedInUntaxedPrice,
            boolean includedInTaxIncludedPrice) {
        if (categoryFees.isEmpty()) {
            return new CategoryApplication(categoryBaseAmount, 1.0);
        }

        double categoryRatePercent = categoryFees.stream()
                .mapToDouble(this::ratePercent)
                .sum();
        double categoryFactor = factor(categoryRatePercent);
        double cumulativeRatePercent = 0.0;
        for (DictPricingMarkupFee fee : categoryFees) {
            double rate = ratePercent(fee);
            cumulativeRatePercent += rate;
            double amountAfterFee = categoryBaseAmount * factor(cumulativeRatePercent);
            double cost = categoryBaseAmount * rate / 100.0;
            feeRows.add(feeRow(
                    pricingScope,
                    fee,
                    categoryBaseAmount,
                    cost,
                    amountAfterFee,
                    factor(rate),
                    includedInUntaxedPrice,
                    includedInTaxIncludedPrice
            ));
        }
        return new CategoryApplication(categoryBaseAmount * categoryFactor, categoryFactor);
    }

    private List<DictPricingMarkupFee> loadEnabledFees(PricingScope pricingScope) {
        QueryWrapper<DictPricingMarkupFee> qw = new QueryWrapper<>();
        qw.eq("pricing_scope", pricingScope.name())
                .eq("enabled", true)
                .orderByAsc("sort_no")
                .orderByAsc("id");
        return pricingMarkupFeeMapper.selectList(qw);
    }

    private List<DictPricingMarkupFee> feesByCategory(List<DictPricingMarkupFee> fees, PricingFeeCategory category) {
        return fees.stream()
                .filter(fee -> category.name().equals(fee.getFeeCategory()))
                .toList();
    }

    private Map<String, Object> feeRow(
            PricingScope pricingScope,
            DictPricingMarkupFee fee,
            double baseAmount,
            double cost,
            double amountAfter,
            double factor,
            boolean includedInUntaxedPrice,
            boolean includedInTaxIncludedPrice) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("feeId", fee.getId());
        row.put("feeName", fee.getFeeName());
        row.put("pricingScope", pricingScope.name());
        row.put("feeCategory", fee.getFeeCategory());
        row.put("ratePercent", ratePercent(fee));
        row.put("factor", factor);
        row.put("baseAmount", baseAmount);
        row.put("cost", cost);
        row.put("amountAfter", amountAfter);
        row.put("includedInUntaxedPrice", includedInUntaxedPrice);
        row.put("includedInTaxIncludedPrice", includedInTaxIncludedPrice);
        return row;
    }

    private double ratePercent(DictPricingMarkupFee fee) {
        BigDecimal ratePercent = fee.getRatePercent();
        return ratePercent == null ? 0.0 : ratePercent.doubleValue();
    }

    private double factor(double ratePercent) {
        return 1.0 + ratePercent / 100.0;
    }

    private double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static class CategoryApplication {
        private final double amountAfter;
        private final double factor;

        private CategoryApplication(double amountAfter, double factor) {
            this.amountAfter = amountAfter;
            this.factor = factor;
        }
    }

    public static class PricingResult {
        private final double baseAmount;
        private final double untaxedUnitPrice;
        private final double taxIncludedUnitPrice;
        private final List<Map<String, Object>> feeRows;
        private final double customerWeightFactor;
        private final double basicPercentFactor;
        private final double taxFactor;

        PricingResult(
                double baseAmount,
                double untaxedUnitPrice,
                double taxIncludedUnitPrice,
                List<Map<String, Object>> feeRows,
                double customerWeightFactor,
                double basicPercentFactor,
                double taxFactor) {
            this.baseAmount = baseAmount;
            this.untaxedUnitPrice = untaxedUnitPrice;
            this.taxIncludedUnitPrice = taxIncludedUnitPrice;
            this.feeRows = feeRows;
            this.customerWeightFactor = customerWeightFactor;
            this.basicPercentFactor = basicPercentFactor;
            this.taxFactor = taxFactor;
        }

        public double getBaseAmount() {
            return baseAmount;
        }

        public double getUntaxedUnitPrice() {
            return untaxedUnitPrice;
        }

        public double getTaxIncludedUnitPrice() {
            return taxIncludedUnitPrice;
        }

        public List<Map<String, Object>> getFeeRows() {
            return feeRows;
        }

        public double getCustomerWeightFactor() {
            return customerWeightFactor;
        }

        public double getBasicPercentFactor() {
            return basicPercentFactor;
        }

        public double getTaxFactor() {
            return taxFactor;
        }

        public double getTaxIncludedMarkupCost() {
            return taxIncludedUnitPrice - baseAmount;
        }
    }
}
