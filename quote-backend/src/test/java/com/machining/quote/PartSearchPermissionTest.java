package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartSearchPermissionTest {
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "PartSearchController.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PartSearchService.java");
    private static final Path DATA_SCOPE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "DataScopeService.java");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path SYSTEM_ROLE_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SystemRoleService.java");
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V37__part_search_permission.sql");
    private static final Path ADMIN_GRANT_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V39__part_search_super_admin_permission.sql");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md");

    @Test
    void controllerProtectsEveryPartSearchEndpointWithIndependentViewPermission() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@RequestMapping(\"/api/market/part-search\")",
                        "@GetMapping(\"/rfq-items\")",
                        "@GetMapping(\"/rfq-items/{itemId}\")",
                        "@GetMapping(\"/sales-items\")",
                        "@GetMapping(\"/sales-items/{itemId}\")")
                .contains("@RequirePermission(\"market:part-search:view\")")
                .doesNotContain(
                        "market:order:view",
                        "market:sales-order:view",
                        "@PostMapping",
                        "@PutMapping",
                        "@DeleteMapping");
    }

    @Test
    void serviceUsesParentOrderDataScopesForBothPartSources() throws IOException {
        assertThat(read(SERVICE))
                .contains(
                        "dataScopeService.applyRfqScope(countQuery, \"rfq_id\")",
                        "dataScopeService.applyRfqScope(pageQuery, \"rfq_id\")",
                        "dataScopeService.applySalesOrderIdScope(countQuery, \"sales_order_id\")",
                        "dataScopeService.applySalesOrderIdScope(pageQuery, \"sales_order_id\")",
                        "dataScopeService.assertCanAccessOrder(order)",
                        "dataScopeService.assertCanAccessSalesOrder(order)");
        assertThat(read(DATA_SCOPE_SERVICE))
                .contains("applySalesOrderIdScope")
                .contains("sales_order_header so")
                .contains("currentOrderScopeSql(\"so\")");
    }

    @Test
    void migrationAndFallbackCatalogExposePartSearchToReadOnlyOrderUsersAndAdmins() throws IOException {
        assertThat(read(MIGRATION))
                .contains(
                        "market:part-search:view",
                        "零件查询",
                        "/part-search",
                        "PartSearchManage",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "READONLY")
                .doesNotContain("V1__qpo_baseline_schema", "V2__qpo_baseline_seed_data");
        assertThat(read(PERMISSION_SERVICE))
                .contains(
                        "permission(214L, \"MENU\", \"market:part-search:view\"",
                        "\"/part-search\"",
                        "\"PartSearchManage\"",
                        "Map.entry(\"market:part-search:view\", Set.of(\"SUPER_ADMIN\", \"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))");
        assertThat(read(ADMIN_GRANT_MIGRATION))
                .contains(
                        "market:part-search:view",
                        "SUPER_ADMIN",
                        "ON CONFLICT (role_id, permission_id) DO NOTHING")
                .doesNotContain("V1__qpo_baseline_schema", "V2__qpo_baseline_seed_data");
        assertThat(read(SYSTEM_ROLE_SERVICE))
                .contains("permissionCode.startsWith(\"market:part-search:\")");
    }

    @Test
    void governanceDocsDocumentReadOnlyPermissionAndNoAuditNoise() throws IOException {
        assertThat(read(PERMISSION_DOC))
                .contains("market:part-search:view")
                .contains("零件查询")
                .contains("/part-search")
                .contains("PartSearchController.java");
        assertThat(read(LOG_DOC))
                .contains("零件查询")
                .contains("只读")
                .contains("不写操作日志")
                .contains("不新增高频业务 INFO");
    }

    private String read(Path path) throws IOException {
        assertThat(path).as("%s should exist", path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
