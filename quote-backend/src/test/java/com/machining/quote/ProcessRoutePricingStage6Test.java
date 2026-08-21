package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.service.ProcessRoutePricingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessRoutePricingStage6Test {

    @Test
    void pricesProcessRowsFromEffectiveHourlyRateAndIgnoresSubmittedPriceSnapshots() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessRoutePricingService service = service(detailMapper, categoryMapper);

        DictProcessDetail inheritedDetail = detail(7L, 3L, "Precision packaging", null);
        DictProcessCategory packaging = category(3L, "PACKAGING", "Packaging", "10.00");
        DictProcessDetail zeroDetail = detail(8L, 4L, "Free setup", BigDecimal.ZERO);
        DictProcessCategory cnc = category(4L, "CNC", "CNC", "80.00");
        DictProcessDetail customDetail = detail(9L, 5L, "Fine milling", new BigDecimal("125.50"));
        DictProcessCategory milling = category(5L, "MILL", "Milling", "80.00");
        when(detailMapper.selectById(7L)).thenReturn(inheritedDetail);
        when(categoryMapper.selectById(3L)).thenReturn(packaging);
        when(detailMapper.selectById(8L)).thenReturn(zeroDetail);
        when(categoryMapper.selectById(4L)).thenReturn(cnc);
        when(detailMapper.selectById(9L)).thenReturn(customDetail);
        when(categoryMapper.selectById(5L)).thenReturn(milling);

        List<Map<String, Object>> priced = service.priceProcessRows(List.of(
                row(20, 7L, 2.5),
                row(30, 8L, 1.5),
                row(40, 9L, 2.0)
        ));

        assertThat(priced).hasSize(3);
        assertThat(priced.get(0)).satisfies(route -> {
            assertThat(route).containsEntry("routeType", "PROCESS");
            assertThat(route).containsEntry("processDetailId", 7L);
            assertThat(route).containsEntry("processCategoryId", 3L);
            assertThat(route).containsEntry("processCode", "PACKAGING");
            assertThat(route).containsEntry("categoryName", "Packaging");
            assertThat(route).containsEntry("detailName", "Precision packaging");
            assertThat(route).containsEntry("detailHourlyRate", null);
            assertThat(route).containsEntry("categoryHourlyRate", 10.0);
            assertThat(route).containsEntry("hourlyRateSource", "CATEGORY");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(10.0);
            assertThat((Double) route.get("unitRate")).isEqualTo(10.0);
            assertThat((Double) route.get("unitWorkHours")).isEqualTo(2.5);
            assertThat((Double) route.get("lineCost")).isEqualTo(25.0);
        });
        assertThat(priced.get(1)).satisfies(route -> {
            assertThat(route).containsEntry("processDetailId", 8L);
            assertThat(route).containsEntry("categoryHourlyRate", 80.0);
            assertThat(route).containsEntry("hourlyRateSource", "DETAIL");
            assertThat((BigDecimal) route.get("detailHourlyRate")).isEqualByComparingTo("0.00");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(0.0);
            assertThat((Double) route.get("unitRate")).isEqualTo(0.0);
            assertThat((Double) route.get("lineCost")).isEqualTo(0.0);
        });
        assertThat(priced.get(2)).satisfies(route -> {
            assertThat(route).containsEntry("processDetailId", 9L);
            assertThat(route).containsEntry("categoryHourlyRate", 80.0);
            assertThat(route).containsEntry("hourlyRateSource", "DETAIL");
            assertThat((BigDecimal) route.get("detailHourlyRate")).isEqualByComparingTo("125.50");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(125.5);
            assertThat((Double) route.get("unitRate")).isEqualTo(125.5);
            assertThat((Double) route.get("lineCost")).isEqualTo(251.0);
        });
    }

    @Test
    void quantityDoesNotChangeProcessHourlyRate() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessRoutePricingService service = service(detailMapper, categoryMapper);

        DictProcessDetail detail = detail(8L, 4L, "CNC finishing", null);
        DictProcessCategory category = category(4L, "CNC", "CNC", "80.00");
        when(detailMapper.selectById(8L)).thenReturn(detail);
        when(categoryMapper.selectById(4L)).thenReturn(category);

        QuoteSubmitReq.ProcessSubmitDTO row = new QuoteSubmitReq.ProcessSubmitDTO();
        row.setProcessDetailId(8L);
        row.setUnitWorkHours(1.25);

        assertThat(service.priceProcessRows(List.of(row)).get(0).get("hourlyRate")).isEqualTo(80.0);
        assertThat(service.priceProcessRows(List.of(row)).get(0).get("lineCost")).isEqualTo(100.0);
    }

    private static ProcessRoutePricingService service(
            DictProcessDetailMapper detailMapper,
            DictProcessCategoryMapper categoryMapper) {
        ProcessRoutePricingService service = new ProcessRoutePricingService();
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        return service;
    }

    private static QuoteSubmitReq.ProcessSubmitDTO row(int sortNo, Long processDetailId, Double unitWorkHours) {
        QuoteSubmitReq.ProcessSubmitDTO row = new QuoteSubmitReq.ProcessSubmitDTO();
        row.setSortNo(sortNo);
        row.setProcessDetailId(processDetailId);
        row.setUnitWorkHours(unitWorkHours);
        row.setUnitRate(999.0);
        row.setLineCost(9999.0);
        return row;
    }

    private static DictProcessDetail detail(Long id, Long categoryId, String detailName, BigDecimal hourlyRate) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setCategoryId(categoryId);
        detail.setDetailName(detailName);
        detail.setHourlyRate(hourlyRate);
        detail.setEnabled(true);
        return detail;
    }

    private static DictProcessCategory category(Long id, String processCode, String categoryName, String hourlyRate) {
        DictProcessCategory category = new DictProcessCategory();
        category.setId(id);
        category.setProcessCode(processCode);
        category.setCategoryName(categoryName);
        category.setHourlyRate(new BigDecimal(hourlyRate));
        category.setEnabled(true);
        return category;
    }
}
