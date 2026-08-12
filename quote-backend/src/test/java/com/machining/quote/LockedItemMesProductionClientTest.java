package com.machining.quote;

import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.mes.MesProductionProcessRouteDTO;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockedItemMesProductionClientTest {
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path ERP_ENV = Path.of("..", "erp.env");

    @Test
    void dispatchPostsLockedItemSnapshotToConfiguredMesPath() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, """
                    {
                      "success": true,
                      "requestId": "REQ-1",
                      "externalId": "MES-PROD-1",
                      "message": "accepted"
                    }
                    """);
        });
        try {
            MesProductionDispatchClient client = client(baseUrl(server), "", "production/submit", 1000);

            MesProductionDispatchResp response = client.dispatch(request());

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getExternalId()).isEqualTo("MES-PROD-1");
            assertThat(captured.method.get()).isEqualTo("POST");
            assertThat(captured.path.get()).isEqualTo("/production/submit");
            assertThat(captured.authorization.get()).isNull();
            assertThat(captured.contentType.get()).contains("application/json");
            assertThat(captured.body.get())
                    .contains("\"bizId\":\"REQ-1\"")
                    .contains("\"customerName\":\"ACME\"")
                    .contains("\"internalProjectNo\":\"IP-9\"")
                    .contains("\"items\":[")
                    .contains("\"saleOrderNo\":\"SO-1\"")
                    .contains("\"saleOrderItemNo\":\"9001\"")
                    .contains("\"productCode\":\"MAT-001\"")
                    .contains("\"demandQty\":4")
                    .contains("\"makeQty\":4")
                    .contains("\"buyQty\":0")
                    .contains("\"stockQty\":0")
                    .contains("\"requiredDate\":\"2026-08-12T23:59:59\"")
                    .contains("\"processTypeCode\":\"PACKAGING\"")
                    .contains("\"workHour\":1.5")
                    .contains("\"programNum\":\"10\"")
                    .doesNotContain("allocationId", "ticketId", "sourceType", "partNo", "version", "productionQty", "ticketQty", "lineCost", "hourlyRate", "materialData", "assemblyRelations");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchSendsBearerTokenWhenConfigured() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"externalId\":\"MES-PROD-2\"}");
        });
        try {
            MesProductionDispatchResp response = client(baseUrl(server), "  token-abc  ", "/api/mes/production/submit", 1000)
                    .dispatch(request());

            assertThat(response.getExternalId()).isEqualTo("MES-PROD-2");
            assertThat(captured.path.get()).isEqualTo("/api/mes/production/submit");
            assertThat(captured.authorization.get()).isEqualTo("Bearer token-abc");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchUsesActiveMesIntegrationConfigForBaseUrlTokenAndTimeout() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"externalId\":\"MES-PROD-DB\"}");
        });
        try {
            MesProductionDispatchClient client = client("http://legacy-mes.invalid", "legacy-token", "/legacy", 1000);
            ReflectionTestUtils.setField(client, "externalIntegrationConfigService", configService(mesConfig(
                    baseUrl(server),
                    "db-token",
                    1000
            )));

            MesProductionDispatchResp response = client.dispatch(request());

            assertThat(response.getExternalId()).isEqualTo("MES-PROD-DB");
            assertThat(captured.path.get()).isEqualTo("/legacy");
            assertThat(captured.authorization.get()).isEqualTo("Bearer db-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchReturnsMesBusinessFailureResponse() throws IOException {
        HttpServer server = server(exchange ->
                respond(exchange, 200, "{\"success\":false,\"message\":\"route missing\"}"));
        try {
            MesProductionDispatchResp response = client(baseUrl(server), "", "/production", 1000)
                    .dispatch(request());

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("route missing");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchConvertsHttpErrorsToStatusSummary() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 500, "{\"error\":\"MES down\"}"));
        try {
            assertThatThrownBy(() -> client(baseUrl(server), "", "/production", 1000).dispatch(request()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MES production dispatch failed status=500")
                    .hasMessageContaining("MES down");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchRejectsMissingBaseUrlBeforeHttpCall() {
        assertThatThrownBy(() -> client("", "", "/production", 1000).dispatch(request()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MES base URL is not configured");
    }

    @Test
    void mesProductionConfigurationIsExposedToSpringFromComposeAndEnv() throws IOException {
        String compose = Files.readString(DOCKER_COMPOSE, StandardCharsets.UTF_8);
        assertThat(compose)
                .contains(
                        "ERP_MES_PRODUCTION_PATH: ${ERP_MES_PRODUCTION_PATH:-/api/mes/production/submit}",
                        "ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}",
                        "ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}",
                        "ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-60000}"
                );
        if (Files.exists(ERP_ENV)) {
            String env = Files.readString(ERP_ENV, StandardCharsets.UTF_8);
            assertThat(env)
                    .contains(
                            "ERP_MES_PRODUCTION_PATH=/api/mes/production/submit",
                            "ERP_MES_BASE_URL=",
                            "ERP_MES_AUTH_TOKEN=",
                            "ERP_MES_TIMEOUT_MS=60000"
                    );
        }
    }

    @Test
    void mesProductionClientFailureContractAvoidsTokenAndFullPayloadLogging() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchClient.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("log.debug(\"MES production dispatch request bizId={} itemCount={} productCode={}")
                .doesNotContain(
                        "mesAuthToken={}",
                        "authToken={}",
                        "requestBody={}",
                        "fullJson"
                );
    }

    private MesProductionDispatchClient client(String baseUrl, String token, String path, int timeoutMs) {
        MesProductionDispatchClient client = new MesProductionDispatchClient();
        ReflectionTestUtils.setField(client, "mesBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "mesAuthToken", token);
        ReflectionTestUtils.setField(client, "mesProductionPath", path);
        ReflectionTestUtils.setField(client, "mesTimeoutMs", timeoutMs);
        return client;
    }

    private ExternalIntegrationConfigService configService(ExternalIntegrationConfig config) {
        return new ExternalIntegrationConfigService(null) {
            @Override
            public ExternalIntegrationConfig activeConfig(ExternalSystemCode systemCode) {
                return config;
            }
        };
    }

    private ExternalIntegrationConfig mesConfig(String baseUrl, String authToken, Integer timeoutMs) {
        return new ExternalIntegrationConfig(
                301L,
                "MES active",
                ExternalSystemCode.MES,
                true,
                true,
                isBlank(authToken) ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                baseUrl,
                "/db/material",
                null,
                authToken,
                timeoutMs,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private MesProductionDispatchReq request() {
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setRequestId("REQ-1");
        req.setAllocationId(1001);
        req.setTicketId(2001);
        req.setSourceType("SALES_ORDER");
        req.setSourceOrderId(3001L);
        req.setSourceItemId(9001L);
        req.setSourceOrderNo("SO-1");
        req.setCustomerName("ACME");
        req.setPartNo("P-100");
        req.setVersion("A");
        req.setMaterialNo("MAT-001");
        req.setItemType("ASSEMBLY");
        req.setAllocationType("PRODUCTION_REQUIRED");
        req.setProductionQty(4);
        req.setTicketQty(10);
        req.setOrderType("SO01");
        req.setInternalProjectNo("IP-9");
        req.setCustomerOrderNo("CO-9");
        req.setCustomerProjectNo("CP-9");
        req.setMaterialName("6061");
        req.setEngineeringMaterialCode("MAT-7075");
        req.setEngineeringMaterialName("7075");
        req.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        req.setEngineeringMaterialTypeName("原材料");
        req.setEngineeringMaterialCategory("铝材");
        req.setSurfaceTreatment("Anodized");
        req.setDescription("Bracket");
        req.setRemark("Rush");
        req.setDeliveryDate(LocalDate.of(2026, 8, 12));
        MesProductionProcessRouteDTO route = new MesProductionProcessRouteDTO();
        route.setProcessCode("PACKAGING");
        route.setCategoryName("包装");
        route.setDetailName("精细包装");
        route.setUnitWorkHours(1.5);
        route.setSortNo(10);
        req.setProcessRoute(List.of(route));
        req.setMaterialGrossWeight(12.3);
        req.setPartNetWeight(10.2);
        req.setAssemblyRelations(List.of());
        return req;
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return server;
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class CapturedRequest {
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        private void capture(HttpExchange exchange) throws IOException {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
