package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionModuleTreeTest {
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path FRONTEND_ROUTER = Path.of("..", "quote-frontend", "src", "router", "index.js");

    private static final Pattern FALLBACK_PERMISSION = Pattern.compile(
            "permission\\((\\d+)L,\\s*\\\"(MENU|BUTTON|API)\\\",\\s*\\\"([^\\\"]+)\\\",\\s*\\\"([^\\\"]+)\\\",\\s*(null|\\\"[^\\\"]+\\\"),\\s*(null|\\\"[^\\\"]+\\\"),\\s*(true|false),\\s*\\d+(?:,\\s*(\\d+)L)?\\)",
            Pattern.MULTILINE
    );
    private static final Pattern SEED_PERMISSION = Pattern.compile(
            "INSERT INTO system_permission \\(id, parent_id, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, enabled, created_at, updated_at, assignable\\) VALUES " +
                    "\\((\\d+),\\s*(NULL|\\d+),\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']*)',\\s*(NULL|'[^']*'),\\s*(NULL|'[^']*'),\\s*(NULL|'[^']*'),\\s*(\\d+),\\s*(true|false),\\s*(true|false),\\s*'[^']*',\\s*'[^']*',\\s*(true|false)\\);",
            Pattern.MULTILINE
    );
    private static final Pattern ROUTE_PERMISSION = Pattern.compile(
            "\\{ path: '([^']+)', component: \\w+, meta: \\{ permission: '([^']+)' } }"
    );

    @Test
    void fallbackPermissionCodesStayUniqueAndCurrent() throws IOException {
        List<String> codes = fallbackPermissions().stream().map(PermissionRow::code).toList();

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                duplicates.add(code);
            }
        }

        assertThat(codes)
                .as("Permission fallback catalog must remain the emergency source of truth")
                .contains(
                        "market:rfq:view",
                        "market:order:view",
                        "market:sales-match:view",
                        "market:delivered-item:view",
                        "market:production-item:view",
                        "market:locked-item:view",
                        "market:delivery-document:view",
                        "engineer:setup:view",
                        "system:role:view"
                )
                .doesNotContain(
                        "market:rfq:order-type-select",
                        "market:release:reset",
                        "market:locked-item:mes-production-submit",
                        "common:customer:select",
                        "common:order:select"
                );
        assertThat(duplicates)
                .as("Permission codes must be unique in the fallback catalog")
                .isEmpty();
    }

    @Test
    void protectedFrontendRoutesHaveMatchingMenuPermissionAndPath() throws IOException {
        Map<String, String> routes = frontendRoutePermissions();
        Map<String, String> menuPathsByPermission = new HashMap<>();
        for (PermissionRow row : fallbackPermissions()) {
            if ("MENU".equals(row.type) && row.path != null) {
                menuPathsByPermission.put(row.code, row.path);
            }
        }

        Map<String, String> mismatches = new LinkedHashMap<>();
        for (Map.Entry<String, String> route : routes.entrySet()) {
            String menuPath = menuPathsByPermission.get(route.getValue());
            if (!route.getKey().equals(menuPath)) {
                mismatches.put(route.getKey(), route.getValue() + " -> " + menuPath);
            }
        }

        assertThat(mismatches)
                .as("Every protected frontend route must map to its own MENU permission with the same route path")
                .isEmpty();
    }

    @Test
    void baselineSeedKeepsModuleChildrenAttachedToTheirMenus() throws IOException {
        Map<String, SeedPermissionRow> rows = seedPermissions();

        assertChild(rows, "market:delivered-item:view", "market:delivered-item:file-preview");
        assertChild(rows, "market:delivered-item:view", "market:delivered-item:mes-sync");
        assertChild(rows, "market:delivered-item:view", "market:delivered-item:wms-allocate");
        assertChild(rows, "market:delivered-item:view", "market:delivered-item:customer-select");

        assertChild(rows, "market:production-item:view", "market:production-item:customer-select");
        assertChild(rows, "market:production-item:view", "market:production-item:mes-production-submit");
        assertChild(rows, "market:locked-item:view", "market:locked-item:customer-select");

        assertChild(rows, "market:sales-match:view", "market:sales-match:customer-select");
        assertChild(rows, "market:sales-match:view", "market:sales-match:order-select");
        assertChild(rows, "market:sales-match:view", "market:sales-match:apply");
        assertChild(rows, "market:sales-match:view", "market:sales-match:file-preview");

        assertChild(rows, "market:delivery-document:view", "market:delivery-document:customer-select");
        assertChild(rows, "market:delivery-document:view", "market:delivery-document:order-select");
        assertChild(rows, "market:delivery-document:view", "market:delivery-document:export");
    }

    @Test
    void baselineSeedRetiresOldAssignablePermissionsWithoutReintroducingCommonScope() throws IOException {
        Map<String, SeedPermissionRow> rows = seedPermissions();
        String seed = Files.readString(BASELINE_SEED, StandardCharsets.UTF_8);

        assertThat(rows)
                .containsKeys("market:history-order:view", "market:locked-item:mes-production-submit")
                .doesNotContainKeys("market:rfq:order-type-select", "common:customer:select", "common:order:select");
        assertThat(rows.get("market:history-order:view"))
                .extracting(SeedPermissionRow::visible, SeedPermissionRow::assignable)
                .containsExactly(false, false);
        assertThat(rows.get("market:locked-item:mes-production-submit"))
                .extracting(SeedPermissionRow::visible, SeedPermissionRow::enabled, SeedPermissionRow::assignable)
                .containsExactly(false, false, false);
        assertThat(seed)
                .as("New clean baseline should not include historical delete/update migration scripts")
                .doesNotContain("DELETE FROM system_role_permission", "DELETE FROM system_permission", "UPDATE system_permission SET parent_id");
    }

    @Test
    void permissionServiceDefaultRoleMappingsMatchBusinessBoundaries() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(source)
                .contains(
                        "Map.entry(\"market:delivered-item:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:delivered-item:mes-sync\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:delivered-item:wms-allocate\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:production-item:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:production-item:mes-production-submit\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:delivery-document:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:delivery-document:export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"engineer:pricing:export-sheet\", Set.of(\"ENGINEER_USER\", \"ENGINEER_MANAGER\"))"
                )
                .doesNotContain(
                        "Map.entry(\"market:delivery-document:export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:locked-item:mes-production-submit\"",
                        "Map.entry(\"market:rfq:order-type-select\"",
                        "common:customer:select",
                        "common:order:select"
                );
    }

    @Test
    void modulePermissionRowsRemainAlignedBetweenServiceAndBaselineSeed() throws IOException {
        Map<String, PermissionRow> serviceRows = fallbackPermissionMap();
        Map<String, SeedPermissionRow> seedRows = seedPermissions();

        for (String code : List.of(
                "market:sales-match:view",
                "market:order:view",
                "market:delivered-item:view",
                "market:production-item:view",
                "market:locked-item:view",
                "market:quote-process:save-quote",
                "market:delivery-document:view",
                "market:delivery-document:export",
                "engineer:pricing:export-sheet"
        )) {
            assertThat(seedRows).containsKey(code);
            assertThat(seedRows.get(code).type).isEqualTo(serviceRows.get(code).type);
            assertThat(seedRows.get(code).path).isEqualTo(serviceRows.get(code).path);
        }
    }

    private static void assertChild(Map<String, SeedPermissionRow> rows, String parentCode, String childCode) {
        SeedPermissionRow parent = rows.get(parentCode);
        SeedPermissionRow child = rows.get(childCode);
        assertThat(parent).as("Missing parent permission %s", parentCode).isNotNull();
        assertThat(child).as("Missing child permission %s", childCode).isNotNull();
        assertThat(child.parentId).as("%s should be attached under %s", childCode, parentCode).isEqualTo(parent.id);
    }

    private List<PermissionRow> fallbackPermissions() throws IOException {
        return new ArrayList<>(fallbackPermissionMap().values());
    }

    private Map<String, PermissionRow> fallbackPermissionMap() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        Matcher matcher = FALLBACK_PERMISSION.matcher(source);
        Map<String, PermissionRow> rows = new LinkedHashMap<>();
        while (matcher.find()) {
            String path = tokenToString(matcher.group(5));
            Long parentId = matcher.group(8) == null ? null : Long.valueOf(matcher.group(8));
            rows.put(matcher.group(3), new PermissionRow(
                    Long.valueOf(matcher.group(1)),
                    parentId,
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4),
                    path,
                    Boolean.parseBoolean(matcher.group(7))
            ));
        }
        return rows;
    }

    private Map<String, SeedPermissionRow> seedPermissions() throws IOException {
        String seed = Files.readString(BASELINE_SEED, StandardCharsets.UTF_8);
        Matcher matcher = SEED_PERMISSION.matcher(seed);
        Map<String, SeedPermissionRow> rows = new LinkedHashMap<>();
        while (matcher.find()) {
            Long parentId = "NULL".equals(matcher.group(2)) ? null : Long.valueOf(matcher.group(2));
            rows.put(matcher.group(4), new SeedPermissionRow(
                    Long.valueOf(matcher.group(1)),
                    parentId,
                    matcher.group(3),
                    matcher.group(4),
                    matcher.group(5),
                    tokenToString(matcher.group(6)),
                    Boolean.parseBoolean(matcher.group(10)),
                    Boolean.parseBoolean(matcher.group(11)),
                    Boolean.parseBoolean(matcher.group(12))
            ));
        }
        return rows;
    }

    private Map<String, String> frontendRoutePermissions() throws IOException {
        String source = Files.readString(FRONTEND_ROUTER, StandardCharsets.UTF_8);
        Matcher matcher = ROUTE_PERMISSION.matcher(source);
        Map<String, String> routes = new LinkedHashMap<>();
        while (matcher.find()) {
            routes.put(matcher.group(1), matcher.group(2));
        }
        return routes;
    }

    private static String tokenToString(String token) {
        if (token == null || "NULL".equals(token) || "null".equals(token)) {
            return null;
        }
        return token.substring(1, token.length() - 1);
    }

    private record PermissionRow(Long id, Long parentId, String type, String code, String name, String path, boolean visible) {
    }

    private record SeedPermissionRow(Long id, Long parentId, String type, String code, String name, String path,
                                     boolean visible, boolean enabled, boolean assignable) {
    }
}
