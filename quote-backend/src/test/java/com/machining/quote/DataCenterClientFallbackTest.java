package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterConfigService;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.DataCenterTokenService;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9TokenService;
import com.machining.quote.service.wms.WmsReservationClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCenterClientFallbackTest {

    @Test
    void mesMaterialClientDefaultsToDataCenterEndpoint() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesMaterialResolveResp expected = new MesMaterialResolveResp();
        expected.setSuccess(true);
        expected.setMaterialNo("MAT-DC-001");
        when(dataCenterClient.call(eq(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE), any(), eq(MesMaterialResolveResp.class)))
                .thenReturn(expected);
        MesMaterialClient client = new MesMaterialClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        ReflectionTestUtils.setField(client, "dataCenterEnabled", true);

        MesMaterialResolveReq request = mesMaterialReq();
        MesMaterialResolveResp response = client.resolveMaterial(request);

        assertThat(response.getMaterialNo()).isEqualTo("MAT-DC-001");
        verify(dataCenterClient).call(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE, request, MesMaterialResolveResp.class);
    }

    @Test
    void wmsReservationClientDefaultsToDataCenterEndpoint() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        WmsSaleLadingReserveResp expected = new WmsSaleLadingReserveResp();
        expected.setCode(200);
        when(dataCenterClient.call(eq(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE), any(), eq(WmsSaleLadingReserveResp.class)))
                .thenReturn(expected);
        WmsReservationClient client = new WmsReservationClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);

        WmsSaleLadingReserveReq request = wmsReserveReq();
        WmsSaleLadingReserveResp response = client.reserve(request);

        assertThat(response).isSameAs(expected);
        verify(dataCenterClient).call(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, request, WmsSaleLadingReserveResp.class);
    }

    @Test
    void u9SalesOrderClientDefaultsToDataCenterEndpointAndDoesNotFetchLegacyU9Token() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        U9TokenService tokenService = mock(U9TokenService.class);
        when(dataCenterClient.call(eq(DataCenterEndpointRegistry.U9_SALES_ORDER_SYNC), any(), eq(String.class)))
                .thenReturn("""
                        {
                          "ResCode": 0,
                          "Success": true,
                          "ResMsg": "ok",
                          "Data": [
                            { "m_isSucess": true, "m_otherID": "O-1", "m_iD": "U9-ID", "m_code": "SO-001" }
                          ]
                        }
                        """);
        U9SalesOrderClient client = new U9SalesOrderClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        ReflectionTestUtils.setField(client, "dataCenterEnabled", true);
        ReflectionTestUtils.setField(client, "tokenService", tokenService);

        List<U9SalesOrderHeadDTO> orders = List.of(u9Order("O-1"));
        U9ApiResult<?> response = client.create(orders);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        verify(dataCenterClient).call(DataCenterEndpointRegistry.U9_SALES_ORDER_SYNC, orders, String.class);
        verify(tokenService, never()).fetchToken();
    }

    @Test
    void mesProductionDispatchClientDefaultsToDataCenterEndpoint() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesProductionDispatchResp expected = new MesProductionDispatchResp();
        expected.setSuccess(true);
        expected.setExternalId("MES-PROD-DC");
        when(dataCenterClient.call(eq(DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH), any(), eq(MesProductionDispatchResp.class)))
                .thenReturn(expected);
        MesProductionDispatchClient client = new MesProductionDispatchClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        ReflectionTestUtils.setField(client, "dataCenterEnabled", true);

        MesProductionDispatchReq request = productionReq();
        MesProductionDispatchResp response = client.dispatch(request);

        assertThat(response.getExternalId()).isEqualTo("MES-PROD-DC");
        verify(dataCenterClient).call(DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH, request, MesProductionDispatchResp.class);
    }

    @Test
    void dataCenterCallExceptionMarksMissingEndpointPathAsDefiniteFailureBeforeTokenFetch() {
        SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
        DataCenterConfigService configService = mock(DataCenterConfigService.class);
        DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
        DataCenterIntegrationClient client = dataCenterClient(mapper, configService, tokenService);
        when(configService.activeConfig()).thenReturn(dataCenterConfig("http://data-center.local"));
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(
                91L,
                DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                "WMS",
                " ",
                60000,
                "POST"
        ));

        assertThatThrownBy(() -> client.call(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, Map.of("ping", true), Map.class))
                .isInstanceOfSatisfying(DataCenterCallException.class, error -> {
                    assertThat(error.getEndpointCode()).isEqualTo(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE);
                    assertThat(error.getCertainty()).isEqualTo(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE);
                    assertThat(error.getHttpStatus()).isNull();
                    assertThat(error.getMessage()).contains("path");
                });
        verify(tokenService, never()).fetchAccessToken(any(SystemDataCenterConfig.class));
    }

    @Test
    void dataCenterCallExceptionKeepsHttpStatusAsDefiniteFailureWithoutLeakingToken() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 500, "{\"msg\":\"rejected\"}"));
        try {
            SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
            DataCenterConfigService configService = mock(DataCenterConfigService.class);
            DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
            DataCenterIntegrationClient client = dataCenterClient(mapper, configService, tokenService);
            SystemDataCenterConfig activeConfig = dataCenterConfig(baseUrl(server));
            when(configService.activeConfig()).thenReturn(activeConfig);
            when(tokenService.fetchAccessToken(activeConfig)).thenReturn("secret-access-token");
            when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(
                    92L,
                    DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                    "WMS",
                    "/wms/reserve",
                    60000,
                    "POST"
            ));

            assertThatThrownBy(() -> client.call(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, Map.of("ping", true), Map.class))
                    .isInstanceOfSatisfying(DataCenterCallException.class, error -> {
                        assertThat(error.getEndpointCode()).isEqualTo(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE);
                        assertThat(error.getCertainty()).isEqualTo(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE);
                        assertThat(error.getHttpStatus()).isEqualTo(500);
                        assertThat(error.getMessage()).contains("status=500").doesNotContain("secret-access-token");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dataCenterCallExceptionTreatsDroppedResponseAfterRequestAsResultUnknown() throws IOException {
        HttpServer server = server(exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.close();
        });
        try {
            SystemDataCenterEndpointConfigMapper mapper = mock(SystemDataCenterEndpointConfigMapper.class);
            DataCenterConfigService configService = mock(DataCenterConfigService.class);
            DataCenterTokenService tokenService = mock(DataCenterTokenService.class);
            DataCenterIntegrationClient client = dataCenterClient(mapper, configService, tokenService);
            SystemDataCenterConfig activeConfig = dataCenterConfig(baseUrl(server));
            when(configService.activeConfig()).thenReturn(activeConfig);
            when(tokenService.fetchAccessToken(activeConfig)).thenReturn("secret-access-token");
            when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(endpoint(
                    93L,
                    DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE,
                    "WMS",
                    "/wms/reserve",
                    60000,
                    "POST"
            ));

            assertThatThrownBy(() -> client.call(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, Map.of("ping", true), Map.class))
                    .isInstanceOfSatisfying(DataCenterCallException.class, error -> {
                        assertThat(error.getEndpointCode()).isEqualTo(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE);
                        assertThat(error.getCertainty()).isEqualTo(DataCenterCallException.FailureCertainty.RESULT_UNKNOWN);
                        assertThat(error.getHttpStatus()).isNull();
                        assertThat(error.getMessage()).doesNotContain("secret-access-token");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mesMaterialClientUsesLegacyDirectCallWhenDataCenterSwitchDisabled() throws IOException {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, "{\"success\":true,\"materialNo\":\"LEGACY-MAT\"}");
        });
        try {
            MesMaterialClient client = new MesMaterialClient();
            ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
            ReflectionTestUtils.setField(client, "dataCenterEnabled", false);
            ReflectionTestUtils.setField(client, "mesBaseUrl", baseUrl(server));
            ReflectionTestUtils.setField(client, "mesAuthToken", "legacy-token");
            ReflectionTestUtils.setField(client, "mesMaterialPath", "/legacy/material");
            ReflectionTestUtils.setField(client, "mesTimeoutMs", 1000);

            MesMaterialResolveResp response = client.resolveMaterial(mesMaterialReq());

            assertThat(response.getMaterialNo()).isEqualTo("LEGACY-MAT");
            assertThat(captured.path.get()).isEqualTo("/legacy/material");
            assertThat(captured.authorization.get()).isEqualTo("Bearer legacy-token");
            verify(dataCenterClient, never()).call(any(), any(), eq(MesMaterialResolveResp.class));
        } finally {
            server.stop(0);
        }
    }

    private MesMaterialResolveReq mesMaterialReq() {
        MesMaterialResolveReq req = new MesMaterialResolveReq();
        req.setMesRequestId("MES-REQ-DC");
        req.setCustomerCode("C-ACME");
        req.setCustomerName("ACME");
        req.setInternalProjectNo("IP-9");
        MesMaterialResolveReq.Item item = new MesMaterialResolveReq.Item();
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        req.setItems(List.of(item));
        return req;
    }

    private WmsSaleLadingReserveReq wmsReserveReq() {
        WmsSaleLadingReserveReq req = new WmsSaleLadingReserveReq();
        req.setExternalBizId("QPO-WMS-DC");
        req.setSaleNo("SO-1");
        WmsSaleLadingReserveReq.Item item = new WmsSaleLadingReserveReq.Item();
        item.setProductCode("MAT-001");
        item.setReserveCount(BigDecimal.TEN);
        item.setRemark("reserved from delivered item");
        req.setItems(List.of(item));
        return req;
    }

    private DataCenterIntegrationClient dataCenterClient(
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

    private U9SalesOrderHeadDTO u9Order(String otherId) {
        U9SalesOrderHeadDTO order = new U9SalesOrderHeadDTO();
        order.setOtherID(otherId);
        order.setOrderBy("C001");
        order.setDocumentType("SO01");
        order.setBusinessDate("2026-07-24");
        U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
        line.setItemCode("MAT-001");
        line.setOrderByQtyTU(BigDecimal.ONE);
        order.setSOLineDTOList(List.of(line));
        return order;
    }

    private MesProductionDispatchReq productionReq() {
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setRequestId("REQ-1");
        req.setAllocationId(1001);
        req.setTicketId(2001);
        req.setSourceType("SALES_ORDER");
        req.setSourceOrderNo("SO-1");
        req.setCustomerName("ACME");
        req.setPartNo("P-100");
        req.setVersion("A");
        req.setMaterialNo("MAT-001");
        req.setProductionQty(4);
        req.setTicketQty(10);
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

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class CapturedRequest {
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();

        private void capture(HttpExchange exchange) {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        }
    }
}
