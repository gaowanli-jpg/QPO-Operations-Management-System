package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderU9ReconcileResp;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.U9SyncDepartmentOptionCache;
import com.machining.quote.entity.U9SyncSellerOptionCache;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.U9SyncDepartmentOptionCacheMapper;
import com.machining.quote.mapper.U9SyncSellerOptionCacheMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.SalesOrderU9SyncStateService;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9OrgCodeResolver;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9SyncReconciliationTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T06:30:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Test
    void syncOrchestrationRunsOutsideTransactionAndReconcileEndpointUsesSubmitPermission() throws Exception {
        Method syncMethod = SalesOrderU9SyncService.class.getMethod("syncSalesOrders", List.class);
        Method reserveMethod = SalesOrderU9SyncStateService.class.getMethod("reserve", Long.class);
        Method reconcileMethod = SalesOrderU9SyncController.class.getMethod("reconcile", Long.class);

        assertThat(syncMethod.getAnnotation(Transactional.class)).isNull();
        assertThat(reserveMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(reconcileMethod.getAnnotation(PostMapping.class).value())
                .containsExactly("/orders/{salesOrderId}/reconcile");
        assertThat(reconcileMethod.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    void createBeforeReconcileFindsExistingMatchedOrderWithoutCallingCreate() {
        Fixture fixture = fixture();
        SalesOrderU9SyncStateService.PreparedOrder prepared = prepared(9001L, "ATT-1");
        when(fixture.stateService.reserve(9001L)).thenReturn(prepared);
        when(fixture.reconciliationService.checkExisting(prepared)).thenReturn(
                U9SalesOrderReconciliationService.Result.foundAndMatched("U9-ID-1", "U9-SO-1", List.of())
        );

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(9001L));

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderId()).isEqualTo(9001L);
            assertThat(row.getU9OrderId()).isEqualTo("U9-ID-1");
            assertThat(row.getU9OrderCode()).isEqualTo("U9-SO-1");
        });
        verify(fixture.u9Client, never()).create(any());
        verify(fixture.stateService).markSuccess(prepared, "U9-ID-1", "U9-SO-1", "创建前查重命中已存在 U9 订单");
    }

    @Test
    void timeoutThenMatchedReconciliationRepairsSuccessAndAmbiguousResultBecomesPending() {
        Fixture matchedFixture = fixture();
        SalesOrderU9SyncStateService.PreparedOrder matched = prepared(9002L, "ATT-2");
        when(matchedFixture.stateService.reserve(9002L)).thenReturn(matched);
        when(matchedFixture.reconciliationService.checkExisting(matched)).thenReturn(
                U9SalesOrderReconciliationService.Result.notFoundConfirmed("创建前未找到同 DocNo U9 订单")
        );
        when(matchedFixture.u9Client.create(any())).thenThrow(new RuntimeException("connect timeout token=hidden-token"));
        when(matchedFixture.reconciliationService.reconcile(matched)).thenReturn(
                U9SalesOrderReconciliationService.Result.foundAndMatched("U9-ID-2", "U9-SO-2", List.of())
        );

        SalesOrderU9SyncResp matchedResp = matchedFixture.service.syncSalesOrders(List.of(9002L));

        assertThat(matchedResp.isSuccess()).isTrue();
        verify(matchedFixture.stateService).markSuccess(matched, "U9-ID-2", "U9-SO-2", "U9 创建结果不明，核对后确认已同步");

        Fixture pendingFixture = fixture();
        SalesOrderU9SyncStateService.PreparedOrder pending = prepared(9003L, "ATT-3");
        when(pendingFixture.stateService.reserve(9003L)).thenReturn(pending);
        when(pendingFixture.reconciliationService.checkExisting(pending)).thenReturn(
                U9SalesOrderReconciliationService.Result.notFoundConfirmed("创建前未找到同 DocNo U9 订单")
        );
        when(pendingFixture.u9Client.create(any())).thenThrow(new RuntimeException("read timeout Authorization: Bearer hidden-auth"));
        when(pendingFixture.reconciliationService.reconcile(pending)).thenReturn(
                U9SalesOrderReconciliationService.Result.queryFailed("U9 查询失败")
        );

        SalesOrderU9SyncResp pendingResp = pendingFixture.service.syncSalesOrders(List.of(9003L));

        assertThat(pendingResp.isSuccess()).isFalse();
        assertThat(pendingResp.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getErrorMessage()).contains("结果待确认"));
        verify(pendingFixture.stateService).markPending(pending, "结果待确认：U9 创建结果不明，核对仍无法确认：U9 查询失败");
    }

    @Test
    void reserveBlocksPendingStatusBeforeAnyExternalCreate() {
        Fixture fixture = fixture();
        when(fixture.stateService.reserve(9004L)).thenThrow(new RuntimeException("结果待确认状态只能核对，不能直接重新创建"));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(9004L));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getErrorMessage()).contains("只能核对"));
        verify(fixture.u9Client, never()).create(any());
    }

    @Test
    void stateServiceReserveRejectsPendingAndFinalUpdatesUseAttemptCondition() {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderU9SyncStateService stateService = stateService(headerMapper, itemMapper);
        SalesOrderHeader pending = header(9010L, "结果待确认");
        when(headerMapper.selectByIdForUpdate(pending.getId())).thenReturn(pending);

        assertThatThrownBy(() -> stateService.reserve(pending.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只能核对");

        verify(headerMapper, never()).update(isNull(), any());

        SalesOrderU9SyncStateService.PreparedOrder prepared = prepared(9011L, "ATT-COND");
        when(headerMapper.update(isNull(), any())).thenReturn(1);
        stateService.markPending(prepared, "需要人工核对");

        ArgumentCaptor<Wrapper<SalesOrderHeader>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(headerMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("id")
                .contains("u9_sync_attempt_id");
    }

    @Test
    void stateServiceValidatesSellerAndDepartmentAgainstConfiguredU9OrgCode() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        SalesOrderU9SyncStateService service = new SalesOrderU9SyncStateService();
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "sellerOptionMapper", sellerMapper);
        ReflectionTestUtils.setField(service, "departmentOptionMapper", departmentMapper);
        U9OrgCodeResolver resolver = orgResolver("106");
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", resolver);

        CustomerMaster customer = new CustomerMaster();
        customer.setEnabled(true);
        when(customerMapper.selectEnabledByCode("CUST-A")).thenReturn(customer);
        when(sellerMapper.selectOne(any())).thenReturn(new U9SyncSellerOptionCache());
        when(departmentMapper.selectOne(any())).thenReturn(new U9SyncDepartmentOptionCache());
        SalesOrderHeader header = new SalesOrderHeader();
        header.setCustomerCode("CUST-A");
        header.setU9SellerCode("S106");
        header.setU9SaleDepartmentCode("D106");

        ReflectionTestUtils.invokeMethod(service, "assertActiveMasterData", header);

        ArgumentCaptor<QueryWrapper<U9SyncSellerOptionCache>> sellerQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<U9SyncDepartmentOptionCache>> departmentQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sellerMapper).selectOne(sellerQueryCaptor.capture());
        verify(departmentMapper).selectOne(departmentQueryCaptor.capture());
        verify(resolver, times(2)).effectiveOrgCode();
        assertThat(sellerQueryCaptor.getValue().getSqlSegment()).contains("u9_org_code");
        assertThat(departmentQueryCaptor.getValue().getSqlSegment()).contains("u9_org_code");
    }

    @Test
    void reconciliationComparesU9HeadAgainstConfiguredOrgCode() {
        U9CommonEntityClient commonEntityClient = mock(U9CommonEntityClient.class);
        U9SalesOrderReconciliationService service = new U9SalesOrderReconciliationService();
        ReflectionTestUtils.setField(service, "commonEntityClient", commonEntityClient);
        ReflectionTestUtils.setField(service, "clock", FIXED_CLOCK);
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver("106"));
        SalesOrderU9SyncStateService.PreparedOrder prepared = prepared(
                header(9025L, "同步中"),
                "ATT-106",
                LocalDateTime.of(2026, 8, 11, 14, 30, 0)
        );
        when(commonEntityClient.query(any(U9CommonEntityQueryReq.class))).thenAnswer(invocation -> {
            U9CommonEntityQueryReq req = invocation.getArgument(0);
            if (U9SalesOrderReconciliationService.SO_ENTITY.equals(req.getEntityFullName())) {
                return List.of(row(Map.of(
                        "ID", "U9-SO-9025",
                        "DocNo", prepared.request().getDocNo(),
                        "OrderBy.Code", "CUST-A",
                        "DocumentType.Code", "SO01",
                        "Org.Code", "101"
                )));
            }
            return List.of(row(Map.of(
                    "ID", "U9-LINE-9025",
                    "SO.ID", "U9-SO-9025",
                    "DocLineNo", 1,
                    "ItemInfo.ItemID.Code", "MAT-90250",
                    "OrderByQtyTU", new BigDecimal("2"),
                    "OrderByQtyPU", new BigDecimal("2"),
                    "FinallyPriceTC", new BigDecimal("16.37")
            )));
        });

        U9SalesOrderReconciliationService.Result result = service.checkExisting(prepared);

        assertThat(result.code()).isEqualTo(U9SalesOrderReconciliationService.ResultCode.FOUND_CONFLICT);
        assertThat(result.message()).contains("106").contains("101");
    }

    @Test
    void reconciliationNoOrderWaitsDuringGraceThenConfirmsFailedAfterGrace() {
        U9CommonEntityClient commonEntityClient = mock(U9CommonEntityClient.class);
        when(commonEntityClient.query(any(U9CommonEntityQueryReq.class))).thenReturn(List.of());
        U9SalesOrderReconciliationService service = new U9SalesOrderReconciliationService();
        ReflectionTestUtils.setField(service, "commonEntityClient", commonEntityClient);
        ReflectionTestUtils.setField(service, "clock", FIXED_CLOCK);
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver("101"));
        ReflectionTestUtils.setField(service, "graceSeconds", 120L);

        SalesOrderU9SyncStateService.PreparedOrder recent = prepared(
                header(9020L, "同步中"), "ATT-RECENT", LocalDateTime.of(2026, 8, 11, 14, 29, 0));
        SalesOrderU9SyncStateService.PreparedOrder old = prepared(
                header(9021L, "结果待确认"), "ATT-OLD", LocalDateTime.of(2026, 8, 11, 14, 20, 0));

        assertThat(service.reconcile(recent).code())
                .isEqualTo(U9SalesOrderReconciliationService.ResultCode.NOT_FOUND_WAIT);
        assertThat(service.reconcile(old).code())
                .isEqualTo(U9SalesOrderReconciliationService.ResultCode.NOT_FOUND_CONFIRMED);
    }

    @Test
    void reconcileApiTurnsConfirmedMissingOrderIntoRetryableFailure() {
        Fixture fixture = fixture();
        SalesOrderU9SyncStateService.PreparedOrder target = prepared(
                header(9030L, "结果待确认"), "ATT-REC", LocalDateTime.of(2026, 8, 11, 14, 20, 0));
        when(fixture.stateService.prepareForReconcile(9030L)).thenReturn(target);
        when(fixture.reconciliationService.reconcile(target)).thenReturn(
                U9SalesOrderReconciliationService.Result.notFoundConfirmed("超过保护期且 U9 明确无单")
        );

        SalesOrderU9ReconcileResp resp = fixture.service.reconcileSalesOrder(9030L);

        assertThat(resp.getCode()).isEqualTo("NOT_FOUND_CONFIRMED");
        assertThat(resp.getU9SyncStatus()).isEqualTo("同步失败");
        assertThat(AuditService.inferModule("SALES_ORDER_U9_RECONCILE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_RECONCILE"))
                .contains("销售订单").contains("U9").contains("核对");
        verify(fixture.stateService).markFailed(target, "超过保护期且 U9 明确无单");
        verify(fixture.auditService).record(
                eq("SALES_ORDER_U9_RECONCILE"),
                eq("sales_order_header"),
                eq(9030L),
                isNull(),
                any(),
                eq("MARKET"),
                eq("FAILED"),
                eq(AuditService.labelForAction("SALES_ORDER_U9_RECONCILE")),
                eq("超过保护期且 U9 明确无单")
        );
        verify(fixture.u9Client, never()).create(any());
    }

    private Fixture fixture() {
        SalesOrderU9SyncService service = new SalesOrderU9SyncService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "syncStateService", fixture.stateService);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", fixture.u9Client);
        ReflectionTestUtils.setField(service, "reconciliationService", fixture.reconciliationService);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        return fixture;
    }

    private SalesOrderU9SyncStateService stateService(SalesOrderHeaderMapper headerMapper, SalesOrderItemMapper itemMapper) {
        SalesOrderU9SyncStateService service = new SalesOrderU9SyncStateService();
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", mock(DataScopeService.class));
        ReflectionTestUtils.setField(service, "requestAssembler", new U9SalesOrderRequestAssembler());
        ReflectionTestUtils.setField(service, "contractValidator", new U9SalesOrderContractValidator());
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver("101"));
        ReflectionTestUtils.setField(service, "clock", FIXED_CLOCK);
        return service;
    }

    private U9OrgCodeResolver orgResolver(String orgCode) {
        U9OrgCodeResolver resolver = mock(U9OrgCodeResolver.class);
        when(resolver.effectiveOrgCode()).thenReturn(orgCode);
        return resolver;
    }

    private SalesOrderU9SyncStateService.PreparedOrder prepared(Long id, String attemptId) {
        return prepared(header(id, "同步中"), attemptId, LocalDateTime.of(2026, 8, 11, 14, 30, 0));
    }

    private SalesOrderU9SyncStateService.PreparedOrder prepared(
            SalesOrderHeader header,
            String attemptId,
            LocalDateTime startedAt) {
        return new SalesOrderU9SyncStateService.PreparedOrder(
                header,
                List.of(item(header.getId() * 10)),
                request(header),
                attemptId,
                "hash-" + attemptId,
                startedAt
        );
    }

    private SalesOrderHeader header(Long id, String status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SOQPO-" + id);
        header.setDocumentNo("DOC-" + id);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-A");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 11));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(status);
        header.setU9SyncAttemptId("ATT-OLD");
        return header;
    }

    private SalesOrderItem item(Long id) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(id / 10);
        item.setLineNo(1);
        item.setPartNo("P-" + id);
        item.setMaterialNo("MAT-" + id);
        item.setMaterialNoStatus("已确认");
        item.setQty(2);
        item.setReleasedQty(2);
        item.setRemainingQty(0);
        item.setUntaxedUnitPrice(new BigDecimal("16.37"));
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 1));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 28));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private U9SalesOrderHeadDTO request(SalesOrderHeader header) {
        U9SalesOrderHeadDTO head = new U9SalesOrderHeadDTO();
        head.setOtherID(header.getSalesOrderNo());
        head.setDocNo(header.getDocumentNo());
        head.setBusinessDate("2026-08-11");
        head.setOrderBy("CUST-A");
        head.setDocumentType("SO01");
        head.setIsPriceIncludeTax(false);
        U9SalesOrderLineDTO line = new U9SalesOrderLineDTO();
        line.setDocLineNo(1);
        line.setItemCode("MAT-" + (header.getId() * 10));
        line.setOrderByQtyTU(new BigDecimal("2"));
        line.setOrderByQtyPU(new BigDecimal("2"));
        line.setFinallyPriceTC(new BigDecimal("16.37"));
        head.getSOLineDTOList().add(line);
        return head;
    }

    private U9ApiResult<U9ResultDTO> createResult(String otherId, boolean success, String id, String code, String error) {
        U9ResultDTO row = new U9ResultDTO();
        row.setMOtherID(otherId);
        row.setMIsSucess(success);
        row.setMID(id);
        row.setMCode(code);
        row.setMErrorMsg(error);
        U9ApiResult<U9ResultDTO> result = new U9ApiResult<>();
        result.setResCode(0);
        result.setSuccess(true);
        result.setData(List.of(row));
        return result;
    }

    private U9CommonEntityRowDTO row(Map<String, Object> values) {
        U9CommonEntityRowDTO row = new U9CommonEntityRowDTO();
        row.setValues(values);
        return row;
    }

    private static final class Fixture {
        private final SalesOrderU9SyncService service;
        private final SalesOrderU9SyncStateService stateService = mock(SalesOrderU9SyncStateService.class);
        private final U9SalesOrderClient u9Client = mock(U9SalesOrderClient.class);
        private final U9SalesOrderReconciliationService reconciliationService = mock(U9SalesOrderReconciliationService.class);
        private final AuditService auditService = mock(AuditService.class);

        private Fixture(SalesOrderU9SyncService service) {
            this.service = service;
        }
    }
}
