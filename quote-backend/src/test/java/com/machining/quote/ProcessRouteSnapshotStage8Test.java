package com.machining.quote;

import com.machining.quote.service.ProcessRouteSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRouteSnapshotStage8Test {

    @Test
    @SuppressWarnings("unchecked")
    void copiesStandardRouteSnapshotWithoutRepricingOrSharingMutableMaps() {
        ProcessRouteSnapshotService service = new ProcessRouteSnapshotService();
        Map<String, Object> route = Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "包装"),
                Map.entry("detailName", "精细包装"),
                Map.entry("processName", "精细包装"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("lineCost", 15.0),
                Map.entry("sortNo", 20)
        );

        Object copied = service.copySnapshot(List.of(route));

        assertThat(copied).isInstanceOf(List.class).isNotSameAs(List.of(route));
        List<Map<String, Object>> copiedRows = (List<Map<String, Object>>) copied;
        assertThat(copiedRows).singleElement().satisfies(row -> {
            assertThat(row).isNotSameAs(route);
            assertThat(row).containsAllEntriesOf(route);
            assertThat(row.get("hourlyRate")).isEqualTo(10.0);
            assertThat(row.get("lineCost")).isEqualTo(15.0);
        });
    }

    @Test
    void formatsRouteSnapshotWithProcessIdentityAndStoredSnapshotPrice() {
        ProcessRouteSnapshotService service = new ProcessRouteSnapshotService();
        String summary = service.formatSnapshot(List.of(
                Map.of(
                        "routeType", "PROCESS",
                        "processCode", "PACKAGING",
                        "categoryName", "包装",
                        "detailName", "精细包装",
                        "hourlyRate", 10.0,
                        "unitWorkHours", 1.5,
                        "lineCost", 15.0
                ),
                Map.of(
                        "routeType", "FEE",
                        "processName", "组装费",
                        "lineCost", 30.0
                )
        ));

        assertThat(summary)
                .contains("PACKAGING", "包装", "精细包装", "1.5h", "¥10", "¥15")
                .contains("组装费", "¥30");
    }
}
