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

        double untaxedAmount = safeBaseAmount;
        double taxIncludedAmount = safeBaseAmount;
        double customerWeightFactor = 1.0;
        double basicPercentFactor = 1.0;
        double taxFactor = 1.0;

        for (DictPricingMarkupFee fee : feesByCategory(fees, PricingFeeCategory.CUSTOMER_WEIGHT)) {
            double before = untaxedAmount;
            double rate = ratePercent(fee);
            double factor = factor(rate);
            untaxedAmount *= factor;
            taxIncludedAmount = untaxedAmount;
            customerWeightFactor *= factor;
            feeRows.add(feeRow(pricingScope, fee, before, untaxedAmount, factor, true, true));
        }

        for (DictPricingMarkupFee fee : feesByCategory(fees, PricingFeeCategory.BASIC_PERCENT)) {
            double before = untaxedAmount;
            double rate = ratePercent(fee);
            double factor = factor(rate);
            untaxedAmount *= factor;
            taxIncludedAmount = untaxedAmount;
            basicPercentFactor *= factor;
            feeRows.add(feeRow(pricingScope, fee, before, untaxedAmount, factor, true, true));
        }

        for (DictPricingMarkupFee fee : feesByCategory(fees, PricingFeeCategory.TAX)) {
            double before = taxIncludedAmount;
            double rate = ratePercent(fee);
            double factor = factor(rate);
            taxIncludedAmount *= factor;
            taxFactor *= factor;
            feeRows.add(feeRow(pricingScope, fee, before, taxIncludedAmount, factor, false, true));
        }

        return new PricingResult(
                safeBaseAmount,
                untaxedAmount,
                taxIncludedAmount,
                feeRows,
                customerWeightFactor,
                basicPercentFactor,
                taxFactor
        );
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
        row.put("cost", amountAfter - baseAmount);
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
