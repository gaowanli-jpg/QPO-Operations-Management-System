package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SystemDataCenterEndpointConfigSaveReq;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterConfigService;
import com.machining.quote.service.DataCenterTokenService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCenterConfigStage4Test {

    @Test
    void controllerExposesEndpointConfigApisWithExpectedPermissions() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "SystemDataCenterEndpointConfigController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/data-center-endpoints\")")
                .contains("@GetMapping")
                .contains("@PutMapping(\"/{endpointCode}\")")
                .contains("@PostMapping(\"/{endpointCode}/test\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:integration:update\")")
                .contains("@RequirePermission(\"system:config:integration:test\")");
    }

    @Test
    void registryDefinesFixedEndpointAndAdapterBindings() throws Exception {
        Object registry = Class.forName("com.machining.quote.service.DataCenterEndpointRegistry")
                .getDeclaredConstructor()
                .newInstance();

        Object mes = ReflectionTestUtils.invokeMethod(registry, "requireDefinition", "MES_MATERIAL_RESOLVE");
        Object wms = ReflectionTestUtils.invokeMethod(registry, "requireDefinition", "WMS_INVENTORY_ALLOCATE");
        Object u9 = ReflectionTestUtils.invokeMethod(registry, "requireDefinition", "U9_SALES_ORDER_SYNC");
        Object u9Common = ReflectionTestUtils.invokeMethod(registry, "requireDefinition", "U9_COMMON_ENTITY_QUERY");
        Object production = ReflectionTestUtils.invokeMethod(registry, "requireDefinition", "MES_PRODUCTION_DISPATCH");

        assertThat(String.valueOf(mes)).contains("MES_MATERIAL_RESOLVE", "MES_MATERIAL_V1", "MES_MATERIAL_RESPONSE_V1");
        assertThat(String.valueOf(wms)).contains("WMS_INVENTORY_ALLOCATE", "WMS_ALLOCATION_V1", "WMS_ALLOCATION_RESPONSE_V1");
        assertThat(String.valueOf(u9)).contains("U9_SALES_ORDER_SYNC", "U9_SALES_ORDER_V1", "U9_SALES_ORDER_RESPONSE_V1", "minimumTimeoutMs=60000");
        assertThat(String.valueOf(u9Common)).contains("U9_COMMON_ENTITY_QUERY", "U9_COMMON_ENTITY_QUERY_V1", "U9_COMMON_ENTITY_RESPONSE_V1", "minimumTimeoutMs=60000");
        assertThat(String.valueOf(production)).contains(
                "MES_PRODUCTION_DISPATCH",
                "MES_PRODUCTION_DISPATCH_V1",
                "MES_PRODUCTION_DISPATCH_RESPONSE_V1"
        );
    }

    @Test
    void listEndpointsPreservesIndependentPathAndTimeoutPerFeature() throws Exception {
        SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
        Object service = endpointService(mapper, mock(AuditService.class), mock(DataCenterConfigService.class), mock(DataCenterTokenService.class));
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                endpoint(1L, "MES_MATERIAL_RESOLVE", "MES", "/dc/mes/material", 7000),
                endpoint(2L, "U9_SALES_ORDER_SYNC", "U9", "/dc/u9/sales-order", 22000)
        ));

        Object result = ReflectionTestUtils.invokeMethod(service, "listEndpoints");

        assertThat(String.valueOf(result))
                .contains("MES_MATERIAL_RESOLVE", "/dc/mes/material", "timeoutMs=7000")
                .contains("U9_SALES_ORDER_SYNC", "/dc/u9/sales-order", "timeoutMs=60000")
                .contains("U9_COMMON_ENTITY_QUERY", "timeoutMs=60000");
    }

    @Test
    void saveEndpointUpdatesOnlySelectedEndpointAndKeepsFixedAdapters() throws Exception {
        SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        Object service = endpointService(mapper, auditService, mock(DataCenterConfigService.class), mock(DataCenterTokenService.class));
        SystemDataCenterEndpointConfig existing = endpoint(9L, "MES_MATERIAL_RESOLVE", "MES", "/old/path", 5000);
        existing.setRequestAdapter("SHOULD_BE_REPLACED");
        existing.setResponseAdapter("SHOULD_BE_REPLACED");
        existing.setLastTestStatus("SUCCESS");
        existing.setLastTestMessage("previous ok");
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        SystemDataCenterEndpointConfigSaveReq req = new SystemDataCenterEndpointConfigSaveReq();
        req.setEndpointCode("WMS_INVENTORY_ALLOCATE");
        req.setMethod("POST");
        req.setPath("/new/mes/material");
        req.setEnabled(true);
        req.setTimeoutMs(12345);
        req.setRemark("feature endpoint");

        Object result = ReflectionTestUtils.invokeMethod(service, "saveEndpoint", "MES_MATERIAL_RESOLVE", req);

        assertThat(String.valueOf(result))
                .contains("MES_MATERIAL_RESOLVE", "/new/mes/material", "timeoutMs=12345")
                .doesNotContain("WMS_INVENTORY_ALLOCATE");
        ArgumentCaptor<SystemDataCenterEndpointConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterEndpointConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        SystemDataCenterEndpointConfig updated = updateCaptor.getValue();
        assertThat(updated.getEndpointCode()).isEqualTo("MES_MATERIAL_RESOLVE");
        assertThat(updated.getTargetSystemCode()).isEqualTo("MES");
        assertThat(updated.getRequestAdapter()).isEqualTo("MES_MATERIAL_V1");
        assertThat(updated.getResponseAdapter()).isEqualTo("MES_MATERIAL_RESPONSE_V1");
        assertThat(updated.getPath()).isEqualTo("/new/mes/material");
        assertThat(updated.getTimeoutMs()).isEqualTo(12345);
        assertThat(updated.getLastTestStatus()).isNull();
        verify(mapper, never()).insert(any(SystemDataCenterEndpointConfig.class));

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SYSTEM_DATA_CENTER_ENDPOINT_UPDATE"),
                eq("system_data_center_endpoint_config"),
                eq(9L),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("endpointCode=MES_MATERIAL_RESOLVE", "targetSystemCode=MES", "path=/new/mes/material")
                .doesNotContain("client_secret", "access_token", "Authorization", "Bearer");
    }

    @Test
    void testEndpointValidatesPathBeforeTokenAndDoesNotCallBusinessEndpoint() throws Exception {
        SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
        DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
        AuditService auditService = mock(AuditService.class);
        Object service = endpointService(mapper, auditService, mock(DataCenterConfigService.class), tokenService);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(11L, "WMS_INVENTORY_ALLOCATE", "WMS", " ", 9000));

        Object result = ReflectionTestUtils.invokeMethod(service, "testEndpoint", "WMS_INVENTORY_ALLOCATE");

        assertThat(String.valueOf(result))
                .contains("success=false")
                .contains("path");
        verify(tokenService, never()).fetchAccessToken(any(SystemDataCenterConfig.class));
        ArgumentCaptor<SystemDataCenterEndpointConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterEndpointConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("FAILED");
        verify(auditService).record(
                eq("SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED"),
                eq("system_data_center_endpoint_config"),
                eq(11L),
                isNull(),
                any(),
                eq("SYSTEM"),
                eq("FAILED"),
                any(),
                any()
        );
    }

    @Test
    void testEndpointFetchesTokenOnlyAndStoresSecretSafeSuccessSummary() throws Exception {
        SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
        DataCenterConfigService configService = mock(DataCenterConfigService.class);
        DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
        AuditService auditService = mock(AuditService.class);
        Object service = endpointService(mapper, auditService, configService, tokenService);
        SystemDataCenterConfig activeConfig = dataCenterConfig("http://data-center.local");
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(12L, "U9_SALES_ORDER_SYNC", "U9", "/dc/u9/sales-order", 18000));
        when(configService.activeConfig()).thenReturn(activeConfig);
        when(tokenService.fetchAccessToken(activeConfig)).thenReturn("hidden-access-token");

        Object result = ReflectionTestUtils.invokeMethod(service, "testEndpoint", "U9_SALES_ORDER_SYNC");

        assertThat(String.valueOf(result))
                .contains("success=true", "U9_SALES_ORDER_SYNC")
                .doesNotContain("hidden-access-token", "secret-value", "Authorization");
        verify(tokenService).fetchAccessToken(activeConfig);
        ArgumentCaptor<SystemDataCenterEndpointConfig> updateCaptor = ArgumentCaptor.forClass(SystemDataCenterEndpointConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("SUCCESS");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SYSTEM_DATA_CENTER_ENDPOINT_TEST"),
                eq("system_data_center_endpoint_config"),
                eq(12L),
                isNull(),
                afterCaptor.capture()
        );
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("endpointCode=U9_SALES_ORDER_SYNC", "targetSystemCode=U9", "host=data-center.local", "path=/dc/u9/sales-order")
                .doesNotContain("hidden-access-token", "secret-value", "Authorization", "client_secret");
    }

    @Test
    void integrationClientCallsConfiguredDataCenterPathWithBearerToken() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (BusinessServer server = businessServer(requestPath, authorization, requestBody, calls)) {
            SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
            DataCenterConfigService configService = mock(DataCenterConfigService.class);
            DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
            Object client = integrationClient(mapper, configService, tokenService);
            SystemDataCenterConfig activeConfig = dataCenterConfig(server.baseUrl());
            when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(21L, "MES_MATERIAL_RESOLVE", "MES", "/gateway/mes/material", 15000));
            when(configService.activeConfig()).thenReturn(activeConfig);
            when(tokenService.fetchAccessToken(activeConfig)).thenReturn("secret-access-token");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = ReflectionTestUtils.invokeMethod(
                    client,
                    "call",
                    "MES_MATERIAL_RESOLVE",
                    Map.of("partNo", "P-100", "version", "A"),
                    Map.class
            );

            assertThat(response).containsEntry("ok", true);
            assertThat(calls).hasValue(1);
            assertThat(requestPath.get()).isEqualTo("/gateway/mes/material");
            assertThat(authorization.get()).isEqualTo("Bearer secret-access-token");
            assertThat(requestBody.get()).contains("\"partNo\":\"P-100\"", "\"version\":\"A\"");
        }
    }

    @Test
    void auditActionsCoverDataCenterEndpointGovernance() {
        assertThat(AuditService.inferModule("SYSTEM_DATA_CENTER_ENDPOINT_UPDATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.labelForAction("SYSTEM_DATA_CENTER_ENDPOINT_UPDATE")).contains("功能接口");
        assertThat(AuditService.labelForAction("SYSTEM_DATA_CENTER_ENDPOINT_TEST")).contains("功能接口");
        assertThat(AuditService.labelForAction("SYSTEM_DATA_CENTER_ENDPOINT_TEST_FAILED")).contains("功能接口");
    }

    private Object endpointService(
            SystemDataCenterEndpointConfigMapper mapper,
            AuditService auditService,
            DataCenterConfigService configService,
            DataCenterTokenService tokenService) throws Exception {
        Object service = Class.forName("com.machining.quote.service.DataCenterEndpointConfigService")
                .getDeclaredConstructor()
                .newInstance();
        ReflectionTestUtils.setField(service, "endpointMapper", mapper);
        ReflectionTestUtils.setField(service, "registry", registry());
        ReflectionTestUtils.setField(service, "dataCenterConfigService", configService);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private Object integrationClient(
            SystemDataCenterEndpointConfigMapper mapper,
            DataCenterConfigService configService,
            DataCenterTokenService tokenService) throws Exception {
        Object client = Class.forName("com.machining.quote.service.DataCenterIntegrationClient")
                .getDeclaredConstructor()
                .newInstance();
        ReflectionTestUtils.setField(client, "endpointMapper", mapper);
        ReflectionTestUtils.setField(client, "registry", registry());
        ReflectionTestUtils.setField(client, "dataCenterConfigService", configService);
        ReflectionTestUtils.setField(client, "tokenService", tokenService);
        return client;
    }

    private Object registry() throws Exception {
        return Class.forName("com.machining.quote.service.DataCenterEndpointRegistry")
                .getDeclaredConstructor()
                .newInstance();
    }

    private SystemDataCenterEndpointConfig endpoint(Long id, String endpointCode, String targetSystemCode, String path, Integer timeoutMs) {
        SystemDataCenterEndpointConfig entity = new SystemDataCenterEndpointConfig();
        entity.setId(id);
        entity.setEndpointCode(endpointCode);
        entity.setDisplayName(endpointCode);
        entity.setTargetSystemCode(targetSystemCode);
        entity.setMethod("POST");
        entity.setPath(path);
        entity.setEnabled(true);
        entity.setTimeoutMs(timeoutMs);
        entity.setRequestAdapter(endpointCode + "_REQ");
        entity.setResponseAdapter(endpointCode + "_RESP");
        entity.setRequestSchemaJson(Map.of());
        entity.setResponseSchemaJson(Map.of());
        entity.setRemark("endpoint");
        return entity;
    }

    private SystemDataCenterConfig dataCenterConfig(String baseUrl) {
        SystemDataCenterConfig entity = new SystemDataCenterConfig();
        entity.setId(3L);
        entity.setName("Data Center");
        entity.setActive(true);
        entity.setEnabled(true);
        entity.setBaseUrl(baseUrl);
        entity.setTokenUrl(baseUrl + "/token");
        entity.setClientId("client-a");
        entity.setClientSecretCipher("secret-value");
        entity.setGrantType("client_credentials");
        entity.setConnectTimeoutMs(1000);
        entity.setReadTimeoutMs(30000);
        entity.setTokenExpireSkewSeconds(60);
        return entity;
    }

    private BusinessServer businessServer(
            AtomicReference<String> requestPath,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody,
            AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/mes/material", exchange -> {
            calls.incrementAndGet();
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return new BusinessServer(server);
    }

    private record BusinessServer(HttpServer server) implements AutoCloseable {
        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
