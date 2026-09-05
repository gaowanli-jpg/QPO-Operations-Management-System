package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class U9CustomerMasterGovernanceCharacterizationTest {
    private static final Path CUSTOMER_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "12-客户主数据模块开发文档.md");
    private static final Path U9_SYNC_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "42-销售订单U9同步模块重构开发文档.md");
    private static final Path U9_CUSTOMER_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "43-U9客户主数据与U9同步选项缓存开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path FILE_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");
    private static final Path CLEANUP_SCRIPT = Path.of(
            "..", "docs", "ops", "sql", "clear_order_business_data_before_u9_customer_cutover.sql");

    @Test
    void stageEightDocsCloseCustomerAndU9SyncGovernanceBoundary() throws IOException {
        String customerDoc = read(CUSTOMER_DOC);
        String u9SyncDoc = read(U9_SYNC_DOC);
        String u9CustomerDoc = read(U9_CUSTOMER_DOC);

        assertThat(customerDoc)
                .contains(
                        "阶段 8 权限、日志、文档和全量回归收口",
                        "客户只来自 U9 客户缓存",
                        "不存在可用客户本地新增、编辑、删除接口",
                        "不存在独立销售部门/销售员管理模块",
                        "清库脚本文档和部署顺序");

        assertThat(u9SyncDoc)
                .contains(
                        "第 43 号文档已承接 U9 客户只读缓存和 U9 同步选项持久化缓存",
                        "Seller / SaleDepartment 选项来自本地持久化缓存",
                        "同步提交前校验客户、Seller、SaleDepartment 的当前有效状态")
                .doesNotContain("不建设 U9 客户资料同步模块")
                .doesNotContain("不把销售员或部门复制到本地全量主数据表");

        assertThat(u9CustomerDoc)
                .contains(
                        "设计状态：整体开发目标已完成并验收。",
                        "2026-08-11 阶段 8 已完成",
                        "mvnw.cmd test",
                        "npm test",
                        "npm run build");
        assertThat(finalChecklist(u9CustomerDoc)).doesNotContain("- [ ]");
    }

    @Test
    void permissionAndLoggingDocsDescribeFinalBoundariesWithoutNewCommonPermissions() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String permissionAdminDoc = read(PERMISSION_ADMIN_DOC);
        String logOpsDoc = read(LOG_OPS_DOC);
        String fileLogDoc = read(FILE_LOG_DOC);

        assertThat(permissionDoc)
                .contains(
                        "U9 客户主数据与同步选项缓存阶段 8 收口",
                        "后端权限仍是最终边界",
                        "不存在独立销售部门/销售员管理模块",
                        "刷新 Seller / SaleDepartment 选项缓存继续复用 `market:u9-sync:submit`")
                .doesNotContain("common:u9-customer", "common:u9-master-data", "common:seller", "common:department");

        assertThat(permissionAdminDoc)
                .contains(
                        "客户资料读取和同步 U9 客户缓存",
                        "仅允许通过 `system:customer:supplier-code-update` 维护 QPO 本地字段 `supplierCode`",
                        "不要寻找或新建销售部门/销售员管理菜单",
                        "刷新 U9 选项缓存、保存 Seller / SaleDepartment、同步和核对均复用 `market:u9-sync:submit`",
                        "后端权限和客户编码数据范围是最终边界");

        assertThat(logOpsDoc)
                .contains(
                        "U9 客户主数据与同步选项缓存阶段 8 收口",
                        "普通主数据查询不产生操作日志噪声",
                        "写操作只记录安全摘要",
                        "敏感信息扫描测试覆盖 U9 客户同步、U9 选项刷新和销售订单 U9 同步");

        assertThat(fileLogDoc)
                .contains(
                        "U9 customer master sync started syncRunId={}",
                        "U9 sync options refresh completed syncRunId={}",
                        "不得记录完整 U9 请求体、完整 U9 响应体、token、Authorization header、Cookie、client secret 或 U9 Data.Headers");
    }

    @Test
    void cleanupScriptAndDeploymentOrderAreDocumentedForCutover() throws IOException {
        String customerDoc = read(CUSTOMER_DOC);
        String u9CustomerDoc = read(U9_CUSTOMER_DOC);
        String permissionAdminDoc = read(PERMISSION_ADMIN_DOC);
        String cleanupScript = read(CLEANUP_SCRIPT);

        assertThat(cleanupScript)
                .contains(
                        "SET qpo.confirm_clear_order_business_data = 'YES'",
                        "TRUNCATE TABLE",
                        "sales_order_header",
                        "order_rfq")
                .doesNotContain("customer_master", "u9_sync_seller_option_cache", "u9_sync_department_option_cache");

        assertThat(customerDoc + u9CustomerDoc + permissionAdminDoc)
                .contains(
                        "先备份数据库",
                        "执行 `docs/ops/sql/clear_order_business_data_before_u9_customer_cutover.sql`",
                        "部署后端并执行 Flyway V6/V7",
                        "同步 U9 客户缓存和 U9 同步选项缓存",
                        "按 U9 客户编码重新配置角色数据范围",
                        "再执行全量回归");
    }

    @Test
    void governanceArtifactsPassSensitiveInformationScan() throws IOException {
        for (Path artifact : governanceArtifacts()) {
            String content = read(artifact);
            assertThat(content)
                    .as("sensitive information scan: %s", artifact)
                    .doesNotContain("Authorization: Bearer " + "eyJ")
                    .doesNotContainPattern("(?i)(access_token|refresh_token)\\s*[:=]\\s*[A-Za-z0-9._\\-+/=]{20,}")
                    .doesNotContainPattern("(?i)(client_secret|password)\\s*[:=]\\s*(?!\\{|\\$\\{)[A-Za-z0-9._\\-+/=]{12,}")
                    .doesNotContainPattern("(?i)Cookie\\s*[:=]\\s*[A-Za-z0-9._\\-+/=]{20,}");
        }
    }

    private static List<Path> governanceArtifacts() {
        return List.of(
                CUSTOMER_DOC,
                U9_SYNC_DOC,
                U9_CUSTOMER_DOC,
                PERMISSION_DOC,
                PERMISSION_ADMIN_DOC,
                LOG_OPS_DOC,
                FILE_LOG_DOC,
                CLEANUP_SCRIPT,
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "u9", "U9CommonEntityClient.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "u9", "U9SalesOrderClient.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "u9", "U9CustomerSyncScheduleConfigService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "u9", "U9MasterDataSyncService.java"),
                Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderU9SyncService.java")
        );
    }

    private static String finalChecklist(String doc) {
        return doc.substring(doc.indexOf("## 16. 最终验收清单"), doc.indexOf("## 17. 风险与处理"));
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
