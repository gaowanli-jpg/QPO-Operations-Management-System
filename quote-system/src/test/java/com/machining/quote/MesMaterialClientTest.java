package com.machining.quote;

import com.machining.quote.dto.MesMaterialResolveReq;
import com.machining.quote.dto.MesMaterialResolveResp;
import com.machining.quote.service.MesMaterialClient;
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

class MesMaterialClientTest {
    private static final Path DOCKER_COMPOSE = Path.of("..", "docker-compose.yml");
    private static final Path ERP_ENV = Path.of("..", "erp.env");

    @Test
    void resolveMaterialPostsPayloadWithoutAuthorizationWhenTokenBlank() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"MES-001\",\"message\":\"ok\",\"externalTraceId\":\"T-1\"}");
        });
        try {
            MesMaterialClient client = client(baseUrl(server), "", "resolve", 1000);

            MesMaterialResolveResp response = client.resolveMaterial(request());

            assertThat(response.getSuccess()).isTrue();
            assertThat(response.getMaterialNo()).isEqualTo("MES-001");
            assertThat(captured.method.get()).isEqualTo("POST");
            assertThat(captured.authorization.get()).isNull();
            assertThat(captured.contentType.get()).contains("application/json");
            assertThat(captured.body.get())
                    .contains("\"ticketId\":123")
                    .contains("\"partNo\":\"P-100\"")
                    .contains("\"internalProjectNo\":\"IP-9\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveMaterialSendsBearerTokenWhenConfigured() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"MES-002\"}");
        });
        try {
            MesMaterialClient client = client(baseUrl(server), "  token-abc  ", "/api/material", 1000);

            MesMaterialResolveResp response = client.resolveMaterial(request());

            assertThat(response.getMaterialNo()).isEqualTo("MES-002");
            assertThat(captured.path.get()).isEqualTo("/api/material");
            assertThat(captured.authorization.get()).isEqualTo("Bearer token-abc");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveMaterialReturnsMesFailureResponseForBusinessFailure() throws IOException {
        HttpServer server = server(exchange ->
                respond(exchange, 200, "{\"success\":false,\"message\":\"part missing\",\"externalTraceId\":\"T-2\"}"));
        try {
            MesMaterialResolveResp response = client(baseUrl(server), "", "/resolve", 1000)
                    .resolveMaterial(request());

            assertThat(response.getSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("part missing");
            assertThat(response.getExternalTraceId()).isEqualTo("T-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveMaterialConvertsHttpErrorsToStatusSummary() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 500, "{\"error\":\"MES down\"}"));
        try {
            assertThatThrownBy(() -> client(baseUrl(server), "", "/resolve", 1000).resolveMaterial(request()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MES material resolve failed status=500")
                    .hasMessageContaining("MES down");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveMaterialUsesConfiguredTimeout() throws IOException {
        HttpServer server = server(exchange -> {
            sleep(500);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"LATE\"}");
        });
        try {
            assertThatThrownBy(() -> client(baseUrl(server), "", "/resolve", 50).resolveMaterial(request()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("MES material resolve failed");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveMaterialRejectsMissingBaseUrlBeforeHttpCall() {
        assertThatThrownBy(() -> client("", "", "/resolve", 1000).resolveMaterial(request()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MES base URL is not configured");
    }

    @Test
    void mesConfigurationIsExposedToSpringFromComposeAndEnv() throws IOException {
        String compose = Files.readString(DOCKER_COMPOSE, StandardCharsets.UTF_8);
        assertThat(compose)
                .contains(
                        "ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}",
                        "ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}",
                        "ERP_MES_MATERIAL_PATH: ${ERP_MES_MATERIAL_PATH:-/api/mes/materials/resolve}",
                        "ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}"
                );
        if (Files.exists(ERP_ENV)) {
            String env = Files.readString(ERP_ENV, StandardCharsets.UTF_8);
            assertThat(env)
                    .contains(
                            "ERP_MES_BASE_URL=",
                            "ERP_MES_AUTH_TOKEN=",
                            "ERP_MES_MATERIAL_PATH=/api/mes/materials/resolve",
                            "ERP_MES_TIMEOUT_MS=10000"
                    );
        }
    }

    @Test
    void mesClientFailureContractAvoidsTokenAndFullPayloadLogging() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "service", "MesMaterialClient.java"), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("log.debug(\"MES material resolve request ticketId={}")
                .doesNotContain(
                        "mesAuthToken={}",
                        "authToken={}",
                        "requestBody={}",
                        "fullJson"
                );
    }

    private MesMaterialClient client(String baseUrl, String token, String path, int timeoutMs) {
        MesMaterialClient client = new MesMaterialClient();
        ReflectionTestUtils.setField(client, "mesBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "mesAuthToken", token);
        ReflectionTestUtils.setField(client, "mesMaterialPath", path);
        ReflectionTestUtils.setField(client, "mesTimeoutMs", timeoutMs);
        return client;
    }

    private MesMaterialResolveReq request() {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setTicketId(123);
        req.setRfqId(88);
        req.setRfqNo("RFQ-1");
        req.setCustomerName("ACME");
        req.setPartNo("P-100");
        req.setVersion("A");
        req.setDrawingUrl("http://qpo.local/drawing.pdf");
        req.setInternalProjectNo("IP-9");
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
