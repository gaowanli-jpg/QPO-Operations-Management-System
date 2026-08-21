package com.machining.quote.service;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProcessRoutePricingService {
    private static final String ROUTE_TYPE_PROCESS = "PROCESS";
    private static final String SOURCE_TYPE_PROCESS = "process";
    private static final String CALC_TYPE_WORK_HOURS = "工时";

    @Autowired
    private DictProcessDetailMapper detailMapper;
    @Autowired
    private DictProcessCategoryMapper categoryMapper;

    public List<Map<String, Object>> priceProcessRows(List<QuoteSubmitReq.ProcessSubmitDTO> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (QuoteSubmitReq.ProcessSubmitDTO row : rows) {
            if (row == null) {
                continue;
            }
            Double unitWorkHours = unitWorkHours(row);
            if (unitWorkHours == null || unitWorkHours <= 0) {
                continue;
            }
            Long processDetailId = row.getProcessDetailId();
            if (processDetailId == null) {
                throw new IllegalArgumentException("请选择具体工艺后再保存核价");
            }
            DictProcessDetail detail = detailMapper.selectById(processDetailId);
            if (detail == null) {
                throw new IllegalArgumentException("具体工艺不存在");
            }
            if (!Boolean.TRUE.equals(detail.getEnabled())) {
                throw new IllegalStateException("具体工艺已停用，请重新选择");
            }
            DictProcessCategory category = categoryMapper.selectById(detail.getCategoryId());
            if (category == null) {
                throw new IllegalArgumentException("工艺大类不存在");
            }
            if (!Boolean.TRUE.equals(category.getEnabled())) {
                throw new IllegalStateException("工艺大类已停用，请重新选择");
            }
            BigDecimal effectiveHourlyRate = effectiveHourlyRate(detail, category);
            double hourlyRate = number(effectiveHourlyRate);
            double lineCost = unitWorkHours * hourlyRate;

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("routeType", ROUTE_TYPE_PROCESS);
            snapshot.put("sourceType", SOURCE_TYPE_PROCESS);
            snapshot.put("processCategoryId", category.getId());
            snapshot.put("processDetailId", detail.getId());
            snapshot.put("processCode", category.getProcessCode());
            snapshot.put("categoryName", category.getCategoryName());
            snapshot.put("detailName", detail.getDetailName());
            snapshot.put("processName", detail.getDetailName());
            snapshot.put("calcType", CALC_TYPE_WORK_HOURS);
            snapshot.put("hourlyRate", hourlyRate);
            snapshot.put("unitWorkHours", unitWorkHours);
            snapshot.put("inputVal", unitWorkHours);
            snapshot.put("unitRate", hourlyRate);
            snapshot.put("detailHourlyRate", detail.getHourlyRate());
            snapshot.put("categoryHourlyRate", number(category.getHourlyRate()));
            snapshot.put("hourlyRateSource", detail.getHourlyRate() == null ? "CATEGORY" : "DETAIL");
            snapshot.put("lineCost", lineCost);
            snapshot.put("sortNo", row.getSortNo());
            if (category.getRemarks() != null && !category.getRemarks().isBlank()) {
                snapshot.put("categoryRemarks", category.getRemarks());
            }
            if (detail.getDetailRemarks() != null && !detail.getDetailRemarks().isBlank()) {
                snapshot.put("detailRemarks", detail.getDetailRemarks());
            }
            result.add(snapshot);
        }
        return result;
    }

    private Double unitWorkHours(QuoteSubmitReq.ProcessSubmitDTO row) {
        return row.getUnitWorkHours() != null ? row.getUnitWorkHours() : row.getInputVal();
    }

    private BigDecimal effectiveHourlyRate(DictProcessDetail detail, DictProcessCategory category) {
        if (detail.getHourlyRate() != null) {
            return detail.getHourlyRate();
        }
        return Objects.requireNonNullElse(category.getHourlyRate(), BigDecimal.ZERO);
    }

    private double number(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
