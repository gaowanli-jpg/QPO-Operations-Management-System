package com.machining.quote;

import com.machining.quote.controller.SalesOrderIntegrationController;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SalesOrderIntegrationGovernanceTest {
    private static final List<String> FORBIDDEN_LOG_FIELDS = List.of(
            "Bearer",
            "Authorization",
            "QPO_OPEN_API_TOKEN",
            "taxIncludedUnitPrice",
            "untaxedUnitPrice",
            "taxIncludedTotalPrice",
            "untaxedTotalPrice",
            "assemblyFee",
            "baseCost",
            "drawingUrl",
            "processRoute",
            "feeData",
            "materialData",
            "SO-20260814-001",
            "MAT-001"
    );

    @Test
    void queryWritesOnlyLowNoiseSummaryFileLogForReadOnlySync(CapturedOutput output) {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(20);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(21)))
                .thenReturn(List.of(order(1L, "SO-20260814-001")));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(
                        item(1L, "SO-20260814-001", 10, "MAT-001"),
                        item(1L, "SO-20260814-001", 20, "MAT-002")
                ));

        service.query(req);

        assertThat(output.getOut())
                .contains("Sales order sync query completed")
                .contains("caller=EXTERNAL_SALES_ORDER_SYNC")
                .contains("pageSize=20")
                .contains("orderCount=1")
                .contains("itemCount=2")
                .contains("hasMore=false")
                .contains("durationMs=")
                .doesNotContain(FORBIDDEN_LOG_FIELDS.toArray(String[]::new));
    }

    @Test
    void queryFailureLogContainsOnlyFailureSummary(CapturedOutput output) {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001")));
        SalesOrderSyncItemRow brokenItem = item(1L, "SO-20260814-001", null, "MAT-001");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(brokenItem));

        assertThatThrownBy(() -> service.query(req))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(output.getOut())
                .contains("Sales order sync query failed")
                .contains("caller=EXTERNAL_SALES_ORDER_SYNC")
                .contains("pageSize=100")
                .contains("reason=missingLineNo")
                .contains("durationMs=")
                .doesNotContain(FORBIDDEN_LOG_FIELDS.toArray(String[]::new))
                .doesNotContain("items", "records", "saleOrderNo", "saleOrderItemNo");
    }

    @Test
    void controllerAuthFailureLogDoesNotExposeAuthorizationValue(CapturedOutput output) {
        SalesOrderIntegrationSyncService syncService = mock(SalesOrderIntegrationSyncService.class);
        SystemInboundCallbackConfigService tokenService = mock(SystemInboundCallbackConfigService.class);
        SalesOrderIntegrationController controller = controller(syncService, tokenService);
        when(tokenService.hasValidOpenApiBearerToken(InboundCallbackCode.SALES_ORDER_SYNC_QUERY, "Bearer secret-token-value")).thenReturn(false);

        ResponseEntity<?> response = controller.listSalesOrders("Bearer secret-token-value", req(50));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(output.getOut())
                .contains("Sales order sync query failed")
                .contains("caller=EXTERNAL_SALES_ORDER_SYNC")
                .contains("pageSize=50")
                .contains("reason=authFailed")
                .doesNotContain("Bearer", "secret-token-value", "Authorization");
    }

    @Test
    void preflightDataGovernanceSqlBlocksRowsWithoutSalesOrderItemLineNo() throws Exception {
        String sql = read("docs/ops/sql/check_sales_order_sync_line_no_before_open.sql");

        assertThat(sql)
                .contains("sales_order_item")
                .contains("line_no IS NULL")
                .contains("NULLIF(BTRIM(soi.material_no), '') IS NULL")
                .contains("sales_order_id")
                .contains("阻断上线")
                .contains("补齐 line_no 和 material_no");
    }

    @Test
    void docsStateUnifiedTokenLowNoiseLoggingAndNoDedicatedSalesOrderSyncToken() throws Exception {
        String permissionDoc = read("docs/QPO权限用户体系/02-权限点清单与现有角色映射.md");
        String loggingDoc = read("docs/QPO日志模块/03-日志运维说明.md");
        String systemConfigDoc = read("docs/QPO系统配置/04-系统配置出站入站接口拆分与MES回调配置开发文档.md");

        assertThat(permissionDoc)
                .contains("/api/integrations/sales-orders")
                .contains("QPO 入站开放接口统一 token")
                .doesNotContain("销售订单同步专用 token");
        assertThat(loggingDoc)
                .contains("Sales order sync query completed")
                .contains("caller=EXTERNAL_SALES_ORDER_SYNC")
                .contains("不写普通业务操作日志")
                .doesNotContain("Authorization header 样例值");
        assertThat(systemConfigDoc)
                .contains("SALES_ORDER_SYNC_QUERY")
                .contains("/api/integrations/sales-orders")
                .contains("QPO_OPEN_API_TOKEN")
                .contains("不新增独立 token");
    }

    @Test
    void salesOrderSyncReadOnlyBoundaryDoesNotTriggerMesWmsU9OrStatusMutations() throws Exception {
        String service = read("quote-backend/src/main/java/com/machining/quote/service/SalesOrderIntegrationSyncService.java");
        String controller = read("quote-backend/src/main/java/com/machining/quote/controller/SalesOrderIntegrationController.java");
        String sqlProvider = read("quote-backend/src/main/java/com/machining/quote/mapper/SalesOrderIntegrationSqlProvider.java");
        String combined = service + controller + sqlProvider;

        assertThat(combined)
                .doesNotContain("MesIntegration")
                .doesNotContain("Wms")
                .doesNotContain("U9")
                .doesNotContain("AuditService")
                .doesNotContain("@RequirePermission");
        assertThat(sqlProvider.toUpperCase())
                .doesNotContain("UPDATE ")
                .doesNotContain("INSERT ")
                .doesNotContain("DELETE ");
    }

    private SalesOrderIntegrationSyncService service(SalesOrderIntegrationMapper mapper) {
        SalesOrderIntegrationSyncService service = new SalesOrderIntegrationSyncService();
        ReflectionTestUtils.setField(service, "salesOrderIntegrationMapper", mapper);
        return service;
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

    private SalesOrderSyncOrderRow order(Long id, String saleOrderNo) {
        SalesOrderSyncOrderRow row = new SalesOrderSyncOrderRow();
        row.setSalesOrderId(id);
        row.setSaleOrderNo(saleOrderNo);
        row.setOrderType("FORMAL");
        row.setStatus("APPROVED");
        row.setChangedAt(LocalDateTime.of(2026, 8, 14, 10, 0));
        return row;
    }

    private SalesOrderSyncItemRow item(Long orderId, String saleOrderNo, Integer lineNo, String materialNo) {
        SalesOrderSyncItemRow row = new SalesOrderSyncItemRow();
        row.setSalesOrderId(orderId);
        row.setLineNo(lineNo);
        row.setMaterialNo(materialNo);
        return row;
    }

    private String read(String path) throws Exception {
        Path target = Path.of(path);
        if (!Files.exists(target)) {
            target = Path.of("..").resolve(path).normalize();
        }
        return Files.readString(target, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
