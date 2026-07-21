package com.machining.quote;

import com.machining.quote.dto.WmsAllocationReq;
import com.machining.quote.dto.WmsAllocationResp;
import com.machining.quote.service.WmsAllocationClient;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(compose)
                .as("docker compose must pass WMS configuration through to Spring")
                .contains(
                        "ERP_WMS_BASE_URL: ${ERP_WMS_BASE_URL:-}",
                        "ERP_WMS_AUTH_TOKEN: ${ERP_WMS_AUTH_TOKEN:-}",
                        "ERP_WMS_ALLOCATION_PATH: ${ERP_WMS_ALLOCATION_PATH:-/api/wms/inventory/allocate}",
                        "ERP_WMS_TIMEOUT_MS: ${ERP_WMS_TIMEOUT_MS:-10000}"
                );
        if (Files.exists(ERP_ENV)) {
            String env = read(ERP_ENV);
            assertThat(env)
                    .as("erp.env must document the WMS configuration contract")
                    .contains(
                            "ERP_WMS_BASE_URL=",
                            "ERP_WMS_AUTH_TOKEN=",
                            "ERP_WMS_ALLOCATION_PATH=/api/wms/inventory/allocate",
                            "ERP_WMS_TIMEOUT_MS=10000"
                    );
        }
    }

    @Test
    void allocatePostsPayloadAndParsesDynamicProductionLocksWithoutAuthorizationWhenTokenBlank() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, """
                    {
                      "success": true,
                      "materialNo": "MAT-001",
                      "inStockLockedQty": 3,
                      "inProductionLocks": [
                        { "productionOrderNo": "MO-001", "lockedQty": 4 },
                        { "productionOrderNo": "MO-002", "lockedQty": 2 }
                      ],
                      "message": "allocated",
                      "externalTraceId": "WMS-T-1"
                    }
                    """);
        });
        try {
            WmsAllocationResp response = client(baseUrl(server), "", "allocate", 1000)
                    .allocate(request());

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getMaterialNo()).isEqualTo("MAT-001");
            assertThat(response.getInStockLockedQty()).isEqualTo(3);
            assertThat(response.getInProductionLocks())
                    .hasSize(2)
                    .extracting(WmsAllocationResp.InProductionLock::getProductionOrderNo)
                    .containsExactly("MO-001", "MO-002");
            assertThat(response.getInProductionLocks())
                    .extracting(WmsAllocationResp.InProductionLock::getLockedQty)
                    .containsExactly(4, 2);
            assertThat(response.getExternalTraceId()).isEqualTo("WMS-T-1");
            assertThat(captured.method.get()).isEqualTo("POST");
            assertThat(captured.authorization.get()).isNull();
            assertThat(captured.contentType.get()).contains("application/json");
            assertThat(captured.body.get())
                    .contains("\"ticketId\":123")
                    .contains("\"rfqNo\":\"RFQ-1\"")
                    .contains("\"customerName\":\"ACME\"")
                    .contains("\"materialNo\":\"MAT-001\"")
                    .contains("\"partNo\":\"P-100\"")
                    .contains("\"internalProjectNo\":\"IP-9\"")
                    .contains("\"demandQty\":10");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allocateSendsBearerTokenWhenConfigured() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"MAT-002\"}");
        });
        try {
            WmsAllocationResp response = client(baseUrl(server), "  token-abc  ", "/api/wms/allocate", 1000)
                    .allocate(request());

            assertThat(response.getMaterialNo()).isEqualTo("MAT-002");
            assertThat(captured.path.get()).isEqualTo("/api/wms/allocate");
            assertThat(captured.authorization.get()).isEqualTo("Bearer token-abc");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allocateReturnsWmsFailureResponseForBusinessFailure() throws IOException {
        HttpServer server = server(exchange ->
                respond(exchange, 200, "{\"success\":false,\"message\":\"inventory unavailable\",\"externalTraceId\":\"WMS-T-2\"}"));
        try {
            WmsAllocationResp response = client(baseUrl(server), "", "/allocate", 1000)
                    .allocate(request());

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("inventory unavailable");
            assertThat(response.getExternalTraceId()).isEqualTo("WMS-T-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allocateConvertsHttpErrorsToStatusSummary() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 500, "{\"error\":\"WMS down\"}"));
        try {
            assertThatThrownBy(() -> client(baseUrl(server), "", "/allocate", 1000).allocate(request()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("WMS allocation failed status=500")
                    .hasMessageContaining("WMS down");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allocateUsesConfiguredTimeout() throws IOException {
        HttpServer server = server(exchange -> {
            sleep(500);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"LATE\"}");
        });
        try {
            assertThatThrownBy(() -> client(baseUrl(server), "", "/allocate", 50).allocate(request()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("WMS allocation failed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allocateRejectsMissingBaseUrlBeforeHttpCall() {
        assertThatThrownBy(() -> client("", "", "/allocate", 1000).allocate(request()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("WMS base URL is not configured");
    }

    @Test
    void wmsClientFailureContractAvoidsTokenAndFullPayloadLogging() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "WmsAllocationClient.java"), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("log.debug(\"WMS allocation request ticketId={}")
                .doesNotContain(
                        "wmsAuthToken={}",
                        "authToken={}",
                        "requestBody={}",
                        "fullJson"
                );
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private WmsAllocationClient client(String baseUrl, String token, String path, int timeoutMs) {
        WmsAllocationClient client = new WmsAllocationClient();
        ReflectionTestUtils.setField(client, "wmsBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "wmsAuthToken", token);
        ReflectionTestUtils.setField(client, "wmsAllocationPath", path);
        ReflectionTestUtils.setField(client, "wmsTimeoutMs", timeoutMs);
        return client;
    }

    private WmsAllocationReq request() {
        WmsAllocationReq req = new WmsAllocationReq();
        req.setTicketId(123);
        req.setRfqId(88);
        req.setRfqNo("RFQ-1");
        req.setCustomerName("ACME");
        req.setMaterialNo("MAT-001");
        req.setPartNo("P-100");
        req.setVersion("A");
        req.setInternalProjectNo("IP-9");
        req.setDemandQty(10);
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
