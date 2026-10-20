package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.dto.SystemDataCenterConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.mapper.SystemDataCenterConfigMapper;
import com.machining.quote.service.AuditService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCenterConfigServiceTest {

    @Test
    void controllerExposesDataCenterConfigEndpointsWithExpectedPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "SystemDataCenterConfigController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/data-center-configs\")")
                .contains("@GetMapping")
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@PostMapping(\"/{id}/test-token\")")
                .contains("@PutMapping(\"/{id}/activate\")")
                .contains("@PutMapping(\"/{id}/disable\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:integration:update\")")
                .contains("@RequirePermission(\"system:config:integration:test\")")
                .contains("@RequirePermission(\"system:config:integration:activate\")");
    }

    @Test
    void listConfigsRedactsClientSecret() throws Exception {
        SystemDataCenterConfigMapper mapper = mock(SystemDataCenterConfigMapper.class);
        Object service = configService(mapper, mock(AuditService.class), null);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(config(2L, "plain-secret")));

        @SuppressWarnings("unchecked")
        List<Object> configs = ReflectionTestUtils.invokeMethod(service, "listConfigs");

        assertThat(configs).hasSize(1);
        assertThat(String.valueOf(configs)).doesNotContain("plain-secret", "client_secret=plain-secret");
        assertThat(String.valueOf(configs)).contains("clientSecret=******", "clientSecretConfigured=true");
    }

    @Test
    void saveConfigKeepsExistingSecretWhenBlankAndRecordsSecretSafeAudit() throws Exception {
        SystemDataCenterConfigMapper mapper = mock(SystemDataCenterConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        Object service = configService(mapper, auditService, null);
        SystemDataCenterConfig existing = config(9L, "old-secret");
        existing.setLastTestStatus("SUCCESS");
        existing.setLastTestMessage("previous ok");
        existing.setLastTestAt(LocalDateTime.now().minusDays(1));
        when(mapper.selectById(9L)).thenReturn(existing);

        SystemDataCenterConfigSaveReq req = saveReq(" ");
        req.setBaseUrl("http://data-center-new");

        Object result = ReflectionTestUtils.invokeMethod(service, "saveConfig", 9L, req);

        assertThat(String.valueOf(result)).doesNotContain("old-secret");
        ArgumentCaptor<SystemDataCenterConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        SystemDataCenterConfig updated = updateCaptor.getValue();
        assertThat(updated.getClientSecretCipher()).isEqualTo("old-secret");
        assertThat(updated.getLastTestStatus()).isNull();
        assertThat(updated.getLastTestMessage()).isNull();
        assertThat(updated.getLastTestAt()).isNull();

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_DATA_CENTER_CONFIG_UPDATE"), eq("system_data_center_config"), eq(9L), beforeCaptor.capture(), afterCaptor.capture());
        assertThat(String.valueOf(beforeCaptor.getValue())).doesNotContain("old-secret", "client_secret", "access_token", "Authorization");
        assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain("old-secret", "client_secret", "access_token", "Authorization");
    }

    @Test
    void createConfigStoresSecretButReturnsAndAuditsOnlyMaskedSecret() throws Exception {
        SystemDataCenterConfigMapper mapper = mock(SystemDataCenterConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        Object service = configService(mapper, auditService, null);
        doAnswer(invocation -> {
            SystemDataCenterConfig entity = invocation.getArgument(0);
            entity.setId(18L);
            return 1;
        }).when(mapper).insert(any(SystemDataCenterConfig.class));

        Object result = ReflectionTestUtils.invokeMethod(service, "saveConfig", null, saveReq("new-secret"));

        assertThat(String.valueOf(result)).contains("clientSecret=******").doesNotContain("new-secret");
        ArgumentCaptor<SystemDataCenterConfig> insertCaptor = ArgumentCaptor.forClass(SystemDataCenterConfig.class);
        verify(mapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getClientSecretCipher()).isEqualTo("new-secret");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_DATA_CENTER_CONFIG_CREATE"), eq("system_data_center_config"), eq(18L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain("new-secret", "client_secret", "access_token", "Authorization");
    }

    @Test
    void testTokenUpdatesStatusWithoutReturningOrAuditingAccessToken() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (TokenServer server = tokenServer(200, "{\"access_token\":\"token-hidden\",\"expires_in\":120,\"token_type\":\"Bearer\"}", requestBody, calls)) {
            SystemDataCenterConfigMapper mapper = mock(SystemDataCenterConfigMapper.class);
            AuditService auditService = mock(AuditService.class);
            Object service = configService(mapper, auditService, tokenService());
            SystemDataCenterConfig entity = config(3L, "secret-value");
            entity.setTokenUrl(server.url("/token"));
            when(mapper.selectById(3L)).thenReturn(entity);

            Object result = ReflectionTestUtils.invokeMethod(service, "testToken", 3L);

            assertThat(String.valueOf(result)).contains("success=true").doesNotContain("token-hidden", "secret-value");
            assertThat(requestBody.get()).contains("client_id=client-a", "client_secret=secret-value", "grant_type=client_credentials");
            ArgumentCaptor<SystemDataCenterConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterConfig.class);
            verify(mapper).updateById(updateCaptor.capture());
            assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("SUCCESS");
            assertThat(updateCaptor.getValue().getLastTestMessage()).doesNotContain("token-hidden", "secret-value");
            ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
            verify(auditService).record(eq("SYSTEM_DATA_CENTER_CONFIG_TEST"), eq("system_data_center_config"), eq(3L), isNull(), afterCaptor.capture());
            assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain("token-hidden", "secret-value", "Authorization");
        }
    }

    @Test
    void tokenServicePostsFormUrlEncodedAndCachesTokenUntilBeforeExpiry() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (TokenServer server = tokenServer(200, "{\"access_token\":\"cached-token\",\"expires_in\":120,\"token_type\":\"Bearer\"}", requestBody, calls, contentType)) {
            Object service = tokenService();
            SystemDataCenterConfig entity = config(4L, "secret-value");
            entity.setTokenUrl(server.url("/token"));

            String first = ReflectionTestUtils.invokeMethod(service, "fetchAccessToken", entity);
            String second = ReflectionTestUtils.invokeMethod(service, "fetchAccessToken", entity);

            assertThat(first).isEqualTo("cached-token");
            assertThat(second).isEqualTo("cached-token");
            assertThat(calls).hasValue(1);
            assertThat(contentType.get()).startsWith("application/x-www-form-urlencoded");
            assertThat(requestBody.get()).contains("client_id=client-a", "client_secret=secret-value", "grant_type=client_credentials");
        }
    }

    @Test
    void tokenServiceReturnsSanitizedFailure() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        String body = "bad client_secret=secret-value access_token=leaked-token Authorization: Bearer leaked";
        try (TokenServer server = tokenServer(401, body, requestBody, calls)) {
            Object service = tokenService();
            SystemDataCenterConfig entity = config(5L, "secret-value");
            entity.setTokenUrl(server.url("/token"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "fetchAccessToken", entity))
                    .hasMessageContaining("Data center token fetch failed")
                    .hasMessageNotContaining("secret-value")
                    .hasMessageNotContaining("leaked-token")
                    .hasMessageNotContaining("Authorization: Bearer leaked");
        }
    }

    private Object configService(SystemDataCenterConfigMapper mapper, AuditService auditService, Object tokenService) throws Exception {
        Object service = Class.forName("com.machining.quote.service.DataCenterConfigService")
                .getDeclaredConstructor()
                .newInstance();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "dataCenterProperties", new DataCenterProperties());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        if (tokenService != null) {
            ReflectionTestUtils.setField(service, "tokenService", tokenService);
        }
        return service;
    }

    private Object tokenService() throws Exception {
        return Class.forName("com.machining.quote.service.DataCenterTokenService")
                .getDeclaredConstructor()
                .newInstance();
    }

    private SystemDataCenterConfig config(Long id, String secret) {
        SystemDataCenterConfig entity = new SystemDataCenterConfig();
        entity.setId(id);
        entity.setName("Data Center");
        entity.setActive(false);
        entity.setEnabled(true);
        entity.setBaseUrl("http://data-center");
        entity.setTokenUrl("http://data-center/token");
        entity.setClientId("client-a");
        entity.setClientSecretCipher(secret);
        entity.setGrantType("client_credentials");
        entity.setConnectTimeoutMs(1000);
        entity.setReadTimeoutMs(1000);
        entity.setTokenExpireSkewSeconds(60);
        entity.setRemark("config");
        return entity;
    }

    private SystemDataCenterConfigSaveReq saveReq(String secret) {
        SystemDataCenterConfigSaveReq req = new SystemDataCenterConfigSaveReq();
        req.setName("Data Center");
        req.setEnabled(true);
        req.setBaseUrl("http://data-center");
        req.setTokenUrl("http://data-center/token");
        req.setClientId("client-a");
        req.setClientSecret(secret);
        req.setGrantType("client_credentials");
        req.setConnectTimeoutMs(5000);
        req.setReadTimeoutMs(30000);
        req.setTokenExpireSkewSeconds(60);
        req.setRemark("config");
        return req;
    }

    private TokenServer tokenServer(int status, String responseBody, AtomicReference<String> requestBody, AtomicInteger calls) throws IOException {
        return tokenServer(status, responseBody, requestBody, calls, new AtomicReference<>());
    }

    private TokenServer tokenServer(
            int status,
            String responseBody,
            AtomicReference<String> requestBody,
            AtomicInteger calls,
            AtomicReference<String> contentType) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return new TokenServer(server);
    }

    private record TokenServer(HttpServer server) implements AutoCloseable {
        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
