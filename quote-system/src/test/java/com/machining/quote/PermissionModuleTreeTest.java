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
    private static final Path QUOTE_SAVE_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V33__quote_process_save_quote_permission.sql");
    private static final Path FRONTEND_ROUTER = Path.of("..", "quote-frontend", "src", "router", "index.js");
    private static final Pattern FALLBACK_PERMISSION = Pattern.compile(
            "permission\\([^,]+,\\s*\"(MENU|BUTTON|API)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"[^\"]+\"\\s*,\\s*(null|\"[^\"]+\")",
            Pattern.MULTILINE
    );
    private static final Pattern ROUTE_PERMISSION = Pattern.compile(
            "\\{ path: '([^']+)', component: \\w+, meta: \\{ permission: '([^']+)' } }"
    );

    @Test
    void fallbackPermissionCodesStayUnique() throws IOException {
        List<String> codes = fallbackPermissions().stream().map(row -> row.code).toList();

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                duplicates.add(code);
            }
        }

        assertThat(codes)
                .as("Permission fallback catalog must remain the emergency source of truth")
                .contains("market:rfq:view", "engineer:setup:view", "system:role:view");
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
    void commonSelectionPermissionsAreRetiredInFavorOfModuleScopedPermissions() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(source)
                .as("Common permissions must stop being directly assignable; migration should introduce assignable filtering")
                .contains("assignable");
        assertThat(source)
                .as("Customer/order/file/task capabilities must be module-scoped")
                .contains(
                        "market:rfq:customer-select",
                        "market:quote-process:order-select",
                        "market:release:order-select",
                        "market:history-order:customer-select",
                        "engineer:setup:order-select"
                );
        assertThat(source)
                .as("Old common permissions may remain only as hidden compatibility, not as assignable fallback entries")
                .doesNotContain(
                        "permission(11L, \"API\", \"common:order:select\"",
                        "permission(12L, \"API\", \"common:customer:select\"",
                        "permission(13L, \"API\", \"common:file:preview\"",
                        "permission(14L, \"API\", \"common:task:view\""
                );
    }

    @Test
    void quoteProcessSaveQuotePermissionBelongsToQuoteProcessModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Saving a quote sheet is a distinct market confirmation action")
                .contains("market:quote-process:save-quote");
        assertThat(source)
                .as("Save quote permission must sit under the quote process MENU permission parent id")
                .contains("permission(146L, \"BUTTON\", \"market:quote-process:save-quote\", \"保存报价单\", null, null, false, 136, 4L)");
        assertThat(source)
                .as("Market roles can save quote sheets; read-only users cannot")
                .contains("Map.entry(\"market:quote-process:save-quote\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))");
    }

    @Test
    void quoteProcessSaveQuotePermissionMigrationKeepsDatabaseCatalogAligned() throws IOException {
        String migration = Files.readString(QUOTE_SAVE_PERMISSION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("market:quote-process:view")
                .contains("market:quote-process:save-quote")
                .contains("保存报价单")
                .contains("MARKET_USER")
                .contains("MARKET_MANAGER")
                .contains("SUPER_ADMIN")
                .doesNotContain("READONLY");
    }

    private List<PermissionRow> fallbackPermissions() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        Matcher matcher = FALLBACK_PERMISSION.matcher(source);
        List<PermissionRow> rows = new ArrayList<>();
        while (matcher.find()) {
            String pathToken = matcher.group(3);
            String path = "null".equals(pathToken) ? null : pathToken.substring(1, pathToken.length() - 1);
            rows.add(new PermissionRow(matcher.group(1), matcher.group(2), path));
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

    private record PermissionRow(String type, String code, String path) {
    }
}
