package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.service.ProcessRoutePricingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessRoutePricingStage6Test {

    @Test
    void pricesProcessRowsFromQpoOwnedDetailHourlyRateAndIgnoresSubmittedPriceSnapshots() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessRoutePricingService service = service(detailMapper);

        DictProcessDetail packaging = detail(7L, "PACKAGING_FINE", "Precision packaging", new BigDecimal("10.00"));
        DictProcessDetail zeroDetail = detail(8L, "FREE_SETUP", "Free setup", BigDecimal.ZERO);
        DictProcessDetail customDetail = detail(9L, "FINE_MILL", "Fine milling", new BigDecimal("125.50"));
        when(detailMapper.selectById(7L)).thenReturn(packaging);
        when(detailMapper.selectById(8L)).thenReturn(zeroDetail);
        when(detailMapper.selectById(9L)).thenReturn(customDetail);

        List<Map<String, Object>> priced = service.priceProcessRows(List.of(
                row(20, 7L, 2.5),
                row(30, 8L, 1.5),
                row(40, 9L, 2.0)
        ));

        assertThat(priced).hasSize(3);
        assertThat(priced.get(0)).satisfies(route -> {
            assertThat(route).containsEntry("routeType", "PROCESS");
            assertThat(route).containsEntry("processDetailId", 7L);
            assertThat(route).containsEntry("processCategoryId", null);
            assertThat(route).containsEntry("processCode", "PACKAGING_FINE");
            assertThat(route).containsEntry("categoryName", null);
            assertThat(route).containsEntry("detailName", "Precision packaging");
            assertThat((BigDecimal) route.get("detailHourlyRate")).isEqualByComparingTo("10.00");
            assertThat(route).containsEntry("categoryHourlyRate", null);
            assertThat(route).containsEntry("hourlyRateSource", "DETAIL");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(10.0);
            assertThat((Double) route.get("unitRate")).isEqualTo(10.0);
            assertThat((Double) route.get("unitWorkHours")).isEqualTo(2.5);
            assertThat((Double) route.get("lineCost")).isEqualTo(25.0);
        });
        assertThat(priced.get(1)).satisfies(route -> {
            assertThat(route).containsEntry("processDetailId", 8L);
            assertThat(route).containsEntry("processCode", "FREE_SETUP");
            assertThat(route).containsEntry("categoryHourlyRate", null);
            assertThat(route).containsEntry("hourlyRateSource", "DETAIL");
            assertThat((BigDecimal) route.get("detailHourlyRate")).isEqualByComparingTo("0.00");
            assertThat((Double) route.get("hourlyRate")).isEqualTo(0.0);
            assertThat((Double) route.get("unitRate")).isEqualTo(0.0);
            assertThat((Double) route.get("lineCost")).isEqualTo(0.0);
        });
        assertThat(priced.get(2)).satisfies(route -> {
            assertThat(route).containsEntry("processDetailId", 9L);
            assertThat(route).containsEntry("processCode", "FINE_MILL");
            assertThat(route).containsEntry("categoryHourlyRate", null);
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
        ProcessRoutePricingService service = service(detailMapper);

        DictProcessDetail detail = detail(8L, "CNC_FINISH", "CNC finishing", new BigDecimal("80.00"));
        when(detailMapper.selectById(8L)).thenReturn(detail);

        QuoteSubmitReq.ProcessSubmitDTO row = new QuoteSubmitReq.ProcessSubmitDTO();
        row.setProcessDetailId(8L);
        row.setUnitWorkHours(1.25);

        assertThat(service.priceProcessRows(List.of(row)).get(0).get("hourlyRate")).isEqualTo(80.0);
        assertThat(service.priceProcessRows(List.of(row)).get(0).get("lineCost")).isEqualTo(100.0);
    }

    @Test
    void disabledMesCategoryDoesNotBlockEnabledQpoOwnedProcessDetail() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessRoutePricingService service = service(detailMapper);
        DictProcessDetail detail = detail(8L, "CNC_FINISH", "CNC finishing", new BigDecimal("80.00"));
        detail.setCategoryId(4L);
        when(detailMapper.selectById(8L)).thenReturn(detail);

        Map<String, Object> priced = service.priceProcessRows(List.of(row(10, 8L, 1.25))).get(0);

        assertThat(priced).containsEntry("processCode", "CNC_FINISH");
        assertThat(priced).containsEntry("lineCost", 100.0);
        assertThat(priced).containsEntry("categoryName", null);
    }

    @Test
    void rejectsEnabledDetailsWithoutQpoOwnedHourlyRate() {
        DictProcessDetailMapper detailMapper = mock(DictProcessDetailMapper.class);
        ProcessRoutePricingService service = service(detailMapper);
        when(detailMapper.selectById(8L)).thenReturn(detail(8L, "CNC_FINISH", "CNC finishing", null));

        assertThatThrownBy(() -> service.priceProcessRows(List.of(row(10, 8L, 1.25))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("具体工艺小时价格");
    }

    private static ProcessRoutePricingService service(DictProcessDetailMapper detailMapper) {
        ProcessRoutePricingService service = new ProcessRoutePricingService();
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
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

    private static DictProcessDetail detail(Long id, String processCode, String detailName, BigDecimal hourlyRate) {
        DictProcessDetail detail = new DictProcessDetail();
        detail.setId(id);
        detail.setProcessCode(processCode);
        detail.setDetailName(detailName);
        detail.setHourlyRate(hourlyRate);
        detail.setEnabled(true);
        return detail;
    }
}
