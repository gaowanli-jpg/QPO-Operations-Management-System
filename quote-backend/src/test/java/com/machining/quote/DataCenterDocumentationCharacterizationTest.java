package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataCenterDocumentationCharacterizationTest {
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path CLEAR_ORDER_BUSINESS_DATA_SQL = Path.of(
            "..", "docs", "ops", "sql", "clear_order_business_data_before_u9_customer_cutover.sql");
    private static final Path WMS_REQUIREMENT_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "01-提单需求池与MES-WMS对接需求清单.md");
    private static final Path WMS_BASE_MODEL_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "02-基础数据模型与已提零件模块开发文档.md");
    private static final Path WMS_DEV_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path WMS_PERMISSION_LOG_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "05-权限日志运维与回归收口开发文档.md");
    private static final Path WMS_INTERFACE_DOC = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "07-提供给WMS的库存锁库接口对接说明.md");
    private static final Path DATA_CENTER_DOC = Path.of(
            "..", "docs", "QPO系统配置", "05-数据中心统一对接开发文档.md");
    private static final Path LEGACY_SYSTEM_CONFIG_DOC = Path.of(
            "..", "docs", "QPO系统配置", "03-系统对接配置开发文档.md");
    private static final Path PERMISSION_CATALOG_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md");
    private static final Path PERMISSION_ADMIN_DOC = Path.of(
            "..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md");
    private static final Path LOG_OPS_DOC = Path.of(
            "..", "docs", "QPO日志模块", "03-日志运维说明.md");
    private static final Path MAIN_FLOW_LOG_DOC = Path.of(
            "..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md");

    @Test
    void deploymentDocsRetainDataCenterWmsFallbackAndRemoveLegacyWmsDirectFallback() throws IOException {
        assertThat(read(ENV_EXAMPLE))
                .contains(
                        "ERP_DATA_CENTER_TOKEN_URL=http://192.168.70.250:8080/realms/api-platform/protocol/openid-connect/token",
                        "ERP_DATA_CENTER_CLIENT_ID=app_8ed28b2a1faf4a0b",
                        "ERP_DATA_CENTER_CLIENT_SECRET=",
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH=")
                .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig);

        assertThat(read(DATA_CENTER_DOC))
                .contains(
                        "QPO -> 数据中心 -> WMS",
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH",
                        "WMS_INVENTORY_ALLOCATE",
                        "WMS_SALE_LADING_RESERVE_V1",
                        "WMS_SALE_LADING_RESERVE_RESPONSE_V1")
                .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig);

        assertThat(read(PERMISSION_ADMIN_DOC))
                .contains(
                        "ERP_DATA_CENTER_WMS_ALLOCATION_PATH",
                        "WMS_INVENTORY_ALLOCATE",
                        "WMS_ALLOCATION_BATCH")
                .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig);
    }

    @Test
    void currentWmsDocsDescribeSaleLadingReserveBatchContractOnly() throws IOException {
        for (Path path : List.of(WMS_REQUIREMENT_DOC, WMS_DEV_DOC, WMS_PERMISSION_LOG_DOC, WMS_INTERFACE_DOC)) {
            assertThat(read(path))
                    .as("current WMS doc must describe the new reserve contract: %s", path)
                    .contains(
                            "WMS_ALLOCATION_BATCH",
                            "WMS_INVENTORY_ALLOCATE",
                            "/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve",
                            "externalBizId",
                            "saleNo",
                            "productCode",
                            "reserveCount",
                            "remark",
                            "inProductionCount",
                            "在产覆盖")
                    .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig)
                    .doesNotContain(
                            "WmsAllocationReq",
                            "WmsAllocationResp",
                            "WmsAllocationClient",
                            "WmsAllocationService",
                            "submitWmsAllocationTask",
                            "/api/wms/inventory/allocate")
                    .doesNotContainPattern("\\bWMS_ALLOCATION\\b");
        }
    }

    @Test
    void permissionAndLoggingDocsCloseBatchGovernanceWithoutSecrets() throws IOException {
        assertThat(read(PERMISSION_CATALOG_DOC))
                .contains(
                        "market:delivered-item:wms-allocate",
                        "WMS_ALLOCATION_BATCH",
                        "WMS_INVENTORY_ALLOCATE")
                .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig)
                .doesNotContainPattern("\\bWMS_ALLOCATION\\b")
                .doesNotContain("common:data-center");

        assertThat(read(LOG_OPS_DOC))
                .contains(
                        "DELIVERY_WMS_ALLOCATION_SUBMIT",
                        "DELIVERY_WMS_ALLOCATION_SUCCESS",
                        "DELIVERY_WMS_ALLOCATION_FAILED",
                        "DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN",
                        "DELIVERY_WMS_ALLOCATION_APPLY_FAILED",
                        "DELIVERY_WMS_ALLOCATION_RECONCILED",
                        "taskId",
                        "batchId",
                        "externalBizId",
                        "saleOrderNo",
                        "count",
                        "status",
                        "durationMs")
                .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig)
                .doesNotContainPattern("\\bWMS_ALLOCATION\\b");
    }

    @Test
    void supportingPlanningDocsDoNotDescribeRemovedWmsRuntimeAsCurrent() throws IOException {
        for (Path path : List.of(WMS_REQUIREMENT_DOC, WMS_BASE_MODEL_DOC, LEGACY_SYSTEM_CONFIG_DOC, MAIN_FLOW_LOG_DOC)) {
            assertThat(read(path))
                    .as("supporting planning doc must not reintroduce removed WMS runtime terms: %s", path)
                    .satisfies(DataCenterDocumentationCharacterizationTest::assertNoLegacyWmsDirectConfig)
                    .doesNotContain(
                            "WmsAllocationClient",
                            "WmsAllocationService",
                            "wms_production_order_no",
                            "wmsProductionOrderNo",
                            "/api/wms/inventory/allocate")
                    .doesNotContainPattern("\\bWMS_ALLOCATION\\b");
        }
    }

    @Test
    void cleanupSqlOnlyTreatsBatchWmsAllocationAsCurrentTaskType() throws IOException {
        String sql = read(CLEAR_ORDER_BUSINESS_DATA_SQL);

        assertThat(sql)
                .contains("'WMS_ALLOCATION_BATCH'")
                .doesNotContain("'WMS_ALLOCATION'");
    }

    @Test
    void documentationAndExamplesDoNotContainRealIntegrationSecrets() throws IOException {
        for (Path path : stage7Artifacts()) {
            String content = read(path);
            assertThat(content)
                    .as("secret scan: %s", path)
                    .doesNotContain(
                            "Authorization: Bearer " + "eyJ",
                            "access_token=" + "eyJ",
                            "ERP_DATA_CENTER_CLIENT_SECRET=" + "X",
                            "QPO_INBOUND_CALLBACK_TOKEN=",
                            "QPO_OPEN_API_TOKEN=")
                    .doesNotContainPattern("ERP_DATA_CENTER_CLIENT_SECRET=[A-Za-z0-9+/=_-]+")
                    .doesNotContainPattern("client_secret=[A-Za-z0-9+/=_-]{12,}");
        }
    }

    private static List<Path> stage7Artifacts() {
        return List.of(
                ENV_EXAMPLE,
                WMS_REQUIREMENT_DOC,
                WMS_DEV_DOC,
                WMS_PERMISSION_LOG_DOC,
                WMS_INTERFACE_DOC,
                DATA_CENTER_DOC,
                PERMISSION_CATALOG_DOC,
                PERMISSION_ADMIN_DOC,
                LOG_OPS_DOC,
                CLEAR_ORDER_BUSINESS_DATA_SQL
        );
    }

    private static void assertNoLegacyWmsDirectConfig(String content) {
        assertThat(content)
                .doesNotContain(
                        "ERP_WMS_BASE_URL",
                        "ERP_WMS_AUTH_TOKEN",
                        "ERP_WMS_ALLOCATION_PATH",
                        "ERP_WMS_TIMEOUT_MS",
                        "erp.wms");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
