package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.controller.MesIntegrationCallbackController;
import com.machining.quote.controller.SalesOrderIntegrationController;
import com.machining.quote.dto.SalesOrderSyncItemDTO;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderDTO;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.dto.SystemInboundCallbackTokenResp;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.entity.SystemInboundCallbackConfig;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import com.machining.quote.mapper.SystemInboundCallbackConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesOrderIntegrationRegressionTest {
    private static final String OLD_TOKEN = "old-open-api-token";
    private static final String SALES_SYNC_PATH = "/api/integrations/sales-orders";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void rotatedOpenApiTokenInvalidatesOldTokenAcrossSalesOrderSyncAndMesCallbackContracts() {
        SystemInboundCallbackConfigService tokenService = tokenServiceWithOldToken();
        SystemInboundCallbackTokenResp rotated = tokenService.rotateOpenApiToken();
        String oldAuth = "Bearer " + OLD_TOKEN;
        String newAuth = "Bearer " + rotated.getToken();

        SalesOrderIntegrationSyncService salesService = mock(SalesOrderIntegrationSyncService.class);
        when(salesService.query(any(SalesOrderSyncQueryReq.class))).thenReturn(SalesOrderSyncResp.empty(100));
        SalesOrderIntegrationController salesController = salesController(salesService, tokenService);

        ResponseEntity<SalesOrderSyncResp> oldSales = salesController.listSalesOrders(oldAuth, req(100));
        ResponseEntity<SalesOrderSyncResp> newSales = salesController.listSalesOrders(newAuth, req(100));

        assertThat(oldSales.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(newSales.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(salesService).query(any(SalesOrderSyncQueryReq.class));

        MesMaterialSyncService mesService = mock(MesMaterialSyncService.class);
        when(mesService.handleMaterialBatchCallback(any(MesMaterialCallbackReq.class))).thenReturn(mesSuccess());
        MesIntegrationCallbackController mesController = mesController(mesService, tokenService);

        ResponseEntity<MesMaterialCallbackResp> oldMes = mesController.materialCallback(oldAuth, mesReq());

        assertThat(oldMes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(oldMes.getBody()).isNotNull();
        assertThat(oldMes.getBody().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verifyNoInteractions(mesService);

        ResponseEntity<MesMaterialCallbackResp> newMes = mesController.materialCallback(newAuth, mesReq());

        assertThat(newMes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(newMes.getBody()).isNotNull();
        assertThat(newMes.getBody().getCode()).isEqualTo(0);
        verify(mesService).handleMaterialBatchCallback(any(MesMaterialCallbackReq.class));
    }

    @Test
    void fullSyncReturnsMesDemandSlimBusinessKeysAndStableNextCursor() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(2);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(3))).thenReturn(List.of(
                order(1L, "SO-20260814-001", LocalDateTime.of(2026, 8, 14, 9, 0)),
                order(2L, "SO-20260814-002", LocalDateTime.of(2026, 8, 14, 9, 5)),
                order(3L, "SO-20260814-003", LocalDateTime.of(2026, 8, 14, 9, 10))
        ));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L, 2L)), eq(req))).thenReturn(List.of(
                item(1L, "SO-20260814-001", 10, "P-001", "A", "MAT-001"),
                item(2L, "SO-20260814-002", 20, "P-002", "B", "MAT-002")
        ));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.getData().isHasMore()).isTrue();
        assertThat(resp.getData().getRecords()).hasSize(2);
        SalesOrderSyncOrderDTO firstOrder = resp.getData().getRecords().get(0);
        SalesOrderSyncItemDTO firstItem = firstOrder.getItems().get(0);
        assertThat(firstOrder.getSaleOrderNo()).isEqualTo("SO-20260814-001");
        assertThat(firstOrder.getOrderType()).isEqualTo("正式订单");
        assertThat(firstItem.getSaleOrderItemNo()).isEqualTo("10");
        assertThat(firstItem.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getReleasedQty()).isEqualTo(2);
        assertThat(firstItem.getDescription()).isEqualTo("MES production demand item");
        assertThat(firstItem.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(firstItem.getRemark()).isEqualTo("MES remark");
        assertThat(objectMapper.writeValueAsString(firstOrder))
                .doesNotContain("customerName", "documentNo", "partNo", "version", "qty", "materialName", "remainingQty");
        assertThat(objectMapper.writeValueAsString(firstItem)).doesNotContain("orderType");

        JsonNode cursor = decodeCursor(resp.getData().getNextCursor());
        assertThat(cursor.get("changedAt").asText()).isEqualTo("2026-08-14T09:05:00");
        assertThat(cursor.get("salesOrderId").asLong()).isEqualTo(2L);
    }

    @Test
    void incrementalAndConditionBackfillRequestsPreserveFiltersForMapperAndReturnOnlyMatchedRows() {
        assertSingleMatchedRow(req -> req.setUpdatedFrom(LocalDateTime.of(2026, 8, 14, 8, 0)));
        assertSingleMatchedRow(req -> req.setSaleOrderNo("SO-20260814-001"));
        assertSingleMatchedRow(req -> req.setMaterialNo("MAT-ONLY"));
        assertSingleMatchedRow(req -> {
            req.setPartNo("P-ONLY");
            req.setVersion("B");
        });
    }

    @Test
    void completionRecordDocumentsRegressionEvidenceAndReadOnlyBoundary() throws Exception {
        String doc = read("docs/QPO销售订单外部同步/01-销售订单外部系统同步接口开发文档.md");

        assertThat(doc)
                .contains("### 阶段 8：联调与回归")
                .contains("阶段 8 验收记录")
                .contains(".\\mvnw.cmd test")
                .contains("npm test")
                .contains("npm run build")
                .contains("首次全量同步")
                .contains("updatedFrom")
                .contains("cursor + pageSize")
                .contains("saleOrderNo + saleOrderItemNo")
                .contains("旧 token 访问销售订单同步返回 `401`")
                .contains("旧 token 访问 MES 回调返回 HTTP `200` 且响应体 `code=401`")
                .contains("不触发 MES / WMS / U9")
                .contains("不修改销售订单状态");
    }

    private void assertSingleMatchedRow(java.util.function.Consumer<SalesOrderSyncQueryReq> customizer) {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        customizer.accept(req);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", LocalDateTime.of(2026, 8, 14, 10, 0))));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item(1L, "SO-20260814-001", 10, "P-ONLY", "B", "MAT-ONLY")));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.getData().getRecords()).hasSize(1);
        assertThat(resp.getData().getRecords().get(0).getItems()).hasSize(1);
        assertThat(resp.getData().getRecords().get(0).getItems().get(0).getSaleOrderItemNo()).isEqualTo("10");
        verify(mapper).selectOrderPage(eq(req), eq(null), eq(null), eq(101));
        verify(mapper).selectItemsByOrderIds(eq(List.of(1L)), eq(req));
    }

    private SalesOrderIntegrationController salesController(
            SalesOrderIntegrationSyncService salesService,
            SystemInboundCallbackConfigService tokenService) {
        SalesOrderIntegrationController controller = new SalesOrderIntegrationController();
        ReflectionTestUtils.setField(controller, "salesOrderIntegrationSyncService", salesService);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", tokenService);
        return controller;
    }

    private MesIntegrationCallbackController mesController(
            MesMaterialSyncService mesService,
            SystemInboundCallbackConfigService tokenService) {
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", mesService);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", tokenService);
        return controller;
    }

    private SalesOrderIntegrationSyncService service(SalesOrderIntegrationMapper mapper) {
        SalesOrderIntegrationSyncService service = new SalesOrderIntegrationSyncService();
        ReflectionTestUtils.setField(service, "salesOrderIntegrationMapper", mapper);
        return service;
    }

    private SystemInboundCallbackConfigService tokenServiceWithOldToken() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        AtomicReference<SystemInboundCallbackConfig> current = new AtomicReference<>(configWithOldToken());
        when(mapper.selectOne(any(QueryWrapper.class))).thenAnswer(invocation -> current.get());
        when(mapper.updateById(any(SystemInboundCallbackConfig.class))).thenAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return 1;
        });
        SystemInboundCallbackConfigService service = new SystemInboundCallbackConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "fallbackPublicBaseUrl", "http://qpo.internal");
        ReflectionTestUtils.setField(service, "fallbackCallbackToken", "");
        ReflectionTestUtils.setField(service, "fallbackCallbackTokenEnabled", true);
        return service;
    }

    private SystemInboundCallbackConfig configWithOldToken() {
        SystemInboundCallbackConfig entity = new SystemInboundCallbackConfig();
        entity.setId(88L);
        entity.setCallbackCode("MES_MATERIAL_CALLBACK");
        entity.setSourceSystemCode("MES");
        entity.setName("MES物料号回调");
        entity.setEnabled(true);
        entity.setTokenEnabled(true);
        entity.setPublicBaseUrl("http://qpo.internal");
        entity.setCallbackPath("/api/integrations/mes/material-callback");
        entity.setTokenHash(sha256Hex(OLD_TOKEN));
        entity.setTokenMask("***oken");
        return entity;
    }

    private SalesOrderSyncQueryReq req(int pageSize) {
        SalesOrderSyncQueryReq req = new SalesOrderSyncQueryReq();
        req.setPageSize(pageSize);
        return req;
    }

    private SalesOrderSyncOrderRow order(Long id, String saleOrderNo, LocalDateTime changedAt) {
        SalesOrderSyncOrderRow row = new SalesOrderSyncOrderRow();
        row.setSalesOrderId(id);
        row.setSaleOrderNo(saleOrderNo);
        row.setInternalProjectNo("IP-001");
        row.setOrderType("正式订单");
        row.setStatus("APPROVED");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 14, 8, 0));
        row.setChangedAt(changedAt);
        return row;
    }

    private SalesOrderSyncItemRow item(Long orderId, String saleOrderNo, Integer lineNo, String partNo, String version, String materialNo) {
        SalesOrderSyncItemRow row = new SalesOrderSyncItemRow();
        row.setSalesOrderId(orderId);
        row.setLineNo(lineNo);
        row.setMaterialNo(materialNo);
        row.setReleasedQty(2);
        row.setDescription("MES production demand item");
        row.setSurfaceTreatment("Anodized");
        row.setRemark("MES remark");
        return row;
    }

    private MesMaterialCallbackReq mesReq() {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("QPO-MAT-20260814-001");
        return req;
    }

    private MesMaterialCallbackResp mesSuccess() {
        MesMaterialCallbackResp resp = new MesMaterialCallbackResp();
        resp.setSuccess(true);
        resp.setCode(0);
        resp.setMsg("OK");
        return resp;
    }

    private JsonNode decodeCursor(String cursor) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(cursor));
    }

    private String read(String path) throws Exception {
        Path target = Path.of(path);
        if (!Files.exists(target)) {
            target = Path.of("..").resolve(path).normalize();
        }
        return Files.readString(target, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
