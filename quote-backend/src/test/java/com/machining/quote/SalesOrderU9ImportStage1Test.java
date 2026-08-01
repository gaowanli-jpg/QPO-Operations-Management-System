package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderU9ImportStage1Test {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V60__sales_order_u9_import.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path SALES_ORDER_CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderImportController.java");
    private static final Path APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path ADMIN_DOC = Path.of("..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");

    @Test
    void migrationCreatesSalesOrderImportTablesAndRfqChildPermission() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS sales_order_import_batch")
                .contains("CREATE TABLE IF NOT EXISTS sales_order_import_record")
                .contains("'market:rfq:view', 'BUTTON', 'market:rfq:import-sales-order'")
                .contains("'导入销售订单'")
                .contains("SUPER_ADMIN")
                .contains("MARKET_USER")
                .contains("MARKET_MANAGER")
                .doesNotContain("MENU', 'market:sales-order");
    }

    @Test
    void fallbackCatalogKeepsSalesOrderImportUnderNewRfqImportModule() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains("permission(169L, \"BUTTON\", \"market:rfq:import-sales-order\", \"导入销售订单\", null, null, false, 117, 1L)")
                .contains("Map.entry(\"market:rfq:import-sales-order\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))")
                .contains("\"market:sales-order:view\"")
                .contains("\"/sales-orders\"");
    }

    @Test
    void backendEndpointSkeletonIsGuardedByIndependentSalesOrderImportPermission() throws IOException {
        String controller = read(SALES_ORDER_CONTROLLER);

        assertThat(controller)
                .contains("@RequestMapping(\"/api/market/sales-orders\")")
                .contains("@PostMapping(\"/import\")")
                .contains("@RequirePermission(\"market:rfq:import-sales-order\")")
                .doesNotContain("@RequirePermission(\"market:rfq:import\")");
    }

    @Test
    void u9ConfigurationIsEnvironmentBackedAndDoesNotDefaultSecrets() throws IOException {
        String yaml = read(APPLICATION_YAML);

        assertThat(yaml)
                .contains("u9:")
                .contains("base-url: ${ERP_U9_BASE_URL:}")
                .contains("user-code: ${ERP_U9_USER_CODE:}")
                .contains("ent-code: ${ERP_U9_ENT_CODE:}")
                .contains("org-code: ${ERP_U9_ORG_CODE:}")
                .contains("client-id: ${ERP_U9_CLIENT_ID:}")
                .contains("client-secret: ${ERP_U9_CLIENT_SECRET:}")
                .contains("connect-timeout-ms: ${ERP_U9_CONNECT_TIMEOUT_MS:5000}")
                .contains("read-timeout-ms: ${ERP_U9_READ_TIMEOUT_MS:30000}")
                .doesNotContain("ERP_U9_CLIENT_SECRET:change")
                .doesNotContain("ERP_U9_CLIENT_SECRET:demo");
    }

    @Test
    void deploymentConfigExposesU9EnvironmentVariablesWithoutSecretDefaults() throws IOException {
        String envExample = read(ENV_EXAMPLE);
        String compose = read(DOCKER_COMPOSE);

        assertThat(envExample)
                .contains("ERP_U9_BASE_URL=")
                .contains("ERP_U9_USER_CODE=")
                .contains("ERP_U9_ENT_CODE=")
                .contains("ERP_U9_ORG_CODE=")
                .contains("ERP_U9_CLIENT_ID=")
                .contains("ERP_U9_CLIENT_SECRET=")
                .contains("ERP_U9_CONNECT_TIMEOUT_MS=5000")
                .contains("ERP_U9_READ_TIMEOUT_MS=30000")
                .doesNotContain("ERP_U9_CLIENT_SECRET=change")
                .doesNotContain("ERP_U9_CLIENT_SECRET=demo");

        assertThat(compose)
                .contains("ERP_U9_BASE_URL: ${ERP_U9_BASE_URL:-}")
                .contains("ERP_U9_USER_CODE: ${ERP_U9_USER_CODE:-}")
                .contains("ERP_U9_ENT_CODE: ${ERP_U9_ENT_CODE:-}")
                .contains("ERP_U9_ORG_CODE: ${ERP_U9_ORG_CODE:-}")
                .contains("ERP_U9_CLIENT_ID: ${ERP_U9_CLIENT_ID:-}")
                .contains("ERP_U9_CLIENT_SECRET: ${ERP_U9_CLIENT_SECRET:-}")
                .contains("ERP_U9_CONNECT_TIMEOUT_MS: ${ERP_U9_CONNECT_TIMEOUT_MS:-5000}")
                .contains("ERP_U9_READ_TIMEOUT_MS: ${ERP_U9_READ_TIMEOUT_MS:-30000}");
    }

    @Test
    void permissionDocsDescribeSalesOrderImportAsNewRfqChildAction() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String adminDoc = read(ADMIN_DOC);

        assertThat(permissionDoc)
                .contains("`market:rfq:import-sales-order`")
                .contains("导入销售订单")
                .contains("挂在 `market:rfq:view` 下")
                .contains("销售订单导入、模板下载仍沿用 `market:rfq:import-sales-order`")
                .contains("销售订单管理入口位于订单分组");
        assertThat(adminDoc)
                .contains("`market:rfq:import-sales-order`")
                .contains("销售订单");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
