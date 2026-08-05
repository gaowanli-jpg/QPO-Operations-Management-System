package com.machining.quote;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.dto.wms.WmsAllocationReq;
import com.machining.quote.dto.wms.WmsAllocationResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.mes.MesMaterialClient;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9TokenService;
import com.machining.quote.service.wms.WmsAllocationClient;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCenterConfigStage5Test {

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
    void wmsAllocationClientDefaultsToDataCenterEndpoint() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        WmsAllocationResp expected = new WmsAllocationResp();
        expected.setSuccess(true);
        expected.setMaterialNo("MAT-WMS-DC");
        expected.setInStockLockedQty(3);
        when(dataCenterClient.call(eq(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE), any(), eq(WmsAllocationResp.class)))
                .thenReturn(expected);
        WmsAllocationClient client = new WmsAllocationClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        ReflectionTestUtils.setField(client, "dataCenterEnabled", true);

        WmsAllocationReq request = wmsReq();
        WmsAllocationResp response = client.allocate(request);

        assertThat(response.getMaterialNo()).isEqualTo("MAT-WMS-DC");
        assertThat(response.getInStockLockedQty()).isEqualTo(3);
        verify(dataCenterClient).call(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, request, WmsAllocationResp.class);
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
        req.setCustomerCode("C-ACME");
        req.setCustomerName("ACME");
        req.setPartNo("P-100");
        req.setVersion("A");
        req.setInternalProjectNo("IP-9");
        return req;
    }

    private WmsAllocationReq wmsReq() {
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
