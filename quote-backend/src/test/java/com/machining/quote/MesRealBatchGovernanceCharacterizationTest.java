package com.machining.quote;

import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MesRealBatchGovernanceCharacterizationTest {
    private static final String MATERIAL_PATH = "/api/v1/mes/openapi/material-preparation/create";
    private static final String PRODUCTION_PATH = "/api/v1/mes/openapi/demand/create";
    private static final String PHASE7_MARKER = "MES \u771f\u5b9e\u6279\u91cf\u9636\u6bb5 7 \u6536\u53e3";
    private static final String QPO_TO_DATA_CENTER_TO_MES = "QPO -> \u6570\u636e\u4e2d\u5fc3 -> MES";
    private static final String MES_TO_DATA_CENTER_TO_QPO = "MES -> \u6570\u636e\u4e2d\u5fc3 -> QPO";
    private static final Path APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");
    private static final Path PHASE7_MIGRATION = Path.of("src", "main", "resources", "db", "migration", "V7__mes_phase7_data_center_paths.sql");
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path ERP_ENV = Path.of("..", "erp.env");

    @Test
    void realMesDataCenterPathsAreConfiguredInRuntimeFallbacksComposeAndSeed() throws IOException {
        String yaml = read(APPLICATION_YAML);
        assertThat(yaml)
                .contains(
                        "read-timeout-ms: ${ERP_DATA_CENTER_READ_TIMEOUT_MS:60000}",
                        "mes-material-path: ${ERP_DATA_CENTER_MES_MATERIAL_PATH:" + MATERIAL_PATH + "}",
                        "mes-production-dispatch-path: ${ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH:" + PRODUCTION_PATH + "}",
                        "timeout-ms: ${ERP_MES_TIMEOUT_MS:60000}");

        DataCenterProperties defaults = new DataCenterProperties();
        assertThat(defaults.getReadTimeoutMs()).isEqualTo(60000);
        assertThat(defaults.getMesMaterialPath()).isEqualTo(MATERIAL_PATH);
        assertThat(defaults.getMesProductionDispatchPath()).isEqualTo(PRODUCTION_PATH);

        String compose = read(DOCKER_COMPOSE);
        assertThat(compose)
                .contains(
                        "ERP_DATA_CENTER_ENABLED: ${ERP_DATA_CENTER_ENABLED:-true}",
                        "ERP_DATA_CENTER_READ_TIMEOUT_MS: ${ERP_DATA_CENTER_READ_TIMEOUT_MS:-60000}",
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH: ${ERP_DATA_CENTER_MES_MATERIAL_PATH:-" + MATERIAL_PATH + "}",
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH: ${ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH:-" + PRODUCTION_PATH + "}",
                        "ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-60000}")
                .doesNotContain("ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}");

        String envExample = read(ENV_EXAMPLE);
        assertThat(envExample)
                .contains(
                        "ERP_DATA_CENTER_READ_TIMEOUT_MS=60000",
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH=" + MATERIAL_PATH,
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH=" + PRODUCTION_PATH,
                        "ERP_MES_TIMEOUT_MS=60000")
                .doesNotContain(
                        "ERP_DATA_CENTER_MES_MATERIAL_PATH=\n",
                        "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH=\n",
                        "ERP_MES_TIMEOUT_MS=10000");

        if (Files.exists(ERP_ENV)) {
            String env = read(ERP_ENV);
            assertThat(env)
                    .contains(
                            "ERP_DATA_CENTER_MES_MATERIAL_PATH=" + MATERIAL_PATH,
                            "ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH=" + PRODUCTION_PATH,
                            "ERP_DATA_CENTER_READ_TIMEOUT_MS=60000",
                            "ERP_MES_TIMEOUT_MS=60000");
        }

        String migration = read(PHASE7_MIGRATION);
        assertThat(migration)
                .contains(
                        "'MES_MATERIAL_RESOLVE'",
                        "'" + MATERIAL_PATH + "'",
                        "'MES_PRODUCTION_DISPATCH'",
                        "'" + PRODUCTION_PATH + "'",
                        "GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000)",
                        "ON CONFLICT (endpoint_code) DO UPDATE");
    }

    @Test
    void docsCloseMesPhase7GovernanceWithoutNewUserPermissionsOrSecretExamples() throws IOException {
        for (Path path : phase7Docs()) {
            String doc = read(path);
            assertThat(doc)
                    .as("phase 7 governance doc: %s", path)
                    .contains(
                            PHASE7_MARKER,
                            QPO_TO_DATA_CENTER_TO_MES,
                            MES_TO_DATA_CENTER_TO_QPO,
                            MATERIAL_PATH,
                            PRODUCTION_PATH,
                            "/api/integrations/mes/material-callback",
                            "market:delivered-item:mes-sync",
                            "market:production-item:mes-production-submit",
                            "MES_INTEGRATION_BATCH_RECONCILE")
                    .doesNotContain("common:mes", "common:data-center", "common:integration");
        }
    }

    @Test
    void sensitiveInformationScanCoversPhase7Artifacts() throws IOException {
        for (Path path : phase7Docs()) {
            String content = read(path);
            assertThat(content)
                    .as("secret scan: %s", path)
                    .doesNotContain("Authorization: Bearer " + "eyJ", "access_token=" + "eyJ")
                    .doesNotContainPattern("(?i)(client_secret|access_token|qpo_inbound_callback_token|erp_mes_callback_token)=[A-Za-z0-9+/=_-]{12,}")
                    .doesNotContainPattern("Authorization: Bearer (?!\\{QPO_OPEN_API_TOKEN\\})[A-Za-z0-9._~+/=-]{12,}");
        }
    }

    @Test
    void mesBatchReconcileActionHasStableDeliveryAuditLabel() {
        assertThat(AuditService.inferModule("MES_INTEGRATION_BATCH_RECONCILE")).isEqualTo("DELIVERY");
        assertThat(AuditService.labelForAction("MES_INTEGRATION_BATCH_RECONCILE"))
                .isEqualTo("MES批次结果核对");
    }

    private static List<Path> phase7Docs() {
        return List.of(
                Path.of("..", "docs", "QPO-MES-WMS对接", "03-MES物料号同步开发文档.md"),
                Path.of("..", "docs", "QPO-MES-WMS对接", "05-权限日志运维与回归收口开发文档.md"),
                Path.of("..", "docs", "QPO-MES-WMS对接", "09-提供给MES的待生产下发接口对接说明.md"),
                Path.of("..", "docs", "QPO报价状态流转", "18-MES物料号异步回调开发文档.md"),
                Path.of("..", "docs", "QPO报价状态流转", "22-锁库待生产MES生产下发与筛选区优化开发文档.md"),
                Path.of("..", "docs", "QPO系统配置", "05-数据中心统一对接开发文档.md"),
                Path.of("..", "docs", "QPO权限用户体系", "02-权限点清单与现有角色映射.md"),
                Path.of("..", "docs", "QPO权限用户体系", "03-权限体系部署与管理员使用说明.md"),
                Path.of("..", "docs", "QPO日志模块", "03-日志运维说明.md"),
                Path.of("..", "docs", "QPO日志模块", "04-主流程文件日志规范化开发文档.md")
        );
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

}
