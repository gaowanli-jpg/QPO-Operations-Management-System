package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.dto.SalesOrderU9PriceMode;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9SyncWorkflowTest {
    @Test
    void missingConfirmedTicketMaterialNoFailsBeforeCallingU9AndWritesFailureSummary() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setMaterialNo(null);
        item.setMaterialNoStatus("未确认");
        fixture.withOrder(header, List.of(item));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getMessage()).contains("物料号");
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.isSuccess()).isFalse();
                    assertThat(row.getSalesOrderId()).isEqualTo(header.getId());
                    assertThat(row.getErrorMessage()).contains("物料号");
        });
        verify(fixture.u9Client, never()).create(any());

        SalesOrderHeader failureUpdate = fixture.lastHeaderUpdate();
        assertThat(failureUpdate.getId()).isEqualTo(header.getId());
        assertThat(failureUpdate.getU9SyncStatus()).isEqualTo("同步失败");
        assertThat(failureUpdate.getU9SyncMessage()).contains("物料号");
    }

    @Test
    void onlyUntaxedPriceFailsBeforeCallingDataCenterOrU9ByDefault() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setTaxIncludedUnitPrice(null);
        markConfirmed(item, "MAT-CONFIRMED-001", 4);
        fixture.withOrder(header, List.of(item));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getMessage()).contains("含税单价为空");
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getErrorMessage()).contains("含税单价为空"));
        verify(fixture.u9Client, never()).create(any());

        SalesOrderHeader failureUpdate = fixture.lastHeaderUpdate();
        assertThat(failureUpdate.getU9SyncStatus()).isEqualTo("同步失败");
        assertThat(failureUpdate.getU9SyncMessage()).contains("含税单价为空");
    }

    @Test
    void successfulSyncBuildsU9RequestFromSalesOrderItemsAndWritesU9Codes() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        header.setDocumentTypeCode("SO88");
        header.setCustomerCode("CUST-001");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setUntaxedUnitPrice(new BigDecimal("16.37"));
        markConfirmed(item, "MAT-CONFIRMED-001", 4);
        fixture.withOrder(header, List.of(item));
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(header.getSalesOrderNo(), true, "U9-ID-1", "U9-SO-001", "")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isZero();
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getU9OrderId()).isEqualTo("U9-ID-1");
                    assertThat(row.getU9OrderCode()).isEqualTo("U9-SO-001");
                });

        U9SalesOrderHeadDTO request = fixture.capturedU9Orders().get(0);
        assertThat(request.getOtherID()).isEqualTo(header.getSalesOrderNo());
        assertThat(request.getDocNo()).isEqualTo(header.getDocumentNo());
        assertThat(request.getOrderBy()).isEqualTo("CUST-001");
        assertThat(request.getDocumentType()).isEqualTo("SO88");
        assertThat(request.getIsPriceIncludeTax()).isTrue();
        assertThat(request.getSOLineDTOList()).singleElement()
                .satisfies(line -> {
                    assertThat(line.getItemCode()).isEqualTo("MAT-CONFIRMED-001");
                    assertThat(line.getOrderByQtyTU()).isEqualByComparingTo("4");
                    assertThat(line.getFinallyPriceTC()).isEqualByComparingTo("18.50");
                    assertThat(line.getDescFlexField().getPrivateDescSeg2()).isEqualTo(header.getCustomerOrderNo());
                    assertThat(line.getDescFlexField().getPrivateDescSeg3()).isEqualTo(header.getInternalProjectNo());
                    assertThat(line.getDescFlexField().getPrivateDescSeg5()).isEqualTo("2026-08-08");
                    assertThat(line.getDescFlexField().getPrivateDescSeg9()).isEqualTo("18.5");
                    assertThat(line.getSOShiplineDTOList()).singleElement()
                            .satisfies(shipLine -> {
                                assertThat(shipLine.getItemCode()).isEqualTo("MAT-CONFIRMED-001");
                                assertThat(shipLine.getShipPlanQtyTU()).isEqualByComparingTo("4");
                                assertThat(shipLine.getRequireDate()).isEqualTo("2026-08-09");
                                assertThat(shipLine.getDeliveryDate()).isEqualTo("2026-08-08");
                            });
                });

        SalesOrderHeader successUpdate = fixture.lastHeaderUpdate();
        assertThat(successUpdate.getU9SyncStatus()).isEqualTo("已同步");
        assertThat(successUpdate.getU9OrderId()).isEqualTo("U9-ID-1");
        assertThat(successUpdate.getU9OrderCode()).isEqualTo("U9-SO-001");
        assertThat(fixture.headerUpdates()).anySatisfy(update -> assertThat(update.getU9SyncRequestHash()).isNotBlank());
        assertThat(successUpdate.getU9SyncMessage()).contains("同步成功");
        assertThat(successUpdate.getDocumentNo()).isNull();
    }

    @Test
    void taxIncludedPriceModeBuildsU9RequestFromTaxIncludedUnitPrice() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setUntaxedUnitPrice(null);
        item.setTaxIncludedUnitPrice(new BigDecimal("18.50"));
        markConfirmed(item, "MAT-CONFIRMED-001", 4);
        fixture.withOrder(header, List.of(item));
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(header.getSalesOrderNo(), true, "U9-ID-1", "U9-SO-001", "")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()), SalesOrderU9PriceMode.TAX_INCLUDED);

        assertThat(resp.isSuccess()).isTrue();
        U9SalesOrderHeadDTO request = fixture.capturedU9Orders().get(0);
        assertThat(request.getIsPriceIncludeTax()).isTrue();
        assertThat(request.getSOLineDTOList()).singleElement()
                .satisfies(line -> {
                    assertThat(line.getFinallyPriceTC()).isEqualByComparingTo("18.50");
                    assertThat(line.getDescFlexField().getPrivateDescSeg9()).isEqualTo("18.5");
                });
    }

    @Test
    void partialU9FailureUpdatesEachSalesOrderIndependently() {
        Fixture fixture = fixture();
        SalesOrderHeader first = header(9001L, "SOQPO-001", "未同步");
        SalesOrderHeader second = header(9002L, "SOQPO-002", "未同步");
        SalesOrderItem firstItem = item(5001L, first.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        SalesOrderItem secondItem = item(5002L, second.getId(), "SALES-MAT-002", new BigDecimal("21.00"));
        markConfirmed(firstItem, "MAT-001", 2);
        markConfirmed(secondItem, "MAT-002", 3);
        fixture.withOrders(
                List.of(first, second),
                List.of(List.of(firstItem), List.of(secondItem))
        );
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(first.getSalesOrderNo(), true, "U9-ID-1", "U9-SO-001", ""),
                u9Row(second.getSalesOrderNo(), false, null, null, "U9 item unavailable")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(first.getId(), second.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(resp.getRows()).extracting("salesOrderNo")
                .containsExactly("SOQPO-001", "SOQPO-002");
        assertThat(resp.getRows().get(1).getErrorMessage()).contains("U9 item unavailable");

        List<SalesOrderHeader> updates = fixture.headerUpdates();
        assertThat(updates).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(first.getId());
            assertThat(update.getU9SyncStatus()).isEqualTo("已同步");
        });
        assertThat(updates).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(second.getId());
            assertThat(update.getU9SyncStatus()).isEqualTo("同步失败");
            assertThat(update.getU9SyncMessage()).contains("U9 item unavailable");
        });
    }

    @Test
    void failedOrderCanBeRetriedButAlreadySyncedOrderIsNotCreatedAgain() {
        Fixture fixture = fixture();
        SalesOrderHeader failed = header(9001L, "SOQPO-001", "同步失败");
        failed.setU9SyncMessage("previous U9 timeout");
        SalesOrderHeader synced = header(9002L, "SOQPO-002", "已同步");
        synced.setU9OrderId("OLD-ID");
        synced.setU9OrderCode("OLD-CODE");
        SalesOrderItem item = item(5001L, failed.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        markConfirmed(item, "MAT-001", 1);
        fixture.withOrders(
                List.of(failed, synced),
                List.of(List.of(item), List.of())
        );
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(failed.getSalesOrderNo(), true, "NEW-ID", "NEW-CODE", "")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(failed.getId(), synced.getId()));

        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(fixture.capturedU9Orders()).extracting(U9SalesOrderHeadDTO::getOtherID)
                .containsExactly(failed.getSalesOrderNo());
        assertThat(resp.getRows()).anySatisfy(row -> {
            assertThat(row.getSalesOrderId()).isEqualTo(synced.getId());
            assertThat(row.isSuccess()).isFalse();
            assertThat(row.getErrorMessage()).contains("已同步");
        });
    }

    @Test
    void auditActionsAndControllerPermissionCloseU9SyncBoundary() {
        assertThat(AuditService.inferModule("SALES_ORDER_U9_SYNC")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_SYNC")).contains("销售订单").contains("U9");
        assertThat(AuditService.inferModule("SALES_ORDER_U9_ASSIGNMENT_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_ASSIGNMENT_UPDATE"))
                .contains("销售订单").contains("U9").contains("销售员").contains("部门");
        assertThat(AuditService.inferModule("SALES_ORDER_U9_RECONCILE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_RECONCILE"))
                .contains("销售订单").contains("U9").contains("核对");
        assertThat(readSource("service/SalesOrderU9SyncService.java"))
                .doesNotContain("OrderDeliveryTicketMapper", "loadTickets(", "ticketMapper.selectList", "order_delivery_ticket")
                .contains("private static final String ACTION_ASSIGNMENT = \"SALES_ORDER_U9_ASSIGNMENT_UPDATE\"")
                .contains("private static final String ACTION_RECONCILE = \"SALES_ORDER_U9_RECONCILE\"")
                .contains("auditAssignment(header, before, assignmentSnapshot(saved))")
                .contains("auditReconcile(prepared, result, startedAt)")
                .contains("assertDataScope(header)");
        String controller = readSource("controller/SalesOrderU9SyncController.java");
        assertThat(controller)
                .contains("@GetMapping(\"/options\")")
                .contains("@GetMapping(\"/orders/{salesOrderId}/preview\")")
                .contains("@RequirePermission(\"market:u9-sync:view\")")
                .contains("@PutMapping(\"/orders/{salesOrderId}/assignment\")")
                .contains("@PostMapping(\"/orders/{salesOrderId}/reconcile\")")
                .contains("@PostMapping(\"/orders/{salesOrderId}/sync\")")
                .contains("@PostMapping(\"/sync\")")
                .contains("@RequirePermission(\"market:u9-sync:submit\")");
        assertThat(readSource("service/SalesOrderU9SyncStateService.java"))
                .contains("public PreparedOrder reserve(Long salesOrderId)")
                .contains("public PreparedOrder prepareForReconcile(Long salesOrderId)")
                .contains("assertDataScope(header)");
        assertThat(readSource("controller/SalesOrderQpoImportController.java"))
                .doesNotContain("@PostMapping(\"/u9-sync\")");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/com/machining/quote/controller/SalesOrderU9SyncCompatibilityController.java")))
                .as("legacy sales-order U9 compatibility controller should be removed")
                .isFalse();
    }

    @Test
    void docsAndSourceKeepU9SyncSecurityContractCurrent() {
        String doc16 = readDoc("docs/QPO报价状态流转/16-销售订单U9同步模块开发文档.md");
        String doc37 = readDoc("docs/QPO报价状态流转/37-RFQ议价提单派生销售订单并接入U9开发文档.md");
        String permissionList = readDoc("docs/QPO权限用户体系/02-权限点清单与现有角色映射.md");
        String permissionOps = readDoc("docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md");
        String logOps = readDoc("docs/QPO日志模块/03-日志运维说明.md");
        String fileLogSpec = readDoc("docs/QPO日志模块/04-主流程文件日志规范化开发文档.md");
        String dataCenterDoc = readDoc("docs/QPO系统配置/05-数据中心统一对接开发文档.md");
        String frontendU9Sync = readFrontend("components/SalesOrderU9Sync.vue");

        assertThat(doc16)
                .contains("U9 销售订单默认使用含税单价")
                .contains("可由操作员切换为未税单价")
                .contains("SALES_ORDER_U9_ASSIGNMENT_UPDATE")
                .contains("SALES_ORDER_U9_RECONCILE")
                .contains("预览和选项读取不写操作日志")
                .doesNotContain("U9 价格只读取 `sales_order_item.tax_included_unit_price`")
                .doesNotContain("`OrderPriceTC` 和 `FinallyPriceTC` 必须同时等于 `tax_included_unit_price`")
                .doesNotContain("CustomerItemNo");
        assertThat(doc37)
                .contains("U9 同步最终以 42 号文档的最小已验证契约为准")
                .contains("FinallyPriceTC` 默认使用 `sales_order_item.tax_included_unit_price`")
                .contains("可切换为 `sales_order_item.untaxed_unit_price`")
                .doesNotContain("销售订单 U9 继续只使用正式含税价格字段")
                .doesNotContain("OrderPriceTC` 和 `FinallyPriceTC` 等于含税单价")
                .doesNotContain("含税单价为空时 U9 不调用外部系统");
        assertThat(permissionList)
                .contains("保存 Seller / SaleDepartment 和核对 U9 结果仍复用 `market:u9-sync:submit`")
                .doesNotContain("| `market:u9-sync:assignment`", "| `market:u9-sync:reconcile`", "| `common:u9`");
        assertThat(frontendU9Sync)
                .contains("const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'")
                .doesNotContain("market:u9-sync:assignment", "market:u9-sync:reconcile", "common:u9");
        assertThat(permissionOps)
                .contains("U9同步不新增权限")
                .contains("后端 `@RequirePermission` 与销售订单数据范围是最终边界");
        assertThat(logOps)
                .contains("SALES_ORDER_U9_ASSIGNMENT_UPDATE")
                .contains("SALES_ORDER_U9_RECONCILE")
                .contains("预览、选项读取和列表刷新不写操作日志");
        assertThat(fileLogSpec)
                .contains("Sales order U9 sync reconcile")
                .contains("不得记录完整 U9 请求体、完整 U9 响应体、Authorization header、token、Cookie");
        assertThat(dataCenterDoc)
                .contains("U9_COMMON_ENTITY_QUERY")
                .contains("/api/v1/u9/CommonEntity/Query")
                .contains("U9_SALES_ORDER_SYNC` 和 `U9_COMMON_ENTITY_QUERY` 的 timeout 不低于 `60000ms`");

        String combinedSource = String.join("\n",
                readSource("service/SalesOrderU9SyncService.java"),
                readSource("service/SalesOrderU9SyncStateService.java"),
                readSource("service/u9/U9SalesOrderClient.java"),
                readSource("service/u9/U9CommonEntityClient.java"));
        assertThat(combinedSource)
                .contains("sanitize(error.getMessage())")
                .contains("safeError(result.message())")
                .doesNotContain("Data.Headers")
                .doesNotContain("Authorization: Bearer")
                .doesNotContain("client_secret=");
    }

    private Fixture fixture() {
        SalesOrderU9SyncService service = new SalesOrderU9SyncService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", fixture.u9Client);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "syncStateService", fixture.stateService);
        ReflectionTestUtils.setField(service, "reconciliationService", fixture.reconciliationService);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentNo("DOC-STABLE-" + id);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-001");
        header.setCustomerName("测试客户");
        header.setCustomerOrderNo("PO-" + id);
        header.setCustomerProjectNo("CP-" + id);
        header.setInternalProjectNo("IP-" + id);
        header.setSalesOrderDate(LocalDate.of(2026, 7, 30));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(u9Status);
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String salesMaterialNo, BigDecimal price) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(7);
        item.setPartNo("P-" + id);
        item.setDescription("零件 " + id);
        item.setSalesMaterialNo(salesMaterialNo);
        item.setTaxIncludedUnitPrice(price);
        item.setUntaxedUnitPrice(price);
        item.setVersion("A");
        item.setQty(1);
        item.setReleasedQty(1);
        item.setRemainingQty(0);
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 8));
        item.setCustomerDeliveryDate(LocalDate.of(2026, 8, 9));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private void markConfirmed(SalesOrderItem item, String materialNo, int qty) {
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus("已确认");
        item.setMaterialNoSource("MES_TICKET");
        item.setQty(qty);
        item.setReleasedQty(qty);
        item.setRemainingQty(0);
    }

    @SafeVarargs
    private U9ApiResult<U9ResultDTO> apiResult(int resCode, U9ResultDTO... rows) {
        U9ApiResult<U9ResultDTO> result = new U9ApiResult<>();
        result.setResCode(resCode);
        result.setSuccess(resCode == 0);
        result.setResMsg(resCode == 0 ? "ok" : "failed");
        result.setData(List.of(rows));
        return result;
    }

    private U9ResultDTO u9Row(String otherId, boolean success, String u9Id, String u9Code, String error) {
        U9ResultDTO row = new U9ResultDTO();
        row.setMOtherID(otherId);
        row.setMIsSucess(success);
        row.setMID(u9Id);
        row.setMCode(u9Code);
        row.setMErrorMsg(error);
        return row;
    }

    private String readSource(String relativePath) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/java/com/machining/quote", relativePath),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readDoc(String relativePath) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("..").resolve(relativePath).normalize(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readFrontend(String relativePath) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("..", "quote-frontend", "src").resolve(relativePath).normalize(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Fixture {
        private final SalesOrderU9SyncService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final U9SalesOrderClient u9Client = mock(U9SalesOrderClient.class);
        private final AuditService auditService = mock(AuditService.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final SalesOrderU9SyncStateService stateService = mock(SalesOrderU9SyncStateService.class);
        private final U9SalesOrderReconciliationService reconciliationService = mock(U9SalesOrderReconciliationService.class);
        private final U9SalesOrderRequestAssembler assembler = new U9SalesOrderRequestAssembler();
        private final U9SalesOrderContractValidator validator = new U9SalesOrderContractValidator();

        private Fixture(SalesOrderU9SyncService service) {
            this.service = service;
            when(reconciliationService.checkExisting(any()))
                    .thenReturn(U9SalesOrderReconciliationService.Result.notFoundConfirmed("创建前未找到同 DocNo U9 订单"));
            doAnswer(invocation -> reserve(invocation.getArgument(0)))
                    .when(stateService).reserve(any());
            doAnswer(invocation -> reserve(invocation.getArgument(0), invocation.getArgument(1)))
                    .when(stateService).reserve(any(), any());
            doAnswer(invocation -> {
                SalesOrderU9SyncStateService.PreparedOrder prepared = invocation.getArgument(0);
                String u9OrderId = invocation.getArgument(1);
                String u9OrderCode = invocation.getArgument(2);
                SalesOrderHeader update = new SalesOrderHeader();
                update.setId(prepared.header().getId());
                update.setU9SyncStatus("已同步");
                update.setU9OrderId(u9OrderId);
                update.setU9OrderCode(u9OrderCode);
                update.setU9SyncMessage(invocation.getArgument(3));
                headerMapper.updateById(update);
                return null;
            }).when(stateService).markSuccess(any(), any(), any(), any());
            doAnswer(invocation -> {
                SalesOrderU9SyncStateService.PreparedOrder prepared = invocation.getArgument(0);
                SalesOrderHeader update = new SalesOrderHeader();
                update.setId(prepared.header().getId());
                update.setU9SyncStatus("同步失败");
                update.setU9SyncMessage(invocation.getArgument(1));
                headerMapper.updateById(update);
                return null;
            }).when(stateService).markFailed(any(), any());
            doAnswer(invocation -> {
                SalesOrderU9SyncStateService.PreparedOrder prepared = invocation.getArgument(0);
                SalesOrderHeader update = new SalesOrderHeader();
                update.setId(prepared.header().getId());
                update.setU9SyncStatus("结果待确认");
                update.setU9SyncMessage(invocation.getArgument(1));
                headerMapper.updateById(update);
                return null;
            }).when(stateService).markPending(any(), any());
        }

        private SalesOrderU9SyncStateService.PreparedOrder reserve(Long salesOrderId) {
            return reserve(salesOrderId, SalesOrderU9PriceMode.TAX_INCLUDED);
        }

        private SalesOrderU9SyncStateService.PreparedOrder reserve(Long salesOrderId, SalesOrderU9PriceMode priceMode) {
            SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
            if (header == null) {
                throw new RuntimeException("销售订单不存在");
            }
            if ("已同步".equals(header.getU9SyncStatus())) {
                throw new RuntimeException("销售订单已同步，不能重复创建 U9 订单");
            }
            List<SalesOrderItem> items = itemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
            U9SalesOrderContractValidator.ValidationResult validation = validator.validate(header, items, priceMode);
            if (validation.hasBlockingErrors()) {
                SalesOrderHeader update = new SalesOrderHeader();
                update.setId(header.getId());
                update.setU9SyncStatus("同步失败");
                update.setU9SyncMessage(validation.firstBlockingError());
                headerMapper.updateById(update);
                throw new RuntimeException(validation.firstBlockingError());
            }
            U9SalesOrderHeadDTO request = assembler.assemble(header, items, priceMode);
            String hash = assembler.fingerprint(request);
            SalesOrderHeader update = new SalesOrderHeader();
            update.setId(header.getId());
            update.setU9SyncStatus("同步中");
            update.setU9SyncRequestHash(hash);
            update.setU9SyncMessage("U9 同步已预约，等待外部创建");
            headerMapper.updateById(update);
            return new SalesOrderU9SyncStateService.PreparedOrder(
                    header,
                    items,
                    request,
                    "ATT-" + salesOrderId,
                    hash,
                    LocalDateTime.of(2026, 8, 11, 14, 30)
            );
        }

        private void withOrder(SalesOrderHeader header, List<SalesOrderItem> items) {
            withOrders(List.of(header), List.of(items));
        }

        private void withOrders(
                List<SalesOrderHeader> headers,
                List<List<SalesOrderItem>> items) {
            for (SalesOrderHeader header : headers) {
                when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            }
            when(itemMapper.selectList(any())).thenReturn(items.get(0), items.subList(1, items.size()).toArray(List[]::new));
        }

        private List<U9SalesOrderHeadDTO> capturedU9Orders() {
            ArgumentCaptor<List<U9SalesOrderHeadDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(u9Client, atLeastOnce()).create(captor.capture());
            List<U9SalesOrderHeadDTO> orders = new ArrayList<>();
            for (List<U9SalesOrderHeadDTO> value : captor.getAllValues()) {
                orders.addAll(value);
            }
            return orders;
        }

        private SalesOrderHeader lastHeaderUpdate() {
            List<SalesOrderHeader> updates = headerUpdates();
            return updates.get(updates.size() - 1);
        }

        private List<SalesOrderHeader> headerUpdates() {
            ArgumentCaptor<SalesOrderHeader> captor = ArgumentCaptor.forClass(SalesOrderHeader.class);
            verify(headerMapper, atLeastOnce()).updateById(captor.capture());
            return captor.getAllValues();
        }
    }
}
