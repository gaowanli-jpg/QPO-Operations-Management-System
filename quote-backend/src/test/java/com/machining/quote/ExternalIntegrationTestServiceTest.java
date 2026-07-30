package com.machining.quote;

import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationTestResult;
import com.machining.quote.integration.ExternalIntegrationTestService;
import com.machining.quote.integration.ExternalSystemCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalIntegrationTestServiceTest {

    @Test
    void testMesConfigPostsMinimalRequestAndTreatsReachableHttpAsSuccess() throws IOException {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"success\":false,\"message\":\"business validation failed\"}");
        });
        try {
            ExternalIntegrationTestResult result = new ExternalIntegrationTestService().test(config(
                    ExternalSystemCode.MES,
                    baseUrl(server),
                    "/mes/material",
                    null,
                    "mes-token",
                    null
            ));

            assertThat(result.success()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.message()).contains("reachable").doesNotContain("mes-token");
            assertThat(method.get()).isEqualTo("POST");
            assertThat(path.get()).isEqualTo("/mes/material");
            assertThat(authorization.get()).isEqualTo("Bearer mes-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testU9ConfigOnlyCallsAuthLoginAndDoesNotExposeSecretInResultMessage() throws IOException {
        AtomicReference<String> pathAndQuery = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            pathAndQuery.set(exchange.getRequestURI().toString());
            respond(exchange, 200, "{\"token\":\"u9-token\"}");
        });
        try {
            ExternalIntegrationTestResult result = new ExternalIntegrationTestService().test(u9Config(baseUrl(server), "u9-secret"));

            assertThat(result.success()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.message()).contains("reachable").doesNotContain("u9-secret", "u9-token");
            assertThat(pathAndQuery.get()).contains("/webapi/OAuth2/AuthLogin");
            assertThat(pathAndQuery.get()).doesNotContain("/webapi/SO/Create");
        } finally {
            server.stop(0);
        }
    }

    private ExternalIntegrationConfig config(
            ExternalSystemCode systemCode,
            String baseUrl,
            String materialPath,
            String allocationPath,
            String authToken,
            String clientSecret) {
        return new ExternalIntegrationConfig(
                1L,
                systemCode.name(),
                systemCode,
                false,
                true,
                authToken == null ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                baseUrl,
                materialPath,
                allocationPath,
                authToken,
                1000,
                null,
                null,
                null,
                null,
                null,
                null,
                clientSecret,
                1000,
                1000,
                null
        );
    }

    private ExternalIntegrationConfig u9Config(String baseUrl, String clientSecret) {
        return new ExternalIntegrationConfig(
                2L,
                "U9",
                ExternalSystemCode.U9,
                false,
                true,
                ExternalAuthType.U9_AUTH_LOGIN,
                baseUrl,
                null,
                null,
                null,
                null,
                "/webapi/OAuth2/AuthLogin",
                "/webapi/SO/Create",
                "qpo",
                "ent",
                "org",
                "client",
                clientSecret,
                1000,
                1000,
                null
        );
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

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
