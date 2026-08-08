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

    private Map<String, Object> route(int sortNo, long processDetailId, String detailName, double unitWorkHours) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sortNo", sortNo);
        row.put("processDetailId", processDetailId);
        row.put("detailName", detailName);
        row.put("processName", detailName);
        row.put("calcType", "工时");
        row.put("unitWorkHours", unitWorkHours);
        row.put("inputVal", unitWorkHours);
        return row;
    }
}
