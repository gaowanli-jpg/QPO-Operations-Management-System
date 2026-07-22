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
                route(20, "CNC2", 2.5),
                route(10, "MILL", 1.0)
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> normalized = ReflectionTestUtils.invokeMethod(service, "normalizeProcessRoute", input);
        assertThat(normalized).extracting(route -> String.valueOf(route.get("processName"))).containsExactly("MILL", "CNC2");

        @SuppressWarnings("unchecked")
        List<QuoteSubmitReq.ProcessSubmitDTO> rows = ReflectionTestUtils.invokeMethod(service, "toProcessSubmitRows", (Object) input);
        assertThat(rows).extracting(QuoteSubmitReq.ProcessSubmitDTO::getSortNo).containsExactly(10, 20);
        assertThat(rows).extracting(QuoteSubmitReq.ProcessSubmitDTO::getProcessName).containsExactly("MILL", "CNC2");
    }

    private Map<String, Object> route(int sortNo, String processName, double inputVal) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sortNo", sortNo);
        row.put("processName", processName);
        row.put("calcType", "time");
        row.put("inputVal", inputVal);
        return row;
    }
}
