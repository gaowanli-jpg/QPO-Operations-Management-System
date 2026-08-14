package com.machining.quote;

import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                Map.of("route-key", 1),
                Map.of("route-key", List.of(sourceRoute)),
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
    void repeatRouteSignatureUsesConcreteProcessIdentityInsteadOfNameOnly() {
        EngineerService service = new EngineerService();
        Map<String, Object> firstRoute = route(10, 7L, "SHARED_NAME", 1.0);
        Map<String, Object> secondRoute = route(10, 8L, "SHARED_NAME", 1.0);

        String firstSignature = ReflectionTestUtils.invokeMethod(service, "routeSignature", List.of(firstRoute));
        String secondSignature = ReflectionTestUtils.invokeMethod(service, "routeSignature", List.of(secondRoute));

        assertThat(firstSignature).isNotEqualTo(secondSignature);
        assertThat(firstSignature).contains("process-detail:7");
        assertThat(secondSignature).contains("process-detail:8");
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
