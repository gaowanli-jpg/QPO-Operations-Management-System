package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WmsAllocationClientTest {
    private static final Path WMS_PLAN = Path.of(
            "..", "docs", "QPO-MES-WMS对接", "04-WMS锁库分配与锁库零件模块开发文档.md");
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path ERP_ENV = Path.of("..", "erp.env");

    @Test
    void wmsClientRequestContractCarriesMaterialAndDemandIdentity() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("QPO must send stable delivered-ticket identity and demand fields to WMS")
                .contains(
                        "POST {ERP_WMS_BASE_URL}{ERP_WMS_ALLOCATION_PATH}",
                        "Authorization: Bearer {ERP_WMS_AUTH_TOKEN}",
                        "\"ticketId\"",
                        "\"rfqId\"",
                        "\"rfqNo\"",
                        "\"customerName\"",
                        "\"materialNo\"",
                        "\"partNo\"",
                        "\"version\"",
                        "\"internalProjectNo\"",
                        "\"demandQty\""
                );
    }

    @Test
    void wmsClientResponseContractSupportsDynamicProductionOrders() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("WMS may return any number of production-order locks")
                .contains(
                        "\"inStockLockedQty\"",
                        "\"inProductionLocks\"",
                        "\"productionOrderNo\"",
                        "\"lockedQty\"",
                        "\"externalTraceId\"",
                        "success=true",
                        "success=false",
                        "materialNo",
                        "material_no"
                );
    }

    @Test
    void wmsClientConfigurationAndFailureRulesAreFrozen() throws IOException {
        String plan = read(WMS_PLAN);

        assertThat(plan)
                .as("The future WMS client must be configurable, token-safe, and testable without a real WMS")
                .contains(
                        "ERP_WMS_BASE_URL",
                        "ERP_WMS_AUTH_TOKEN",
                        "ERP_WMS_ALLOCATION_PATH",
                        "ERP_WMS_TIMEOUT_MS",
                        "Bearer",
                        "success=false",
                        "HTTP",
                        "token"
                );
    }

    @Test
    void wmsConfigurationIsExposedToSpringFromComposeAndEnv() throws IOException {
        String compose = read(DOCKER_COMPOSE);
        String env = read(ERP_ENV);

        assertThat(compose)
                .as("docker compose must pass WMS configuration through to Spring")
                .contains(
                        "ERP_WMS_BASE_URL: ${ERP_WMS_BASE_URL:-}",
                        "ERP_WMS_AUTH_TOKEN: ${ERP_WMS_AUTH_TOKEN:-}",
                        "ERP_WMS_ALLOCATION_PATH: ${ERP_WMS_ALLOCATION_PATH:-/api/wms/inventory/allocate}",
                        "ERP_WMS_TIMEOUT_MS: ${ERP_WMS_TIMEOUT_MS:-10000}"
                );
        assertThat(env)
                .as("erp.env must document the WMS configuration contract")
                .contains(
                        "ERP_WMS_BASE_URL=",
                        "ERP_WMS_AUTH_TOKEN=",
                        "ERP_WMS_ALLOCATION_PATH=/api/wms/inventory/allocate",
                        "ERP_WMS_TIMEOUT_MS=10000"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
