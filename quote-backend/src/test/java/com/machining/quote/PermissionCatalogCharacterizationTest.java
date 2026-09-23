package com.machining.quote;

import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.service.PermissionService;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogCharacterizationTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path CONTROLLER_DIR = MAIN_JAVA.resolve(Path.of("com", "machining", "quote", "controller"));
    private static final Path MAPPING_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path BASELINE_SEED = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path FRONTEND_ROUTER = Path.of("..", "quote-frontend", "src", "router", "index.js");

    private static final Pattern FALLBACK_PERMISSION = Pattern.compile(
            "permission\\((\\d+)L,\\s*\\\"(MENU|BUTTON|API)\\\",\\s*\\\"([^\\\"]+)\\\",\\s*\\\"([^\\\"]+)\\\",\\s*(null|\\\"[^\\\"]+\\\"),\\s*(null|\\\"[^\\\"]+\\\"),\\s*(true|false),\\s*\\d+(?:,\\s*(\\d+)L)?\\)",
            Pattern.MULTILINE
    );
    private static final Pattern SEED_PERMISSION = Pattern.compile(
            "INSERT INTO system_permission(?: \\([^)]*\\))? VALUES " +
                    "\\((\\d+),\\s*(NULL|\\d+),\\s*'([^']+)',\\s*'([^']+)',\\s*'([^']*)',\\s*(NULL|'[^']*'),\\s*(NULL|'[^']*'),\\s*(NULL|'[^']*'),\\s*(\\d+),\\s*(true|false),\\s*(true|false),\\s*'[^']*',\\s*'[^']*',\\s*(true|false)\\);",
            Pattern.MULTILINE
    );
    private static final Pattern ROUTE_PERMISSION = Pattern.compile(
            "\\{ path: '([^']+)', component: \\w+, meta: \\{ permission: '([^']+)' } }"
    );
    private static final Pattern PERMISSION_CATALOG_ROW = Pattern.compile("^\\| `([a-z]+:[a-z0-9-]+:[a-z0-9-]+)` \\|");
    private static final Pattern REQUIRE_ROLE = Pattern.compile("@RequireRole\\(\\{([^}]*)}\\)");

    @Test
    void permissionDocumentationFreezesUniquePermissionCodes() throws IOException {
        String doc = readMappingDoc();
        List<String> codes = new ArrayList<>();
        for (String line : doc.split("\\R")) {
            Matcher matcher = PERMISSION_CATALOG_ROW.matcher(line);
            if (matcher.find()) {
                codes.add(matcher.group(1));
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String code : codes) {
            if (!seen.add(code)) {
                duplicates.add(code);
            }
        }

        assertThat(doc).contains("权限清单冻结版本：Stage 8.6 授权树收口版");
        assertThat(codes)
                .as("Permission catalog documentation must freeze the target permission catalog before RBAC migrations start")
                .contains(
                        "market:rfq:import",
                        "engineer:pricing:save-item",
                        "market:release:create",
                        "system:user:update",
                        "system:role:assign-permission",
                        "system:audit:export"
                );
        assertThat(duplicates)
                .as("Permission codes must stay unique")
                .isEmpty();
    }

    @Test
    void requireRoleMigrationCatalogCoversAllCurrentAnnotations() throws IOException {
        String doc = readMappingDoc();
        List<String> missing = new ArrayList<>();

        try (Stream<Path> files = Files.walk(CONTROLLER_DIR)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectMissingMigrationRows(path, doc, missing));
        }

        assertThat(missing)
                .as("Every current @RequireRole occurrence needs an explicit migration row")
                .isEmpty();
    }

    @Test
    void roleMigrationKeepsCurrentBuiltInRolesExplicit() throws IOException {
        String doc = readMappingDoc();

        assertThat(doc)
                .contains("| `ADMIN` | `SUPER_ADMIN` |")
                .contains("| `MARKET` | `MARKET_USER` |")
                .contains("| `ENGINEER` | `ENGINEER_USER` |")
                .contains("管理员继续拥有全部功能权限")
                .contains("市场与工程账号迁移后默认保持现有可访问页面不变");
    }

    @Test
    void fallbackPermissionInfoExposesVisibleMenuTreeForDynamicShell() {
        PermissionService permissionService = new PermissionService();

        PermissionInfoDTO info = permissionService.getPermissionInfo(null, "ADMIN");

        assertThat(info.getMenus())
                .extracting("path")
                .containsExactly(
                        "/market",
                        "/orders",
                        "/sales-orders",
                        "/part-search",
                        "/delivered-items",
                        "/production-items",
                        "/locked-items",
                        "/quote-process",
                        "/release",
                        "/release-append",
                        "/u9-sync",
                        "/delivery-documents",
                        "/quote-template",
                        "/engineering",
                        "/material-process",
                        "/linked-changes",
                        "/sales-order-change",
                        "/users",
                        "/roles",
                        "/organization",
                        "/customers",
                        "/system-config",
                        "/audit-logs"
                );
    }

    @Test
    void fallbackPermissionInfoReturnsButtonPermissionPoints() {
        PermissionService permissionService = new PermissionService();

        PermissionInfoDTO info = permissionService.getPermissionInfo(null, "ADMIN");

        assertThat(info.getPermissions()).contains(
                "market:rfq:import",
                "market:rfq:upload-drawing",
                "market:sales-order:view",
                "market:sales-order:customer-select",
                "market:sales-order:file-preview",
                "market:sales-order:supplement-drawing",
                "market:sales-order:discard",
                "market:sales-order:restore",
                "change:linked-change:view",
                "change:linked-change:customer-select",
                "change:linked-change:handle",
                "market:quote-process:update-lead-time",
                "market:quote-process:save-quote",
                "market:quote-process:export",
                "market:delivered-item:wms-allocate",
                "market:production-item:view",
                "market:production-item:customer-select",
                "market:production-item:mes-production-submit",
                "market:release:create",
                "market:template:upload",
                "engineer:setup:save-config",
                "engineer:pricing:save-item",
                "engineer:pricing:refresh",
                "engineer:pricing:complete-order",
                "engineer:pricing:export-sheet",
                "engineer:dict:material-manage",
                "engineer:dict:process-manage",
                "engineer:dict:fee-manage",
                "system:user:create",
                "system:user:update",
                "system:user:disable",
                "system:user:reset-password",
                "system:user:delete",
                "system:role:create",
                "system:role:update",
                "system:role:delete",
                "system:role:assign-permission",
                "system:role:assign-data-scope",
                "system:dept:manage",
                "system:post:manage",
                "system:customer:sync",
                "system:customer:supplier-code-update",
                "system:audit:export",
                "system:audit:retention"
        );
        assertThat(info.getPermissions()).doesNotContain(
                "market:rfq:order-type-select",
                "market:release:reset",
                "market:sales-match:view",
                "market:sales-match:customer-select",
                "market:sales-match:order-select",
                "market:sales-match:apply",
                "system:customer:create",
                "system:customer:update",
                "system:customer:delete"
        );
    }

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
                        "market:sales-order:view",
                        "market:delivered-item:view",
                        "market:production-item:view",
                        "market:locked-item:view",
                        "change:linked-change:view",
                        "change:sales-order:view",
                        "market:delivery-document:view",
                        "engineer:setup:view",
                        "system:role:view"
                )
                .doesNotContain(
                        "market:rfq:order-type-select",
                        "market:release:reset",
                        "market:locked-item:mes-production-submit",
                        "common:customer:select",
                        "common:order:select",
                        "market:sales-match:view"
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
                        "Map.entry(\"engineer:pricing:export-sheet\", Set.of(\"ENGINEER_USER\", \"ENGINEER_MANAGER\"))",
                        "Map.entry(\"system:customer:supplier-code-update\", Set.of(\"MARKET_MANAGER\"))"
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
                "market:sales-order:view",
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

    private void collectMissingMigrationRows(Path path, String doc, List<String> missing) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = REQUIRE_ROLE.matcher(source);
            String relativePath = "controller/" + path.getFileName();
            while (matcher.find()) {
                String roleSet = normalizeRoleSet(matcher.group(1));
                if (!doc.contains(relativePath) || !doc.contains(roleSet)) {
                    missing.add(relativePath + " " + roleSet);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan " + path, e);
        }
    }

    private String normalizeRoleSet(String rawRoles) {
        return rawRoles.replace("\"", "")
                .replace(" ", "")
                .replace("\r", "")
                .replace("\n", "");
    }

    private String readMappingDoc() throws IOException {
        assertThat(MAPPING_DOC)
                .as("Permission mapping document must exist")
                .exists();
        return Files.readString(MAPPING_DOC, StandardCharsets.UTF_8);
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
