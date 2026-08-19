package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineerServiceProcessSortTests {

    @Test
    void normalizesProcessRoutesBySortNoBeforeMappingToSubmitRows() {
        EngineerService service = new EngineerService();

        List<Map<String, Object>> input = List.of(
                route(20, 2L, "CNC2", 2.5),
                route(10, 1L, "MILL", 1.0)
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> normalized = ReflectionTestUtils.invokeMethod(service, "normalizeProcessRoute", input);
        assertThat(normalized).extracting(route -> route.get("processDetailId")).containsExactly(1L, 2L);

        @SuppressWarnings("unchecked")
        List<QuoteSubmitReq.ProcessSubmitDTO> rows = ReflectionTestUtils.invokeMethod(service, "toProcessSubmitRows", (Object) input);
        assertThat(rows).extracting(QuoteSubmitReq.ProcessSubmitDTO::getSortNo).containsExactly(10, 20);
        assertThat(rows).extracting(QuoteSubmitReq.ProcessSubmitDTO::getProcessDetailId).containsExactly(1L, 2L);
        assertThat(rows).extracting(QuoteSubmitReq.ProcessSubmitDTO::getUnitWorkHours).containsExactly(1.0, 2.5);
    }

    @Test
    void repeatRecommendedConfigPreservesConcreteProcessIdentity() {
        EngineerService service = new EngineerService();
        Map<String, Object> sourceRoute = route(10, 7L, "PRECISION_PACK", 1.25);

        @SuppressWarnings("unchecked")
        Map<String, Object> config = ReflectionTestUtils.invokeMethod(
                service,
                "buildRecommendedConfig",
                List.of(sourceRoute),
                Map.of(),
                Map.of()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processRoute = (List<Map<String, Object>>) config.get("processRoute");
        assertThat(processRoute).singleElement().satisfies(route -> {
            assertThat(route.get("processDetailId")).isEqualTo(sourceRoute.get("processDetailId"));
            assertThat(route.get("processCategoryId")).isEqualTo(sourceRoute.get("processCategoryId"));
            assertThat(route.get("processCode")).isEqualTo(sourceRoute.get("processCode"));
            assertThat(route.get("categoryName")).isEqualTo(sourceRoute.get("categoryName"));
            assertThat(route.get("detailName")).isEqualTo(sourceRoute.get("detailName"));
            assertThat(route.get("hourlyRate")).isEqualTo(sourceRoute.get("hourlyRate"));
            assertThat(route.get("processName")).isEqualTo(sourceRoute.get("processName"));
        });
    }

    @Test
    void reusableEngineerProcessRouteFiltersAssemblyFeeAndKeepsHistoricalRows() {
        EngineerService service = new EngineerService();
        String assemblyFeeName = String.valueOf(ReflectionTestUtils.getField(EngineerService.class, "ASSEMBLY_FEE_PROCESS_NAME"));
        Map<String, Object> restorableRoute = route(30, 7L, "SHARED_NAME", 1.0);
        Map<String, Object> legacyRoute = new LinkedHashMap<>(route(20, 8L, "LEGACY_NAME", 1.0));
        legacyRoute.remove("processDetailId");
        Map<String, Object> engineerConfig = Map.of("processRoute", List.of(
                Map.of(
                        "sortNo", 10,
                        "sourceType", "extra",
                        "processName", assemblyFeeName,
                        "calcType", "\u975e\u5de5\u65f6\u8d39\u7528"
                ),
                legacyRoute,
                restorableRoute
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> processRoute = ReflectionTestUtils.invokeMethod(service, "reusableEngineerProcessRoute", engineerConfig);

        assertThat(processRoute).hasSize(2);
        assertThat(processRoute.get(0).get("processDetailId")).isNull();
        assertThat(processRoute.get(0).get("processName")).isEqualTo(legacyRoute.get("processName"));
        assertThat(processRoute.get(1).get("processDetailId")).isEqualTo(restorableRoute.get("processDetailId"));
        assertThat(processRoute.get(1).get("processName")).isEqualTo(restorableRoute.get("processName"));
    }

    @Test
    void orderEngineerConfigRejectsAssemblyFeeProcessRouteSnapshots() {
        EngineerService service = new EngineerService();
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        OrderRfq order = new OrderRfq();
        order.setId(12);
        when(orderMapper.selectByIdForUpdate(12)).thenReturn(order);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("processRoute", List.of(Map.of(
                "sortNo", 10,
                "processName", "组装费",
                "calcType", "非工时费用",
                "sourceType", "extra"
        )));

        assertThatThrownBy(() -> service.saveOrderEngineerConfig(12, config))
                .hasMessageContaining("组装费")
                .hasMessageContaining("全局工艺");
        verify(orderMapper, never()).updateById(order);
    }

    private Map<String, Object> route(int sortNo, long processDetailId, String detailName, double unitWorkHours) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sortNo", sortNo);
        row.put("processDetailId", processDetailId);
        row.put("processCategoryId", processDetailId + 100);
        row.put("processCode", "PROC-" + processDetailId);
        row.put("categoryName", "Category " + processDetailId);
        row.put("detailName", detailName);
        row.put("processName", detailName);
        row.put("calcType", "工时");
        row.put("hourlyRate", 80.0);
        row.put("unitWorkHours", unitWorkHours);
        row.put("inputVal", unitWorkHours);
        return row;
    }
}
