package com.machining.quote.service;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.mapper.PricingOptionUsageStatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PricingOptionUsageStatService {
    public static final String OPTION_TYPE_MATERIAL = "MATERIAL";
    public static final String OPTION_TYPE_PROCESS_CATEGORY = "PROCESS_CATEGORY";
    public static final String OPTION_TYPE_EXTRA_FEE = "EXTRA_FEE";

    @Autowired
    private PricingOptionUsageStatMapper usageStatMapper;

    public void recordQuoteOptionUsage(Map<String, Object> materialData, List<QuoteSubmitReq.ProcessSubmitDTO> processRoute) {
        String materialCode = normalizedKey(value(materialData, "materialCode"));
        if (materialCode != null) {
            usageStatMapper.increment(OPTION_TYPE_MATERIAL, materialCode);
        }

        if (processRoute == null || processRoute.isEmpty()) {
            return;
        }
        for (QuoteSubmitReq.ProcessSubmitDTO row : processRoute) {
            recordProcessRow(row);
        }
    }

    public void recordSavedQuoteOptionUsage(Object materialData, Object processRoute) {
        String materialCode = normalizedKey(value(materialData, "materialCode"));
        if (materialCode != null) {
            usageStatMapper.increment(OPTION_TYPE_MATERIAL, materialCode);
        }
        if (!(processRoute instanceof List<?> rows)) {
            return;
        }
        for (Object row : rows) {
            recordSavedProcessRow(row);
        }
    }

    private void recordProcessRow(QuoteSubmitReq.ProcessSubmitDTO row) {
        if (row == null) {
            return;
        }
        if (isExtraFee(row.getSourceType(), row.getCalcType())) {
            increment(OPTION_TYPE_EXTRA_FEE, row.getFeeCode());
        } else {
            increment(OPTION_TYPE_PROCESS_CATEGORY, row.getProcessCode());
        }
    }

    private void recordSavedProcessRow(Object rawRow) {
        if (!(rawRow instanceof Map<?, ?> row)) {
            return;
        }
        String sourceType = value(row, "sourceType");
        String calcType = value(row, "calcType");
        if (isExtraFee(sourceType, calcType)) {
            increment(OPTION_TYPE_EXTRA_FEE, value(row, "feeCode"));
        } else {
            increment(OPTION_TYPE_PROCESS_CATEGORY, value(row, "processCode"));
        }
    }

    private void increment(String optionType, String rawKey) {
        String key = normalizedKey(rawKey);
        if (key != null) {
            usageStatMapper.increment(optionType, key);
        }
    }

    private boolean isExtraFee(String sourceType, String calcType) {
        return "extra".equalsIgnoreCase(trim(sourceType)) || "非工时费用".equals(trim(calcType));
    }

    private static String value(Object source, String key) {
        if (!(source instanceof Map<?, ?> map)) {
            return null;
        }
        return value(map, key);
    }

    private static String value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String normalizedKey(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
