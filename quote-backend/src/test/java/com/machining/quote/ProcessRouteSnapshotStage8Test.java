package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.service.JsonSnapshotCopyService;
import com.machining.quote.service.ProcessRouteSnapshotService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRouteSnapshotStage8Test {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Test
    @SuppressWarnings("unchecked")
    void copiesJsonNodeRouteArrayWithoutDroppingRows() throws Exception {
        ProcessRouteSnapshotService service = new ProcessRouteSnapshotService();
        JsonNode routeNode = OBJECT_MAPPER.readTree("""
                [
                  {"routeType":"PROCESS","processCode":"CNC","detailName":"精加工","unitWorkHours":1.25,"nested":{"source":"json-node"}}
                ]
                """);

        Object copied = service.copySnapshot(routeNode);

        assertThat(copied).isInstanceOf(List.class);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) copied;
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("routeType", "PROCESS");
            assertThat(row).containsEntry("processCode", "CNC");
            assertThat(row).containsEntry("detailName", "精加工");
            assertThat((Map<String, Object>) row.get("nested")).containsEntry("source", "json-node");
        });
    }

    @Test
    void treatsNullBlankAndEmptyJsonShapesAsMissingSnapshots() {
        JsonSnapshotCopyService copier = new JsonSnapshotCopyService();

        assertThat(copier.isMissingSnapshot(null)).isTrue();
        assertThat(copier.isMissingSnapshot("   ")).isTrue();
        assertThat(copier.isMissingSnapshot("{}")).isTrue();
        assertThat(copier.isMissingSnapshot("[]")).isTrue();
        assertThat(copier.isMissingSnapshot(Map.of())).isTrue();
        assertThat(copier.isMissingSnapshot(List.of())).isTrue();
        assertThat(copier.isMissingSnapshot(OBJECT_MAPPER.createObjectNode())).isTrue();
        assertThat(copier.isMissingSnapshot(OBJECT_MAPPER.createArrayNode())).isTrue();
        assertThat(copier.isMissingSnapshot("not-json")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deepCopiesNestedMapsAndListsWithoutSharingMutableState() {
        JsonSnapshotCopyService copier = new JsonSnapshotCopyService();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("value", "original");
        List<Object> rows = new ArrayList<>();
        rows.add(nested);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("rows", rows);

        Map<String, Object> copied = (Map<String, Object>) copier.deepCopySnapshot(source);
        List<Map<String, Object>> copiedRows = (List<Map<String, Object>>) copied.get("rows");
        copiedRows.get(0).put("value", "changed");

        assertThat(copied).isNotSameAs(source);
        assertThat(copiedRows).isNotSameAs(rows);
        assertThat(copiedRows.get(0)).isNotSameAs(nested);
        assertThat(nested).containsEntry("value", "original");
    }

    @Test
    void countsRowsConsistentlyForJsonStringListAndJsonNode() throws Exception {
        ProcessRouteSnapshotService service = new ProcessRouteSnapshotService();
        String routeJson = """
                [
                  {"routeType":"PROCESS","processCode":"CNC"},
                  {"routeType":"FEE","processName":"包装费"}
                ]
                """;
        List<Map<String, Object>> routeList = List.of(
                Map.of("routeType", "PROCESS", "processCode", "CNC"),
                Map.of("routeType", "FEE", "processName", "包装费"));
        JsonNode routeNode = OBJECT_MAPPER.readTree(routeJson);

        assertThat(service.countSnapshotRows(routeJson)).isEqualTo(2);
        assertThat(service.countSnapshotRows(routeList)).isEqualTo(2);
        assertThat(service.countSnapshotRows(routeNode)).isEqualTo(2);
    }

    @Test
    void invalidJsonStringIsTreatedAsMissingInsteadOfLiteralSnapshot() {
        JsonSnapshotCopyService copier = new JsonSnapshotCopyService();
        ProcessRouteSnapshotService service = new ProcessRouteSnapshotService();

        assertThat(copier.deepCopySnapshot("not-json")).isNull();
        assertThat(service.copySnapshot("not-json")).isEqualTo(List.of());
    }
}
