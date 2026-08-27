package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.PricingFeeCategory;
import com.machining.quote.domain.PricingScope;
import com.machining.quote.dto.PricingMarkupFeeSaveReq;
import com.machining.quote.entity.DictPricingMarkupFee;
import com.machining.quote.mapper.DictPricingMarkupFeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PricingMarkupFeeService {
    private static final String TARGET_TYPE = "dict_pricing_markup_fee";

    @Autowired
    private DictPricingMarkupFeeMapper pricingMarkupFeeMapper;
    @Autowired
    private AuditService auditService;

    public java.util.List<DictPricingMarkupFee> listFees(String pricingScope, String feeCategory) {
        PricingScope scope = parseScope(pricingScope);
        PricingFeeCategory category = parseCategory(feeCategory);

        QueryWrapper<DictPricingMarkupFee> qw = new QueryWrapper<>();
        qw.eq("pricing_scope", scope.name())
                .eq("fee_category", category.name())
                .orderByAsc("sort_no")
                .orderByAsc("id");
        return pricingMarkupFeeMapper.selectList(qw);
    }

    @Transactional
    public DictPricingMarkupFee saveFee(Long id, PricingMarkupFeeSaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("pricing markup fee request is required");
        }
        DictPricingMarkupFee before = id == null ? null : requireFee(id);
        DictPricingMarkupFee entity = id == null ? normalizeCreate(req) : normalizeUpdate(before, req);
        entity.setId(id);

        if (id == null) {
            pricingMarkupFeeMapper.insert(entity);
            auditService.record(
                    "DICT_PRICING_MARKUP_FEE_CREATE",
                    TARGET_TYPE,
                    entity.getId(),
                    null,
                    auditSummary(entity)
            );
        } else {
            pricingMarkupFeeMapper.updateById(entity);
            auditService.record(
                    "DICT_PRICING_MARKUP_FEE_UPDATE",
                    TARGET_TYPE,
                    id,
                    auditSummary(before),
                    auditSummary(entity)
            );
        }
        return entity;
    }

    @Transactional
    public void deleteFee(Long id) {
        DictPricingMarkupFee before = requireFee(id);
        if (Boolean.TRUE.equals(before.getSystemRequired())) {
            throw new IllegalArgumentException("system item cannot be deleted");
        }
        pricingMarkupFeeMapper.deleteById(id);
        auditService.record(
                "DICT_PRICING_MARKUP_FEE_DELETE",
                TARGET_TYPE,
                id,
                auditSummary(before),
                null
        );
    }

    private DictPricingMarkupFee normalizeCreate(PricingMarkupFeeSaveReq req) {
        PricingScope scope = parseScope(req.getPricingScope());
        PricingFeeCategory category = parseCategory(req.getFeeCategory());
        rejectManualSystemCategory(category);

        DictPricingMarkupFee entity = new DictPricingMarkupFee();
        entity.setPricingScope(scope.name());
        entity.setFeeCategory(category.name());
        entity.setFeeName(requiredName(req.getFeeName()));
        entity.setRatePercent(nonNegativeRate(req.getRatePercent()));
        entity.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        entity.setEnabled(req.getEnabled() == null || req.getEnabled());
        entity.setSystemRequired(false);
        return entity;
    }

    private DictPricingMarkupFee normalizeUpdate(DictPricingMarkupFee before, PricingMarkupFeeSaveReq req) {
        BigDecimal ratePercent = req.getRatePercent() == null ? before.getRatePercent() : nonNegativeRate(req.getRatePercent());
        Boolean enabled = req.getEnabled() == null ? before.getEnabled() : req.getEnabled();

        DictPricingMarkupFee entity = new DictPricingMarkupFee();
        entity.setPricingScope(before.getPricingScope());
        entity.setFeeCategory(before.getFeeCategory());
        entity.setSystemRequired(Boolean.TRUE.equals(before.getSystemRequired()));
        entity.setRatePercent(ratePercent);
        entity.setEnabled(enabled);

        if (Boolean.TRUE.equals(before.getSystemRequired())) {
            entity.setFeeName(before.getFeeName());
            entity.setSortNo(before.getSortNo());
        } else {
            PricingFeeCategory requestedCategory = isBlank(req.getFeeCategory())
                    ? PricingFeeCategory.valueOf(before.getFeeCategory())
                    : parseCategory(req.getFeeCategory());
            rejectManualSystemCategory(requestedCategory);
            entity.setFeeName(requiredName(req.getFeeName()));
            entity.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        }
        return entity;
    }

    private DictPricingMarkupFee requireFee(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("pricing markup fee id is required");
        }
        DictPricingMarkupFee fee = pricingMarkupFeeMapper.selectById(id);
        if (fee == null) {
            throw new IllegalArgumentException("pricing markup fee does not exist");
        }
        return fee;
    }

    private PricingScope parseScope(String value) {
        String normalized = required(value, "pricingScope").toUpperCase(Locale.ROOT);
        try {
            return PricingScope.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("pricingScope must be NORMAL or ASSEMBLY");
        }
    }

    private PricingFeeCategory parseCategory(String value) {
        String normalized = required(value, "feeCategory").toUpperCase(Locale.ROOT);
        try {
            return PricingFeeCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("feeCategory must be BASIC_PERCENT, TAX, or CUSTOMER_WEIGHT");
        }
    }

    private void rejectManualSystemCategory(PricingFeeCategory category) {
        if (category == PricingFeeCategory.TAX || category == PricingFeeCategory.CUSTOMER_WEIGHT) {
            throw new IllegalArgumentException("system fee category can only be edited from its default row");
        }
    }

    private String requiredName(String value) {
        String trimmed = required(value, "feeName");
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("feeName length must be less than or equal to 100");
        }
        return trimmed;
    }

    private String required(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private BigDecimal nonNegativeRate(BigDecimal value) {
        BigDecimal rate = value == null ? BigDecimal.ZERO : value;
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("ratePercent must be greater than or equal to 0");
        }
        return rate;
    }

    private Map<String, Object> auditSummary(DictPricingMarkupFee entity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", entity.getId());
        summary.put("pricingScope", entity.getPricingScope());
        summary.put("feeCategory", entity.getFeeCategory());
        summary.put("feeName", entity.getFeeName());
        summary.put("ratePercent", entity.getRatePercent());
        summary.put("sortNo", entity.getSortNo());
        summary.put("enabled", entity.getEnabled());
        summary.put("systemRequired", entity.getSystemRequired());
        return summary;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
