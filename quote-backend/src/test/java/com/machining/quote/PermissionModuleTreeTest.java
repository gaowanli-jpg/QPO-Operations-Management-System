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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionModuleTreeTest {
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path QUOTE_SAVE_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V33__quote_process_save_quote_permission.sql");
    private static final Path RETIRE_COMMON_SELECTION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V38__retire_common_selection_permissions.sql");
    private static final Path ORGANIZATION_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V40__organization_identity_permissions.sql");
    private static final Path PRICING_SHEET_EXPORT_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V41__engineer_pricing_sheet_export_permission.sql");
    private static final Path DELIVERED_ITEM_FOUNDATION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V45__delivery_ticket_mes_wms_foundation.sql");
    private static final Path DELIVERED_ITEM_MES_SYNC_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V46__delivery_ticket_mes_sync_permission.sql");
    private static final Path WMS_ALLOCATION_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V47__delivery_ticket_wms_allocation_permissions.sql");
    private static final Path MES_WMS_PERMISSION_TREE_REPAIR_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V49__mes_wms_permission_tree_repair.sql");
    private static final Path DELIVERY_COMMON_QUERY_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V51__delivery_common_query_indexes.sql");
    private static final Path RETIRE_RFQ_ORDER_TYPE_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V53__retire_rfq_order_type_select_permission.sql");
    private static final Path DELIVERY_DOCUMENT_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V57__delivery_document_module_permissions.sql");
    private static final Path INQUIRY_ORDER_RENAME_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V70__rename_order_management_to_inquiry_orders.sql");
    private static final Path LOCKED_ITEM_MES_PRODUCTION_PERMISSION_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V76__locked_item_mes_production_dispatch.sql");
    private static final Path SYSTEM_USER_ROLE_MODULE_RENAME_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V80__rename_user_role_permission_modules.sql");
    private static final Path WMS_ALLOCATION_PLAN = Path.of("..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path PERMISSION_LIST_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path DOCS_ROOT = Path.of("..", "docs");
    private static final Path FRONTEND_ROUTER = Path.of("..", "quote-frontend", "src", "router", "index.js");
    private static final List<String> APS_TARGET_SYSTEM_PHRASES = List.of(
            "APS 同步工艺快照",
            "本批次 APS",
            "APS 需要的",
            "APS 增量同步",
            "APS 同步安全性",
            "APS 继续从",
            "APS 同步只从",
            "APS 数据源对新",
            "APS 数据源只读取",
            "APS 同步能拉到",
            "APS 同步新数据",
            "APS 同步继续按",
            "APS 端可继续",
            "APS 会被迫",
            "APS 可能已经",
            "APS 同步稳定性",
            "APS 同步不能被破坏"
    );
    private static final Pattern FALLBACK_PERMISSION = Pattern.compile(
            "permission\\([^,]+,\\s*\"(MENU|BUTTON|API)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*(null|\"[^\"]+\")",
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
    void rfqOrderTypeSelectionPermissionIsRetiredFromNewRfqImportModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Order type is now chosen during delivery ticket generation, not RFQ import")
                .doesNotContain("market:rfq:order-type-select");
        assertThat(source)
                .as("New RFQ import must not expose an assignable order-type button")
                .doesNotContain("permission(157L, \"BUTTON\", \"market:rfq:order-type-select\"");
        assertThat(source)
                .as("Default role mapping must not grant the retired RFQ order-type ability")
                .doesNotContain("Map.entry(\"market:rfq:order-type-select\"");
    }

    @Test
    void rfqOrderTypeSelectionPermissionRetirementMigrationRemovesAssignableCatalogEntry() throws IOException {
        String migration = Files.readString(RETIRE_RFQ_ORDER_TYPE_PERMISSION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("market:rfq:order-type-select")
                .contains("DELETE FROM system_role_permission")
                .contains("DELETE FROM system_permission");
    }

    @Test
    void deliveredItemPermissionIsIndependentOrderMenuModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Delivered item demand pool needs its own left-sidebar MENU permission")
                .contains("market:delivered-item:view", "market:delivered-item:file-preview");
        assertThat(source)
                .as("Delivered items must be a visible MENU route, not a reused order management permission")
                .contains("permission(158L, \"MENU\", \"market:delivered-item:view\"")
                .contains("\"/delivered-items\"")
                .contains("\"DeliveredItemManage\"");
        assertThat(source)
                .as("Delivered item file preview must hang under the delivered item module")
                .contains("permission(159L, \"API\", \"market:delivered-item:file-preview\"")
                .contains("158L)");
        assertThat(source)
                .as("Market roles and read-only roles can view the demand pool by default")
                .contains("Map.entry(\"market:delivered-item:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
    }

    @Test
    void deliveryDocumentPermissionsFormIndependentMenuTree() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        String migration = Files.readString(DELIVERY_DOCUMENT_PERMISSION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .contains(
                        "market:delivery-document:view",
                        "market:delivery-document:customer-select",
                        "market:delivery-document:order-select",
                        "market:delivery-document:export"
                );
        assertThat(source)
                .contains(
                        "permission(165L, \"MENU\", \"market:delivery-document:view\", \"提单表据\", \"/delivery-documents\", \"DeliveryDocumentManage\", true, 150)",
                        "permission(166L, \"API\", \"market:delivery-document:customer-select\", \"提单表据选择客户\", null, null, false, 151, 165L)",
                        "permission(167L, \"API\", \"market:delivery-document:order-select\", \"提单表据选择订单\", null, null, false, 152, 165L)",
                        "permission(168L, \"BUTTON\", \"market:delivery-document:export\", \"导出提单表据\", null, null, false, 153, 165L)",
                        "Map.entry(\"market:delivery-document:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:delivery-document:export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))"
                )
                .doesNotContain("Map.entry(\"market:delivery-document:export\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
        assertThat(source.indexOf("\"market:u9-sync:view\""))
                .isBetween(source.indexOf("\"market:release:view\""), source.indexOf("\"market:delivery-document:view\""));
        assertThat(migration)
                .contains(
                        "('market:delivery-document:view', 'API', 'market:delivery-document:customer-select'",
                        "('market:delivery-document:view', 'API', 'market:delivery-document:order-select'",
                        "('market:delivery-document:view', 'BUTTON', 'market:delivery-document:export'",
                        "AND r.role_code = 'READONLY'",
                        "AND p.permission_code = 'market:delivery-document:export'"
                );
    }

    @Test
    void deliveredItemMesSyncPermissionBelongsToDeliveredItemModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Getting MES material numbers is a delivered-item action, not a common permission")
                .contains("market:delivered-item:mes-sync");
        assertThat(source)
                .as("MES material sync must hang under the delivered-item MENU permission")
                .contains("permission(160L, \"BUTTON\", \"market:delivered-item:mes-sync\", \"获取MES物料号\", null, null, false, 128, 158L)");
        assertThat(source)
                .as("Market users and managers can request MES material numbers; read-only users cannot")
                .contains("Map.entry(\"market:delivered-item:mes-sync\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))")
                .doesNotContain("Map.entry(\"market:delivered-item:mes-sync\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
    }

    @Test
    void deliveredItemMesSyncMigrationKeepsDatabaseCatalogAligned() throws IOException {
        String migration = Files.readString(DELIVERED_ITEM_MES_SYNC_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .as("MES material sync must be added to the database permission tree under delivered items")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:mes-sync",
                        "BUTTON",
                        "128",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER"
                )
                .doesNotContain("WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')");
    }

    @Test
    void wmsAllocationPermissionsAreReservedForDeliveredAndLockedItemModules() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("WMS allocation must be module-owned, not a common permission")
                .contains(
                        "market:delivered-item:wms-allocate",
                        "market:locked-item:view"
                );
        assertThat(source)
                .as("WMS allocation must hang under delivered items, while locked items must be its own order MENU")
                .contains(
                        "permission(161L, \"BUTTON\", \"market:delivered-item:wms-allocate\"",
                        "129, 158L)",
                        "permission(162L, \"MENU\", \"market:locked-item:view\"",
                        "\"/locked-items\"",
                        "\"LockedItemManage\""
                );
        assertThat(source)
                .as("Market roles can submit WMS allocation; read-only users can only view locked items")
                .contains(
                        "Map.entry(\"market:delivered-item:wms-allocate\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))",
                        "Map.entry(\"market:locked-item:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))"
                )
                .doesNotContain("Map.entry(\"market:delivered-item:wms-allocate\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
        assertThat(source)
                .as("WMS allocation permissions must not become assignable common permissions")
                .doesNotContain("common:wms", "common:locked-item");
    }

    @Test
    void lockedItemMesProductionSubmitPermissionBelongsToLockedItemModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        String migration = Files.readString(LOCKED_ITEM_MES_PRODUCTION_PERMISSION_MIGRATION, StandardCharsets.UTF_8);
        String permissionDoc = Files.readString(PERMISSION_LIST_DOC, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("MES production dispatch is a locked-item operation, not a common permission")
                .contains("market:locked-item:mes-production-submit");
        assertThat(source)
                .contains(
                        "permission(196L, \"BUTTON\", \"market:locked-item:mes-production-submit\", \"锁库零件下发MES生产\", null, null, false, 129, 162L)",
                        "Map.entry(\"market:locked-item:mes-production-submit\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))"
                )
                .doesNotContain("Map.entry(\"market:locked-item:mes-production-submit\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
        assertThat(migration)
                .contains(
                        "market:locked-item:view",
                        "market:locked-item:mes-production-submit",
                        "锁库零件下发MES生产",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER"
                )
                .doesNotContain("WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')");
        assertThat(permissionDoc)
                .contains(
                        "market:locked-item:mes-production-submit",
                        "锁库零件下发MES生产"
                )
                .doesNotContain("common:mes-production");
    }

    @Test
    void wmsAllocationPermissionMigrationKeepsDatabaseCatalogAligned() throws IOException {
        String migration = Files.readString(WMS_ALLOCATION_PERMISSION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .as("V47 must create the WMS action and locked-item menu in the database catalog")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:wms-allocate",
                        "market:locked-item:view",
                        "MENU",
                        "BUTTON",
                        "/locked-items",
                        "LockedItemManage",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "READONLY"
                )
                .doesNotContain("market:delivered-item:wms-allocate' AND r.role_code = 'READONLY'");
    }

    @Test
    void mesWmsPermissionTreeRepairMigrationKeepsDeliveredItemChildrenAttached() throws IOException {
        String migration = Files.readString(MES_WMS_PERMISSION_TREE_REPAIR_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .as("Older databases that already ran V45 need delivered-item children reattached under the module")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:file-preview",
                        "market:delivered-item:mes-sync",
                        "market:delivered-item:wms-allocate",
                        "market:locked-item:view",
                        "parent_id = delivered_item.id"
                );
    }

    @Test
    void mesWmsFinalPermissionsAreCompleteAndModuleScoped() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("MES/WMS final governance permissions must all exist in the fallback catalog")
                .contains(
                        "market:delivered-item:view",
                        "market:delivered-item:customer-select",
                        "market:delivered-item:file-preview",
                        "market:delivered-item:mes-sync",
                        "market:delivered-item:wms-allocate",
                        "market:locked-item:view",
                        "market:locked-item:customer-select"
                );
        assertThat(source)
                .as("Delivered and locked items must stay as independent order MENU modules")
                .contains(
                        "permission(158L, \"MENU\", \"market:delivered-item:view\"",
                        "\"/delivered-items\"",
                        "\"DeliveredItemManage\"",
                        "permission(162L, \"MENU\", \"market:locked-item:view\"",
                        "\"/locked-items\"",
                        "\"LockedItemManage\""
                );
        assertThat(source)
                .as("MES/WMS delivered-item actions and file preview must hang under delivered items")
                .contains(
                        "permission(159L, \"API\", \"market:delivered-item:file-preview\"",
                        "127, 158L)",
                        "permission(163L, \"API\", \"market:delivered-item:customer-select\"",
                        "130, 158L)",
                        "permission(160L, \"BUTTON\", \"market:delivered-item:mes-sync\"",
                        "128, 158L)",
                        "permission(161L, \"BUTTON\", \"market:delivered-item:wms-allocate\"",
                        "129, 158L)",
                        "permission(164L, \"API\", \"market:locked-item:customer-select\"",
                        "128, 162L)"
                );
        assertThat(source)
                .as("No MES/WMS ability should be introduced as an assignable common permission")
                .doesNotContain("common:mes", "common:wms", "common:delivered-item", "common:locked-item");
    }

    @Test
    void deliveredAndLockedCustomerSelectionPermissionsStayModuleScoped() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        String migration = Files.readString(DELIVERY_COMMON_QUERY_MIGRATION, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .contains(
                        "market:delivered-item:customer-select",
                        "market:locked-item:customer-select"
                );
        assertThat(source)
                .contains(
                        "Map.entry(\"market:delivered-item:customer-select\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"market:locked-item:customer-select\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))"
                )
                .doesNotContain("common:customer:select");
        assertThat(migration)
                .contains(
                        "market:delivered-item:customer-select",
                        "market:locked-item:customer-select",
                        "market:delivered-item:view",
                        "market:locked-item:view",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "READONLY"
                );
    }

    @Test
    void qpoDocsNoLongerDescribeApsAsFutureTargetSystem() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DOCS_ROOT)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(this::isQpoGovernanceDoc)
                    .toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    for (String phrase : APS_TARGET_SYSTEM_PHRASES) {
                        if (line.contains(phrase)) {
                            violations.add(file + ":" + (i + 1) + " contains " + phrase);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("QPO docs may mention old APS compatibility, but must not describe APS as a future target system")
                .isEmpty();
    }

    @Test
    void deliveredItemFoundationMigrationKeepsPermissionTreeAligned() throws IOException {
        String migration = Files.readString(DELIVERED_ITEM_FOUNDATION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("market:delivered-item:view")
                .contains("market:delivered-item:file-preview")
                .contains("/delivered-items")
                .contains("DeliveredItemManage")
                .contains("SUPER_ADMIN")
                .contains("MARKET_USER")
                .contains("MARKET_MANAGER")
                .contains("READONLY");
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
                        "market:sales-order:customer-select",
                        "engineer:setup:order-select"
                );
        assertThat(source)
                .as("Old common customer/order selection permissions must be removed from fallback permissions")
                .doesNotContain("common:order:select", "common:customer:select");
        assertThat(source)
                .as("Unrelated compatibility permissions can remain until their call sites are migrated")
                .contains("common:file:preview", "common:task:view");
    }

    @Test
    void commonSelectionPermissionMigrationRemovesDatabaseCatalogEntries() throws IOException {
        String migration = Files.readString(RETIRE_COMMON_SELECTION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("common:customer:select")
                .contains("common:order:select")
                .contains("DELETE FROM system_role_permission")
                .contains("DELETE FROM system_permission")
                .doesNotContain("common:file:preview")
                .doesNotContain("common:task:view");
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

    @Test
    void pricingSheetExportPermissionBelongsToEngineerPricingModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Generating a pricing sheet is an engineering-side action, not a market quote export")
                .contains("engineer:pricing:export-sheet");
        assertThat(source)
                .as("Pricing sheet export permission must sit under the hidden engineer pricing module")
                .contains("permission(152L, \"BUTTON\", \"engineer:pricing:export-sheet\", \"生成核价单\", null, null, false, 225, 7L)");
        assertThat(source)
                .as("Engineering roles can generate pricing sheets; read-only users cannot")
                .contains("Map.entry(\"engineer:pricing:export-sheet\", Set.of(\"ENGINEER_USER\", \"ENGINEER_MANAGER\"))");
    }

    @Test
    void pricingSheetExportPermissionMigrationKeepsDatabaseCatalogAligned() throws IOException {
        String migration = Files.readString(PRICING_SHEET_EXPORT_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("engineer:pricing:view")
                .contains("engineer:pricing:export-sheet")
                .contains("生成核价单")
                .contains("ENGINEER_USER")
                .contains("ENGINEER_MANAGER")
                .contains("SUPER_ADMIN")
                .doesNotContain("READONLY");
    }

    @Test
    void releaseResetPermissionIsRetiredFromAssignableCatalog() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Delivery tickets and process sheets are irreversible release records; reset must not be assignable")
                .doesNotContain("market:release:reset");
        assertThat(source)
                .as("Default role mapping must not grant the retired release reset ability")
                .doesNotContain("Map.entry(\"market:release:reset\"");
    }

    @Test
    void orderStatusManagementPermissionsBelongToOrderModule() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(source)
                .as("The RFQ order module keeps compatibility codes while showing the inquiry-order label")
                .contains(
                        "permission(3L, \"MENU\", \"market:order:view\", \"询价订单\", \"/orders\", \"OrderManage\", true, 120)",
                        "permission(132L, \"API\", \"market:order:customer-select\", \"询价订单选择客户\", null, null, false, 123, 3L)",
                        "permission(133L, \"API\", \"market:order:file-preview\", \"询价订单预览文件\", null, null, false, 124, 3L)"
                )
                .doesNotContain("permission(3L, \"MENU\", \"market:order:view\", \"订单管理\"");
        assertThat(fallbackPermissions().stream().map(row -> row.code).toList())
                .as("Order status management actions need independent order-module permissions")
                .contains(
                        "market:order:rollback-quoted",
                        "market:order:rollback-priced",
                        "market:order:discard",
                        "market:order:restore"
                );
        assertThat(source)
                .as("Status action permissions must hang under the order management MENU permission")
                .contains(
                        "permission(153L, \"BUTTON\", \"market:order:rollback-quoted\", \"已报价退回已核价\", null, null, false, 128, 3L)",
                        "permission(154L, \"BUTTON\", \"market:order:rollback-priced\", \"已核价退回未核价\", null, null, false, 129, 3L)",
                        "permission(155L, \"BUTTON\", \"market:order:discard\", \"废弃订单\", null, null, false, 130, 3L)",
                        "permission(156L, \"BUTTON\", \"market:order:restore\", \"恢复废弃订单\", null, null, false, 131, 3L)"
                );
        assertThat(source)
                .as("Order status management should be manager-controlled by default")
                .contains(
                        "Map.entry(\"market:order:rollback-quoted\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:order:rollback-priced\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:order:discard\", Set.of(\"MARKET_MANAGER\"))",
                        "Map.entry(\"market:order:restore\", Set.of(\"MARKET_MANAGER\"))"
                );
    }

    @Test
    void inquiryOrderRenameMigrationKeepsDatabasePermissionCatalogAligned() throws IOException {
        String migration = Files.readString(INQUIRY_ORDER_RENAME_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("market:order:view")
                .contains("询价订单")
                .contains("market:order:customer-select")
                .contains("询价订单选择客户")
                .contains("market:order:file-preview")
                .contains("询价订单预览文件")
                .doesNotContain("market:history-order:view");
    }

    @Test
    void systemUserAndRolePermissionModulesUseCurrentBusinessNames() throws IOException {
        Map<String, String> namesByCode = new LinkedHashMap<>();
        for (PermissionRow row : fallbackPermissions()) {
            namesByCode.put(row.code, row.name);
        }
        String migration = Files.readString(SYSTEM_USER_ROLE_MODULE_RENAME_MIGRATION, StandardCharsets.UTF_8);

        assertThat(namesByCode)
                .containsEntry("system:user:view", "用户管理")
                .containsEntry("system:role:view", "角色权限");
        assertThat(namesByCode.values())
                .doesNotContain("用户权限", "角色管理");
        assertThat(migration)
                .contains("system:user:view")
                .contains("用户管理")
                .contains("system:role:view")
                .contains("角色权限");
    }

    @Test
    void organizationPermissionsFormAssignableModuleTree() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);

        assertThat(source)
                .as("Organization archive must be a visible left-sidebar MENU module")
                .contains("permission(147L, \"MENU\", \"system:org:view\"")
                .contains("\"/organization\"")
                .contains("\"SystemOrgManage\"");
        assertThat(source)
                .as("Department and post permissions must hang under the organization archive module")
                .contains("permission(148L, \"MENU\", \"system:dept:view\"")
                .contains("831, 147L")
                .contains("permission(149L, \"BUTTON\", \"system:dept:manage\"")
                .contains("832, 148L")
                .contains("permission(150L, \"MENU\", \"system:post:view\"")
                .contains("840, 147L")
                .contains("permission(151L, \"BUTTON\", \"system:post:manage\"")
                .contains("841, 150L");
    }

    @Test
    void organizationPermissionMigrationAlignsTreeAndDefaultRoleGrants() throws IOException {
        String migration = Files.readString(ORGANIZATION_PERMISSION_MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("system:org:view")
                .contains("system:dept:view")
                .contains("system:dept:manage")
                .contains("system:post:view")
                .contains("system:post:manage")
                .contains("system_role_permission")
                .contains("SUPER_ADMIN");
    }

    private List<PermissionRow> fallbackPermissions() throws IOException {
        String source = Files.readString(PERMISSION_SERVICE, StandardCharsets.UTF_8);
        Matcher matcher = FALLBACK_PERMISSION.matcher(source);
        List<PermissionRow> rows = new ArrayList<>();
        while (matcher.find()) {
            String pathToken = matcher.group(4);
            String path = "null".equals(pathToken) ? null : pathToken.substring(1, pathToken.length() - 1);
            rows.add(new PermissionRow(matcher.group(1), matcher.group(2), matcher.group(3), path));
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

    private boolean isQpoGovernanceDoc(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/QPO-MES-WMS")
                || normalized.contains("/QPO报价状态流转")
                || normalized.contains("/QPO权限用户体系")
                || normalized.contains("/QPO日志模块");
    }

    private record PermissionRow(String type, String code, String name, String path) {
    }
}
