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
    void pricesProcessRowsFromCategoryHourlyRateAndIgnoresSubmittedPriceSnapshots() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessRoutePricingService service = service(detailMapper, categoryMapper);

        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(7L);
        detail.setCategoryId(3L);
        detail.setDetailName("精细包装");
        detail.setEnabled(true);
        DictProcessCategory category = new DictProcessCategory();
        category.setId(3L);
        category.setProcessCode("PACKAGING");
        category.setCategoryName("包装");
        category.setHourlyRate(new BigDecimal("10.00"));
        category.setEnabled(true);
        when(detailMapper.selectById(7L)).thenReturn(detail);
        when(categoryMapper.selectById(3L)).thenReturn(category);

        QuoteSubmitReq.ProcessSubmitDTO row = new QuoteSubmitReq.ProcessSubmitDTO();
        row.setSortNo(20);
        row.setProcessDetailId(7L);
        row.setUnitWorkHours(2.5);
        row.setUnitRate(999.0);
        row.setLineCost(9999.0);

        List<Map<String, Object>> priced = service.priceProcessRows(List.of(row));

        assertThat(priced).singleElement().satisfies(route -> {
            assertThat(route).containsEntry("routeType", "PROCESS");
            assertThat(route).containsEntry("processDetailId", 7L);
            assertThat(route).containsEntry("processCategoryId", 3L);
            assertThat(route).containsEntry("processCode", "PACKAGING");
            assertThat(route).containsEntry("categoryName", "包装");
            assertThat(route).containsEntry("detailName", "精细包装");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(10.0);
            assertThat((Double) route.get("unitWorkHours")).isEqualTo(2.5);
            assertThat((Double) route.get("lineCost")).isEqualTo(25.0);
        });
    }

    @Test
    void quantityDoesNotChangeProcessHourlyRate() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        DictProcessCategoryMapper categoryMapper = mock(DictProcessCategoryMapper.class);
        ProcessRoutePricingService service = service(detailMapper, categoryMapper);

        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(8L);
        detail.setCategoryId(4L);
        detail.setDetailName("CNC 精加工");
        detail.setEnabled(true);
        DictProcessCategory category = new DictProcessCategory();
        category.setId(4L);
        category.setProcessCode("CNC");
        category.setCategoryName("数控");
        category.setHourlyRate(new BigDecimal("80.00"));
        category.setEnabled(true);
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
}
