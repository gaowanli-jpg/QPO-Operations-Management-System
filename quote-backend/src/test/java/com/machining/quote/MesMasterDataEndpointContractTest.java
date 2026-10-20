package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import com.machining.quote.service.DataCenterConfigService;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.DataCenterTokenService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MesMasterDataEndpointContractTest {
    private static final Path DESIGN_DOC = Path.of(
            "..", "docs", "superpowers", "specs", "2026-08-13-mes-master-data-sync-design.md");
    private static final Path APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");
    private static final Path ERP_ENV_EXAMPLE = Path.of("..", "erp.env.example");
    private static final Path DATA_CENTER_PROPERTIES = Path.of(
            "src", "main", "java", "com", "machining", "quote", "config", "DataCenterProperties.java");
    private static final Path STAGE_ONE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V8__mes_master_data_sync.sql");
    private static final Path ENDPOINT_REGISTRY = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DataCenterEndpointRegistry.java");
    private static final Path DATA_CENTER_CLIENT = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DataCenterIntegrationClient.java");
    private static final Path PROCESS_ROUTE_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionProcessRouteDTO.java");
    private static final Path BOM_DTO = Path.of(
            "src", "main", "java", "com", "machining", "quote", "dto", "mes", "MesProductionBomDTO.java");
    private static final Path DISPATCH_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "mes", "MesProductionDispatchService.java");

    @Test
    void stageOneRegistryDefinesReadOnlyMesMasterDataEndpoints() throws IOException {
        String doc = read(DESIGN_DOC);
        DataCenterEndpointRegistry registry = new DataCenterEndpointRegistry();
        DataCenterProperties properties = new DataCenterProperties();
        properties.setMesProcessTypeListPath("/dc/mes/process-type/list");
        properties.setMesMaterialPagePath("/dc/mes/material/page");

        SystemDataCenterEndpointConfig processType = registry.newEndpoint(DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST);
        SystemDataCenterEndpointConfig materialPage = registry.newEndpoint(DataCenterEndpointRegistry.MES_MATERIAL_PAGE);

        assertThat(doc)
                .contains(
                        "MesMasterDataEndpointContractTest",
                        "MES_PROCESS_TYPE_LIST",
                        "MES_MATERIAL_PAGE",
                        "callGet(String endpointCode, Map<String, ?> queryParams, Class<T> responseType)",
                        "ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH",
                        "ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH");

        assertThat(registry.definitions())
                .extracting(DataCenterEndpointRegistry.EndpointDefinition::endpointCode)
                .contains(
                        DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST,
                        DataCenterEndpointRegistry.MES_MATERIAL_PAGE,
                        DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE,
                        DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH,
                        DataCenterEndpointRegistry.U9_COMMON_ENTITY_QUERY);
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST))
                .satisfies(definition -> {
                    assertThat(definition.targetSystemCode()).isEqualTo("MES");
                    assertThat(definition.defaultMethod()).isEqualTo("GET");
                    assertThat(definition.requestAdapter()).isEqualTo("MES_PROCESS_TYPE_LIST_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("MES_PROCESS_TYPE_LIST_RESPONSE_V1");
                    assertThat(definition.minimumTimeoutMs()).isEqualTo(60000);
                });
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.MES_MATERIAL_PAGE))
                .satisfies(definition -> {
                    assertThat(definition.targetSystemCode()).isEqualTo("MES");
                    assertThat(definition.defaultMethod()).isEqualTo("GET");
                    assertThat(definition.requestAdapter()).isEqualTo("MES_MATERIAL_PAGE_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("MES_MATERIAL_PAGE_RESPONSE_V1");
                    assertThat(definition.minimumTimeoutMs()).isEqualTo(60000);
                });
        assertThat(processType.getMethod()).isEqualTo("GET");
        assertThat(processType.getTargetSystemCode()).isEqualTo("MES");
        assertThat(processType.getTimeoutMs()).isEqualTo(60000);
        assertThat(materialPage.getMethod()).isEqualTo("GET");
        assertThat(materialPage.getTargetSystemCode()).isEqualTo("MES");
        assertThat(materialPage.getTimeoutMs()).isEqualTo(60000);
        assertThat(registry.fallbackPath(DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST, properties))
                .isEqualTo("/dc/mes/process-type/list");
        assertThat(registry.fallbackPath(DataCenterEndpointRegistry.MES_MATERIAL_PAGE, properties))
                .isEqualTo("/dc/mes/material/page");

        assertThat(read(ENDPOINT_REGISTRY))
                .contains("MES_PROCESS_TYPE_LIST", "MES_MATERIAL_PAGE")
                .contains("MES_MATERIAL_RESOLVE", "MES_PRODUCTION_DISPATCH", "U9_COMMON_ENTITY_QUERY");
    }

    @Test
    void stageOneConfigurationAndMigrationSeedMesMasterDataEndpoints() throws IOException {
        assertThat(read(APPLICATION_YAML))
                .contains(
                        "mes-process-type-list-path: ${ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH:/api/v1/mes/openapi/process-type/list}",
                        "mes-material-page-path: ${ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH:/api/v1/mes/openapi/material/page}");
        assertThat(read(ERP_ENV_EXAMPLE))
                .contains(
                        "ERP_DATA_CENTER_MES_PROCESS_TYPE_LIST_PATH=/api/v1/mes/openapi/process-type/list",
                        "ERP_DATA_CENTER_MES_MATERIAL_PAGE_PATH=/api/v1/mes/openapi/material/page");
        assertThat(read(DATA_CENTER_PROPERTIES))
                .contains(
                        "private String mesProcessTypeListPath",
                        "private String mesMaterialPagePath",
                        "getMesProcessTypeListPath()",
                        "setMesProcessTypeListPath",
                        "getMesMaterialPagePath()",
                        "setMesMaterialPagePath");

        assertThat(read(STAGE_ONE_MIGRATION))
                .contains(
                        "'MES_PROCESS_TYPE_LIST'",
                        "'MES_MATERIAL_PAGE'",
                        "'MES'",
                        "'GET'",
                        "'/api/v1/mes/openapi/process-type/list'",
                        "'/api/v1/mes/openapi/material/page'",
                        "60000",
                        "'MES_PROCESS_TYPE_LIST_V1'",
                        "'MES_PROCESS_TYPE_LIST_RESPONSE_V1'",
                        "'MES_MATERIAL_PAGE_V1'",
                        "'MES_MATERIAL_PAGE_RESPONSE_V1'",
                        "ON CONFLICT (endpoint_code) DO UPDATE SET")
                .doesNotContain("MES_MATERIAL_RESOLVE',\n    'MES',\n    'GET'", "MES_PRODUCTION_DISPATCH',\n    'MES',\n    'GET'");
    }

    @Test
    void callGetAppendsQueryParametersAndSkipsBlankValues() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (BusinessServer server = businessServer(requestMethod, requestPath, rawQuery, authorization, requestBody, calls)) {
            SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
            DataCenterConfigService configService = mock(DataCenterConfigService.class);
            DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
            DataCenterIntegrationClient client = integrationClient(mapper, configService, tokenService);
            SystemDataCenterConfig activeConfig = dataCenterConfig(server.baseUrl());
            when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(
                    endpoint(31L, DataCenterEndpointRegistry.MES_MATERIAL_PAGE, "MES", "/gateway/mes/material/page", 1000, "GET"));
            when(configService.activeConfig()).thenReturn(activeConfig);
            when(tokenService.fetchAccessToken(activeConfig)).thenReturn("query-token");

            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("pageNo", 1);
            queryParams.put("pageSize", 200);
            queryParams.put("materialType", " ");
            queryParams.put("ignored", null);
            queryParams.put("keyword", "A B");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.callGet(
                    DataCenterEndpointRegistry.MES_MATERIAL_PAGE,
                    queryParams,
                    Map.class);

            assertThat(response).containsEntry("ok", true);
            assertThat(calls).hasValue(1);
            assertThat(requestMethod.get()).isEqualTo("GET");
            assertThat(requestPath.get()).isEqualTo("/gateway/mes/material/page");
            assertThat(rawQuery.get())
                    .startsWith("pageNo=1&pageSize=200")
                    .contains("keyword=A+B")
                    .doesNotContain("materialType", "ignored");
            assertThat(authorization.get()).isEqualTo("Bearer query-token");
            assertThat(requestBody.get()).isEmpty();
        }
    }

    @Test
    void stageOneKeepsExistingEndpointContractsUnchanged() throws IOException {
        assertThat(read(DATA_CENTER_CLIENT))
                .contains("public <T> T call(String endpointCode, Object requestPayload, Class<T> responseType)");

        DataCenterEndpointRegistry registry = new DataCenterEndpointRegistry();
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE))
                .satisfies(definition -> {
                    assertThat(definition.defaultMethod()).isEqualTo("POST");
                    assertThat(definition.requestAdapter()).isEqualTo("MES_MATERIAL_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("MES_MATERIAL_RESPONSE_V1");
                });
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH))
                .satisfies(definition -> {
                    assertThat(definition.defaultMethod()).isEqualTo("POST");
                    assertThat(definition.requestAdapter()).isEqualTo("MES_PRODUCTION_DISPATCH_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("MES_PRODUCTION_DISPATCH_RESPONSE_V1");
                });
        assertThat(registry.requireDefinition(DataCenterEndpointRegistry.U9_COMMON_ENTITY_QUERY))
                .satisfies(definition -> {
                    assertThat(definition.defaultMethod()).isEqualTo("POST");
                    assertThat(definition.requestAdapter()).isEqualTo("U9_COMMON_ENTITY_QUERY_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("U9_COMMON_ENTITY_RESPONSE_V1");
                });
    }

    @Test
    void stageZeroKeepsMesProductionDispatchContractOutOfScope() throws IOException {
        assertThat(read(PROCESS_ROUTE_DTO))
                .contains(
                        "private String processName;",
                        "private String processTypeCode;",
                        "public void setProcessCode(String processCode)",
                        "this.processTypeCode = trimToNull(processCode);",
                        "public void setDetailName(String detailName)",
                        "this.processName = trimToNull(detailName);");

        assertThat(read(BOM_DTO))
                .contains("private String itemCode;", "private BigDecimal quantity;");

        assertThat(read(DISPATCH_SERVICE))
                .contains(
                        "dto.setProcessCode(processCode);",
                        "dto.setDetailName(detailName);",
                        "allocation.getEngineeringMaterialCode()",
                        "allocation.getMaterialGrossWeight()",
                        "new MesProductionBomDTO(materialCode, BigDecimal.valueOf(grossWeightKg))");
    }

    private static String read(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private DataCenterIntegrationClient integrationClient(
            SystemDataCenterEndpointConfigMapper mapper,
            DataCenterConfigService configService,
            DataCenterTokenService tokenService) {
        DataCenterIntegrationClient client = new DataCenterIntegrationClient();
        ReflectionTestUtils.setField(client, "endpointMapper", mapper);
        ReflectionTestUtils.setField(client, "registry", new DataCenterEndpointRegistry());
        ReflectionTestUtils.setField(client, "dataCenterConfigService", configService);
        ReflectionTestUtils.setField(client, "tokenService", tokenService);
        return client;
    }

    private SystemDataCenterEndpointConfig endpoint(
            Long id,
            String endpointCode,
            String targetSystemCode,
            String path,
            Integer timeoutMs,
            String method) {
        SystemDataCenterEndpointConfig entity = new SystemDataCenterEndpointConfig();
        entity.setId(id);
        entity.setEndpointCode(endpointCode);
        entity.setDisplayName(endpointCode);
        entity.setTargetSystemCode(targetSystemCode);
        entity.setMethod(method);
        entity.setPath(path);
        entity.setEnabled(true);
        entity.setTimeoutMs(timeoutMs);
        entity.setRequestAdapter(endpointCode + "_REQ");
        entity.setResponseAdapter(endpointCode + "_RESP");
        entity.setRequestSchemaJson(Map.of());
        entity.setResponseSchemaJson(Map.of());
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
            AtomicReference<String> requestMethod,
            AtomicReference<String> requestPath,
            AtomicReference<String> rawQuery,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody,
            AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gateway/mes/material/page", exchange -> {
            calls.incrementAndGet();
            requestMethod.set(exchange.getRequestMethod());
            requestPath.set(exchange.getRequestURI().getPath());
            rawQuery.set(exchange.getRequestURI().getRawQuery());
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
