package com.machining.quote;

import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMasterStage1Test {
    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V61__customer_master.sql");
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path PERMISSION_DOC = Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_DOC = Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md");

    @Test
    void migrationCreatesCustomerMasterAndInitializesHistoricalCustomers() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS customer_master")
                .contains("customer_code VARCHAR(200) NOT NULL")
                .contains("customer_short_name VARCHAR(200) NOT NULL")
                .contains("customer_name VARCHAR(200) NOT NULL")
                .contains("uk_customer_master_code")
                .contains("uk_customer_master_short_name")
                .contains("uk_customer_master_name")
                .contains("INSERT INTO customer_master")
                .contains("SELECT DISTINCT TRIM(customer_name)")
                .contains("FROM order_rfq")
                .contains("ON CONFLICT DO NOTHING");
    }

    @Test
    void migrationAddsSystemCustomerPermissionsAndDefaultRoleGrants() throws IOException {
        String migration = read(MIGRATION);

        assertThat(migration)
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
