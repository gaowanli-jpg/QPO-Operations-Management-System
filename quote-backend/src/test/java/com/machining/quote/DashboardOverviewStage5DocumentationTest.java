package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardOverviewStage5DocumentationTest {
    private static final Path DASHBOARD_REQUIREMENT_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "28-首页看板模块需求文档.md");
    private static final Path DASHBOARD_PROTOTYPE_DOC = Path.of(
            "..", "docs", "QPO报价状态流转", "29-首页看板前端原型开发文档.md");
    private static final Path PERMISSION_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path DASHBOARD_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "DashboardController.java");
    private static final Path DASHBOARD_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DashboardOverviewService.java");

    @Test
    void dashboardRequirementAndPrototypeDocsDescribeRealReadOnlyApiBoundary() throws IOException {
        String requirementDoc = read(DASHBOARD_REQUIREMENT_DOC);
        String prototypeDoc = read(DASHBOARD_PROTOTYPE_DOC);
        String dashboardDocs = requirementDoc + "\n" + prototypeDoc;

        assertThat(requirementDoc)
                .contains("GET /api/dashboard/overview")
                .contains("RFQ 饼图只统计 `未核价 / 已核价 / 已报价`")
                .contains("销售订单饼图只统计 `已报价`")
                .contains("客户交叉表不展示 `部分已提单 / 已生成提单 / 废弃`")
                .contains("功能权限")
                .contains("数据权限");
        assertThat(prototypeDoc)
                .contains("mock 已退为测试 fixture")
                .contains("生产页面使用 `GET /api/dashboard/overview`")
                .contains("不再从 mock 数据直接初始化生产页面")
                .contains("loading / error / empty")
                .contains("不调用 MES / WMS / U9 / 生产下发写操作 API");
        assertThat(dashboardDocs)
                .doesNotContain("client_secret")
                .doesNotContain("access_token")
                .doesNotContain("Authorization: Bearer")
                .doesNotContain("192.168.70.250")
                .doesNotContain("X7v6KHDLuaxTvOs7KsMlJrpJ5O2d9hP7");
    }

    @Test
    void permissionAndLoggingDocsKeepDashboardAsDefaultReadOnlyEntry() throws IOException {
        String permissionDoc = read(PERMISSION_DOC);
        String logOpsDoc = read(LOG_OPS_DOC);

        assertThat(permissionDoc)
                .contains("首页看板")
                .contains("不新增 `dashboard:view`")
                .contains("登录默认页")
                .contains("目标模块权限")
                .contains("market:order:view")
                .contains("market:sales-order:view")
                .contains("market:delivered-item:view")
                .contains("market:locked-item:view")
                .contains("market:u9-sync:view");
        assertThat(logOpsDoc)
                .contains("首页看板")
                .contains("普通读取")
                .contains("不写操作日志")
                .contains("不新增高频文件日志")
                .contains("GET /api/dashboard/overview");
    }

    @Test
    void dashboardOverviewEndpointDoesNotAddMenuPermissionOrOperationAuditNoise() throws IOException {
        String controller = read(DASHBOARD_CONTROLLER);
        String implementation = controller + "\n" + read(DASHBOARD_SERVICE);

        assertThat(controller)
                .contains("@GetMapping(\"/overview\")")
                .doesNotContain("@RequirePermission");
        assertThat(implementation)
                .doesNotContain("AuditService")
                .doesNotContain("auditService")
                .doesNotContain("OperationLog")
                .doesNotContain("recordOperation");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
