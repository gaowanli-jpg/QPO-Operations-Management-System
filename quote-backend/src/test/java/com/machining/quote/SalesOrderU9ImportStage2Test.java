package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderDryRunResp;
import com.machining.quote.dto.SalesOrderExcelParseResult;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import com.machining.quote.service.SalesOrderExcelParser;
import com.machining.quote.service.SalesOrderImportService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9TokenService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderU9ImportStage2Test {
    private final SalesOrderExcelParser parser = new SalesOrderExcelParser();

    @Test
    void parserAggregatesRowsIntoU9SalesOrdersWithDefaultLineAndShipLineValues() throws Exception {
        SalesOrderExcelParseResult result = parser.parse(
                new ByteArrayInputStream(excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", 10, "PCS", "PCS", "18.5", "2026-08-01"),
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-002", "CP-002", "零件B", 5, "PCS", "PCS", "20", "2026-08-03"),
                        row("C002", "SO01", "PO-002", "2026-07-25", "ITEM-003", "CP-003", "零件C", 2, "PCS", "PCS", "8", "2026-08-05")
                ))),
                "SOI202607240001"
        );

        assertThat(result.getSourceRowCount()).isEqualTo(3);
        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getLineCount()).isEqualTo(3);

        U9SalesOrderHeadDTO firstOrder = result.getOrders().get(0);
        assertThat(firstOrder.getOtherID()).isEqualTo("SOI202607240001-1");
        assertThat(firstOrder.getOrderBy()).isEqualTo("C001");
        assertThat(firstOrder.getDocumentType()).isEqualTo("SO01");
        assertThat(firstOrder.getBusinessDate()).isEqualTo("2026-07-24");
        assertThat(firstOrder.getSOLineDTOList()).hasSize(2);

        var firstLine = firstOrder.getSOLineDTOList().get(0);
        assertThat(firstLine.getItemCode()).isEqualTo("ITEM-001");
        assertThat(firstLine.getCustomerItemNo()).isEqualTo("CP-001");
        assertThat(firstLine.getCustomerItemName()).isEqualTo("零件A");
        assertThat(firstLine.getOrderByQtyTU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstLine.getOrderByQtyPU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstLine.getOrderPriceTC()).isEqualByComparingTo(new BigDecimal("18.5"));
        assertThat(firstLine.getFinallyPriceTC()).isEqualByComparingTo(new BigDecimal("18.5"));
        assertThat(firstLine.getFreeType()).isEqualTo(-1);
        assertThat(firstLine.getFreeReason()).isEqualTo(-1);
        assertThat(firstLine.getShipTogetherFlag()).isEqualTo(-1);
        assertThat(firstLine.getPriceSource()).isEqualTo(1);
        assertThat(firstLine.getSOShiplineDTOList()).hasSize(1);

        var firstShipLine = firstLine.getSOShiplineDTOList().get(0);
        assertThat(firstShipLine.getItemCode()).isEqualTo("ITEM-001");
        assertThat(firstShipLine.getRequireDate()).isEqualTo("2026-08-01");
        assertThat(firstShipLine.getShipPlanQtyTU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstShipLine.getShipPlanQtyPU()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(firstShipLine.getTU()).isEqualTo("PCS");
        assertThat(firstShipLine.getPU()).isEqualTo("PCS");
        assertThat(firstShipLine.getTUBaseToPUBaseRate()).isEqualByComparingTo(BigDecimal.ONE);

        String json = new ObjectMapper().writeValueAsString(firstOrder);
        assertThat(json)
                .contains("\"OtherID\":\"SOI202607240001-1\"")
                .contains("\"SOLineDTOList\"")
                .contains("\"SOShiplineDTOList\"");
    }

    @Test
    void parserRejectsMissingRequiredHeadersWithClearMessage() {
        List<String> headers = List.of("客户编码", "单据类型编码", "客户订单号", "业务日期", "客户料号", "客户品名", "数量", "销售单位", "计价单位", "单价", "交期");

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(excelBytes(headers, List.of())), "SOI202607240001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失表头")
                .hasMessageContaining("U9料号");
    }

    @Test
    void parserRejectsRowsMissingRequiredValuesWithRowNumber() {
        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream(excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", "", "PCS", "PCS", "18.5", "2026-08-01")
                ))),
                "SOI202607240001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("数量");
    }

    @Test
    void dryRunParsesAndValidatesWithoutCallingU9() throws Exception {
        SalesOrderImportService service = new SalesOrderImportService(parser);
        MockMultipartFile file = new MockMultipartFile(
                "excelFile",
                "sales-order.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(fullHeaders(), List.of(
                        row("C001", "SO01", "PO-001", "2026-07-24", "ITEM-001", "CP-001", "零件A", 10, "PCS", "PCS", "18.5", "2026-08-01")
                ))
        );

        SalesOrderDryRunResp resp = service.dryRun(file, "SOI202607240001");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getBatchNo()).isEqualTo("SOI202607240001");
        assertThat(resp.getTotalOrderCount()).isEqualTo(1);
        assertThat(resp.getTotalLineCount()).isEqualTo(1);
        assertThat(resp.getOrders()).hasSize(1);
    }

    @Test
    void u9ServicesValidateRequiredConfigurationBeforeExternalCalls() {
        U9TokenService tokenService = new U9TokenService();
        ReflectionTestUtils.setField(tokenService, "u9BaseUrl", "https://u9.example.com/u9c");
        ReflectionTestUtils.setField(tokenService, "u9UserCode", "demo");
        ReflectionTestUtils.setField(tokenService, "u9EntCode", "001");
        ReflectionTestUtils.setField(tokenService, "u9OrgCode", "001");
        ReflectionTestUtils.setField(tokenService, "u9ClientId", "QPO");

        assertThatThrownBy(tokenService::fetchToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP_U9_CLIENT_SECRET");

        U9SalesOrderClient client = new U9SalesOrderClient();
        ReflectionTestUtils.setField(client, "u9BaseUrl", "");
        ReflectionTestUtils.setField(client, "tokenService", tokenService);

        assertThatThrownBy(() -> client.create(List.of(new U9SalesOrderHeadDTO())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ERP_U9_BASE_URL");
    }

    @Test
    void u9TokenServiceUsesActiveIntegrationConfigServiceConfig() throws IOException {
        AtomicReference<String> pathAndQuery = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            pathAndQuery.set(exchange.getRequestURI().toString());
            respond(exchange, 200, "{\"token\":\"runtime-token\"}");
        });
        try {
            U9TokenService tokenService = new U9TokenService();
            ReflectionTestUtils.setField(tokenService, "u9BaseUrl", "http://legacy-u9.invalid");
            ReflectionTestUtils.setField(tokenService, "externalIntegrationConfigService", configService(u9Config(
                    baseUrl(server),
                    "/db/AuthLogin",
                    "/legacy-create",
                    "qpo-user",
                    "ent-db",
                    "org-db",
                    "client-db",
                    "secret-db"
            )));

            String token = tokenService.fetchToken();

            assertThat(token).isEqualTo("runtime-token");
            assertThat(pathAndQuery.get())
                    .startsWith("/db/AuthLogin?")
                    .contains("userCode=qpo-user")
                    .contains("entcode=ent-db")
                    .contains("orgcode=org-db")
                    .contains("clientid=client-db")
                    .contains("clientsecret=secret-db");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void u9SalesOrderClientUsesActiveIntegrationConfigServiceConfig() throws IOException {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = server(exchange -> {
            captured.capture(exchange);
            respond(exchange, 200, """
                    {
                      "ResCode": 0,
                      "Success": true,
                      "ResMsg": "ok",
                      "Data": [
                        { "m_isSucess": true, "m_otherID": "O-1", "m_iD": "U9-ID", "m_code": "SO-001", "m_errorMsg": "" }
                      ]
                    }
                    """);
        });
        try {
            U9TokenService tokenService = mock(U9TokenService.class);
            when(tokenService.fetchToken()).thenReturn("runtime-token");
            U9SalesOrderClient client = new U9SalesOrderClient();
            ReflectionTestUtils.setField(client, "u9BaseUrl", "http://legacy-u9.invalid");
            ReflectionTestUtils.setField(client, "tokenService", tokenService);
            ReflectionTestUtils.setField(client, "externalIntegrationConfigService", configService(u9Config(
                    baseUrl(server),
                    "/legacy-auth",
                    "/db/SO/CreateOrder",
                    "qpo-user",
                    "ent-db",
                    "org-db",
                    "client-db",
                    "secret-db"
            )));

            U9ApiResult<?> result = client.create(List.of(order("O-1")));

            assertThat(result.getSuccess()).isTrue();
            assertThat(captured.method.get()).isEqualTo("POST");
            assertThat(captured.path.get()).isEqualTo("/db/SO/CreateOrder");
            assertThat(captured.token.get()).isEqualTo("runtime-token");
            assertThat(captured.body.get()).contains("\"OtherID\":\"O-1\"");
        } finally {
            server.stop(0);
        }
    }

    private List<String> fullHeaders() {
        return List.of("客户编码", "单据类型编码", "客户订单号", "业务日期", "U9料号", "客户料号", "客户品名", "数量", "销售单位", "计价单位", "单价", "交期");
    }

    private U9SalesOrderHeadDTO order(String otherId) {
        U9SalesOrderHeadDTO order = new U9SalesOrderHeadDTO();
        order.setOtherID(otherId);
        order.setOrderBy("C001");
        order.setDocumentType("SO01");
        order.setBusinessDate("2026-07-24");
        return order;
    }

    private ExternalIntegrationConfigService configService(ExternalIntegrationConfig config) {
        return new ExternalIntegrationConfigService(null) {
            @Override
            public ExternalIntegrationConfig activeConfig(ExternalSystemCode systemCode) {
                return config;
            }
        };
    }

    private ExternalIntegrationConfig u9Config(
            String baseUrl,
            String authLoginPath,
            String salesOrderCreatePath,
            String userCode,
            String entCode,
            String orgCode,
            String clientId,
            String clientSecret) {
        return new ExternalIntegrationConfig(
                301L,
                "U9 active",
                ExternalSystemCode.U9,
                true,
                true,
                ExternalAuthType.U9_AUTH_LOGIN,
                baseUrl,
                null,
                null,
                null,
                null,
                authLoginPath,
                salesOrderCreatePath,
                userCode,
                entCode,
                orgCode,
                clientId,
                clientSecret,
                1000,
                1000,
                null
        );
    }

    private List<Object> row(Object... values) {
        return List.of(values);
    }

    private byte[] excelBytes(List<String> headers, List<List<Object>> rows) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            List<List<String>> head = headers.stream().map(List::of).toList();
            EasyExcel.write(outputStream).head(head).sheet("sales-order").doWrite(rows);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> token = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        private void capture(HttpExchange exchange) throws IOException {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            token.set(exchange.getRequestHeaders().getFirst("token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
