package com.machining.quote;

import com.machining.quote.controller.SalesOrderIntegrationController;
import com.machining.quote.dto.SalesOrderSyncPageData;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesOrderIntegrationControllerTest {
    private static final String PATH = "/api/integrations/sales-orders";

    @Test
    void controllerRejectsMissingOrWrongTokenWithHttpUnauthorizedBeforeService() {
        SalesOrderIntegrationSyncService syncService = mock(SalesOrderIntegrationSyncService.class);
        SystemInboundCallbackConfigService tokenService = mock(SystemInboundCallbackConfigService.class);
        SalesOrderIntegrationController controller = controller(syncService, tokenService);
        when(tokenService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, null)).thenReturn(false);
        when(tokenService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, "Bearer wrong")).thenReturn(false);

        ResponseEntity<SalesOrderSyncResp> missing = controller.listSalesOrders(null, new SalesOrderSyncQueryReq());
        ResponseEntity<SalesOrderSyncResp> wrong = controller.listSalesOrders("Bearer wrong", new SalesOrderSyncQueryReq());

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
        assertThat(missing.getBody().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(missing.getBody().getMessage()).isEqualTo("销售订单同步接口 token 无效");
        assertThat(wrong.getBody()).isNotNull();
        assertThat(wrong.getBody().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verifyNoInteractions(syncService);
    }

    @Test
    void controllerAcceptsOpenApiTokenAndDelegatesNormalizedRequest() {
        SalesOrderIntegrationSyncService syncService = mock(SalesOrderIntegrationSyncService.class);
        SystemInboundCallbackConfigService tokenService = mock(SystemInboundCallbackConfigService.class);
        SalesOrderIntegrationController controller = controller(syncService, tokenService);
        when(tokenService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, "Bearer qpo-open-api-token")).thenReturn(true);
        when(syncService.query(any(SalesOrderSyncQueryReq.class))).thenReturn(SalesOrderSyncResp.empty(20));
        SalesOrderSyncQueryReq req = new SalesOrderSyncQueryReq();
        req.setPageSize(20);
        req.setCursor("cursor-1");
        req.setSaleOrderNo("SO-1");
        req.setMaterialNo("MAT-1");
        req.setPartNo("P-1");
        req.setVersion("A");
        req.setStatus("已报价");
        req.setCreatedFrom(LocalDateTime.of(2026, 8, 14, 10, 0));
        req.setCreatedTo(LocalDateTime.of(2026, 8, 14, 11, 0));
        req.setUpdatedFrom(LocalDateTime.of(2026, 8, 14, 12, 0));
        req.setUpdatedTo(LocalDateTime.of(2026, 8, 14, 13, 0));

        ResponseEntity<SalesOrderSyncResp> response = controller.listSalesOrders("Bearer qpo-open-api-token", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        ArgumentCaptor<SalesOrderSyncQueryReq> reqCaptor = ArgumentCaptor.forClass(SalesOrderSyncQueryReq.class);
        verify(syncService).query(reqCaptor.capture());
        SalesOrderSyncQueryReq delegated = reqCaptor.getValue();
        assertThat(delegated.getPageSize()).isEqualTo(20);
        assertThat(delegated.getCursor()).isEqualTo("cursor-1");
        assertThat(delegated.getSaleOrderNo()).isEqualTo("SO-1");
        assertThat(delegated.getMaterialNo()).isEqualTo("MAT-1");
        assertThat(delegated.getPartNo()).isEqualTo("P-1");
        assertThat(delegated.getVersion()).isEqualTo("A");
        assertThat(delegated.getStatus()).isEqualTo("已报价");
        assertThat(delegated.getCreatedFrom()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 0));
        assertThat(delegated.getUpdatedTo()).isEqualTo(LocalDateTime.of(2026, 8, 14, 13, 0));
    }

    @Test
    void controllerDefaultsPageSizeToOneHundredAndAllowsOnlyDocumentedValues() {
        SalesOrderIntegrationSyncService syncService = mock(SalesOrderIntegrationSyncService.class);
        SystemInboundCallbackConfigService tokenService = mock(SystemInboundCallbackConfigService.class);
        SalesOrderIntegrationController controller = controller(syncService, tokenService);
        when(tokenService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, "Bearer qpo-open-api-token")).thenReturn(true);
        when(syncService.query(any(SalesOrderSyncQueryReq.class))).thenAnswer(invocation -> {
            SalesOrderSyncQueryReq req = invocation.getArgument(0);
            return SalesOrderSyncResp.empty(req.getPageSize());
        });

        ResponseEntity<SalesOrderSyncResp> defaulted = controller.listSalesOrders("Bearer qpo-open-api-token", new SalesOrderSyncQueryReq());
        ResponseEntity<SalesOrderSyncResp> size50 = controller.listSalesOrders("Bearer qpo-open-api-token", req(50));
        ResponseEntity<SalesOrderSyncResp> size100 = controller.listSalesOrders("Bearer qpo-open-api-token", req(100));
        ResponseEntity<SalesOrderSyncResp> size10 = controller.listSalesOrders("Bearer qpo-open-api-token", req(10));
        ResponseEntity<SalesOrderSyncResp> size500 = controller.listSalesOrders("Bearer qpo-open-api-token", req(500));

        assertThat(defaulted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(defaulted.getBody()).isNotNull();
        assertThat(defaulted.getBody().getData().getPageSize()).isEqualTo(100);
        assertThat(size50.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(size50.getBody()).isNotNull();
        assertThat(size50.getBody().getData().getPageSize()).isEqualTo(50);
        assertThat(size100.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(size100.getBody()).isNotNull();
        assertThat(size100.getBody().getData().getPageSize()).isEqualTo(100);
        assertThat(size10.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(size10.getBody()).isNotNull();
        assertThat(size10.getBody().getMessage()).isEqualTo("pageSize 只允许 20、50、100");
        assertThat(size500.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void endpointRejectsNonNumericPageSizeWithHttpBadRequest() throws Exception {
        SalesOrderIntegrationSyncService syncService = mock(SalesOrderIntegrationSyncService.class);
        SystemInboundCallbackConfigService tokenService = mock(SystemInboundCallbackConfigService.class);
        SalesOrderIntegrationController controller = controller(syncService, tokenService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get(PATH)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer qpo-open-api-token")
                        .param("pageSize", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(syncService);
    }

    @Test
    void serviceReturnsEmptySkeletonResponse() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = new SalesOrderIntegrationSyncService();
        ReflectionTestUtils.setField(service, "salesOrderIntegrationMapper", mapper);
        SalesOrderSyncQueryReq req = req(100);

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getMessage()).isEqualTo("OK");
        SalesOrderSyncPageData data = resp.getData();
        assertThat(data).isNotNull();
        assertThat(data.getRecords()).isEqualTo(List.of());
        assertThat(data.getPageSize()).isEqualTo(100);
        assertThat(data.isHasMore()).isFalse();
        assertThat(data.getNextCursor()).isNull();
    }

    @Test
    void integrationPathIsPublicButControllerKeepsOpenApiTokenBoundary() throws Exception {
        String authInterceptor = read("src/main/java/com/machining/quote/config/AuthInterceptor.java");
        String webConfig = read("src/main/java/com/machining/quote/config/WebConfig.java");
        String controller = read("src/main/java/com/machining/quote/controller/SalesOrderIntegrationController.java");

        assertThat(authInterceptor).contains(PATH);
        assertThat(webConfig).contains(PATH);
        assertThat(controller)
                .contains("hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, authorization)")
                .doesNotContain("@RequirePermission");
    }

    private SalesOrderIntegrationController controller(
            SalesOrderIntegrationSyncService syncService,
            SystemInboundCallbackConfigService tokenService) {
        SalesOrderIntegrationController controller = new SalesOrderIntegrationController();
        ReflectionTestUtils.setField(controller, "salesOrderIntegrationSyncService", syncService);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", tokenService);
        return controller;
    }

    private SalesOrderSyncQueryReq req(Integer pageSize) {
        SalesOrderSyncQueryReq req = new SalesOrderSyncQueryReq();
        req.setPageSize(pageSize);
        return req;
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
