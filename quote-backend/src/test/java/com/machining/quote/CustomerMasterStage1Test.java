package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMasterStage1Test {
    private static final Path SCHEMA_BASELINE = Path.of("src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");
    private static final Path SEED_BASELINE = Path.of("src", "main", "resources", "db", "migration", "V2__qpo_baseline_seed_data.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md");

    @Test
    void baselineCreatesCustomerMasterWithoutOperationalCustomerSeed() throws IOException {
        String schema = read(SCHEMA_BASELINE);
        String seed = read(SEED_BASELINE);

        assertThat(schema)
                .contains("CREATE TABLE customer_master")
                .contains("customer_code character varying(200) NOT NULL")
                .contains("customer_short_name character varying(200) NOT NULL")
                .contains("customer_name character varying(200) NOT NULL")
                .contains("uk_customer_master_code")
                .contains("uk_customer_master_short_name")
                .contains("uk_customer_master_name");
        assertThat(seed)
                .doesNotContain("INSERT INTO customer_master")
                .doesNotContain("FROM order_rfq");
    }

    @Test
    void migrationAddsSystemCustomerPermissionsAndDefaultRoleGrants() throws IOException {
        String seed = read(SEED_BASELINE);

        assertThat(seed)
                .contains("'system:customer:view'")
                .contains("'system:customer:create'")
                .contains("'system:customer:update'")
                .contains("'system:customer:delete'")
                .contains("'客户资料'")
                .contains("'新增客户'")
                .contains("'编辑客户'")
                .contains("'删除客户'")
                .contains("SUPER_ADMIN")
                .contains("MARKET_MANAGER")
                .doesNotContain("common:customer");
    }

    @Test
    void fallbackCatalogContainsCustomerMasterPermissions() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains("\"system:customer:view\"")
                .contains("\"system:customer:create\"")
                .contains("\"system:customer:update\"")
                .contains("\"system:customer:delete\"")
                .contains("\"CustomerManage\"")
                .contains("Map.entry(\"system:customer:view\"")
                .contains("Map.entry(\"system:customer:create\"")
                .contains("Map.entry(\"system:customer:update\"")
                .contains("Map.entry(\"system:customer:delete\"");
    }

    @Test
    void auditLabelsAndDocsCoverCustomerMasterGovernance() throws IOException {
        assertThat(AuditService.labelForAction("CUSTOMER_MASTER_CREATE")).isEqualTo("新增客户资料");
        assertThat(AuditService.labelForAction("CUSTOMER_MASTER_UPDATE")).isEqualTo("更新客户资料");
        assertThat(AuditService.labelForAction("CUSTOMER_MASTER_DELETE")).isEqualTo("删除客户资料");
        assertThat(AuditService.inferModule("CUSTOMER_MASTER_CREATE")).isEqualTo("SYSTEM");

        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);

        assertThat(permissionDoc)
                .contains("`system:customer:view`")
                .contains("`system:customer:create`")
                .contains("`system:customer:update`")
                .contains("`system:customer:delete`")
                .contains("客户资料");
        assertThat(logDoc)
                .contains("CUSTOMER_MASTER_CREATE")
                .contains("CUSTOMER_MASTER_UPDATE")
                .contains("CUSTOMER_MASTER_DELETE")
                .contains("customer_master");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
