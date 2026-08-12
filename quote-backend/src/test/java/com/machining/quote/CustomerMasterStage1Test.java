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
                .contains("uk_customer_master_code");
        assertThat(seed)
                .doesNotContain("INSERT INTO customer_master")
                .doesNotContain("FROM order_rfq");
    }

    @Test
    void baselineKeepsCustomerViewAddsManualSyncAndRetiresLocalWritePermissions() throws IOException {
        String seed = read(SEED_BASELINE);

        assertThat(seed)
                .contains("'system:customer:view'")
                .contains("'system:customer:sync'")
                .contains("'system:customer:create'")
                .contains("'system:customer:update'")
                .contains("'system:customer:delete'")
                .contains("'system:customer:create',", "false, false")
                .doesNotContain("common:customer");
    }

    @Test
    void fallbackCatalogContainsReadOnlyCustomerMasterAndSyncPermission() throws IOException {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains("\"system:customer:view\"")
                .contains("\"system:customer:sync\"")
                .contains("\"CustomerManage\"")
                .contains("Map.entry(\"system:customer:view\"")
                .contains("Map.entry(\"system:customer:sync\", Set.of())")
                .doesNotContain("Map.entry(\"system:customer:create\"")
                .doesNotContain("Map.entry(\"system:customer:update\"")
                .doesNotContain("Map.entry(\"system:customer:delete\"");
    }

    @Test
    void auditLabelsAndDocsCoverU9CustomerSyncGovernance() throws IOException {
        assertThat(AuditService.labelForAction("U9_CUSTOMER_SYNC")).isEqualTo("同步 U9 客户主数据");
        assertThat(AuditService.inferModule("U9_CUSTOMER_SYNC")).isEqualTo("SYSTEM");

        String permissionDoc = read(PERMISSION_DOC);
        String logDoc = read(LOG_DOC);

        assertThat(permissionDoc)
                .contains("`system:customer:view`")
                .contains("`system:customer:sync`")
                .contains("system:customer:create/update/delete` 已下线")
                .doesNotContain("控制客户资料写操作");
        assertThat(logDoc)
                .contains("U9_CUSTOMER_SYNC")
                .contains("u9_master_data_sync_run")
                .contains("普通客户资料分页查询不写操作日志")
                .doesNotContain("CUSTOMER_MASTER_CREATE`、`CUSTOMER_MASTER_UPDATE`、`CUSTOMER_MASTER_DELETE`");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
