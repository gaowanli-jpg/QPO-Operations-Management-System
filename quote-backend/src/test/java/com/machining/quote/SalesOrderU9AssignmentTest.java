package com.machining.quote;

import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9AssignmentReq;
import com.machining.quote.dto.SalesOrderU9AssignmentResp;
import com.machining.quote.dto.SalesOrderU9DepartmentOptionDTO;
import com.machining.quote.dto.SalesOrderU9SellerOptionDTO;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9MasterDataService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9AssignmentTest {
    @Test
    void controllerExposesAssignmentSaveWithSubmitPermission() throws Exception {
        Method method = SalesOrderU9SyncController.class.getMethod(
                "saveAssignment", Long.class, SalesOrderU9AssignmentReq.class);

        assertThat(method.getAnnotation(PutMapping.class).value())
                .containsExactly("/orders/{salesOrderId}/assignment");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveAssignmentUsesRowLockDataScopeMasterDataAndWritesAuditSummary() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "未同步");
        header.setU9SellerCode("OLD-SELLER");
        header.setU9SellerName("旧销售员");
        header.setU9SaleDepartmentCode("OLD-DEPT");
        header.setU9SaleDepartmentName("旧部门");
        fixture.withOrder(header);

        SalesOrderU9AssignmentReq req = new SalesOrderU9AssignmentReq();
        req.setSellerCode(" C00150 ");
        req.setSaleDepartmentCode(" 200201 ");

        SalesOrderU9AssignmentResp resp = fixture.service.saveAssignment(header.getId(), req);

        assertThat(resp.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(resp.getSalesOrderNo()).isEqualTo("SOQPO-9001");
        assertThat(resp.getSellerCode()).isEqualTo("C00150");
        assertThat(resp.getSellerName()).isEqualTo("周洪");
        assertThat(resp.getSaleDepartmentCode()).isEqualTo("200201");
        assertThat(resp.getSaleDepartmentName()).isEqualTo("订单运维部");
        assertThat(resp.getUpdatedAt()).isNotNull();

        verify(fixture.headerMapper).selectByIdForUpdate(header.getId());
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.masterDataService).options();
        verify(fixture.headerMapper).update(isNull(), any());
        verify(fixture.headerMapper, never()).updateById(any());

        assertThat(AuditService.inferModule("SALES_ORDER_U9_ASSIGNMENT_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_ASSIGNMENT_UPDATE"))
                .contains("销售订单").contains("销售员").contains("部门");

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_U9_ASSIGNMENT_UPDATE"),
                eq("sales_order_header"),
                eq(header.getId()),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_U9_ASSIGNMENT_UPDATE")),
                isNull()
        );
        assertThat((Map<String, Object>) beforeCaptor.getValue())
                .containsEntry("sellerCode", "OLD-SELLER")
                .containsEntry("saleDepartmentCode", "OLD-DEPT");
        assertThat((Map<String, Object>) afterCaptor.getValue())
                .containsEntry("sellerCode", "C00150")
                .containsEntry("sellerName", "周洪")
                .containsEntry("saleDepartmentCode", "200201")
                .containsEntry("saleDepartmentName", "订单运维部");
    }

    @Test
    void assignmentAllowsBothFieldsEmptyDepartmentOverrideAndClearingSeller() {
        Fixture clearAll = fixture();
        SalesOrderHeader clearAllHeader = header(9002L, "未同步");
        clearAllHeader.setU9SellerCode("C00150");
        clearAllHeader.setU9SellerName("周洪");
        clearAllHeader.setU9SaleDepartmentCode("200201");
        clearAllHeader.setU9SaleDepartmentName("订单运维部");
        clearAll.withOrder(clearAllHeader);

        SalesOrderU9AssignmentResp cleared = clearAll.service.saveAssignment(clearAllHeader.getId(), new SalesOrderU9AssignmentReq());

        assertThat(cleared.getSellerCode()).isNull();
        assertThat(cleared.getSellerName()).isNull();
        assertThat(cleared.getSaleDepartmentCode()).isNull();
        assertThat(cleared.getSaleDepartmentName()).isNull();

        Fixture override = fixture();
        SalesOrderHeader overrideHeader = header(9003L, "未同步");
        override.withOrder(overrideHeader);
        SalesOrderU9AssignmentReq overrideReq = new SalesOrderU9AssignmentReq();
        overrideReq.setSellerCode("C00150");
        overrideReq.setSaleDepartmentCode("900001");

        SalesOrderU9AssignmentResp overridden = override.service.saveAssignment(overrideHeader.getId(), overrideReq);

        assertThat(overridden.getSellerCode()).isEqualTo("C00150");
        assertThat(overridden.getSellerName()).isEqualTo("周洪");
        assertThat(overridden.getSaleDepartmentCode()).isEqualTo("900001");
        assertThat(overridden.getSaleDepartmentName()).isEqualTo("特殊销售部");

        Fixture clearSeller = fixture();
        SalesOrderHeader clearSellerHeader = header(9004L, "未同步");
        clearSeller.withOrder(clearSellerHeader);
        SalesOrderU9AssignmentReq clearSellerReq = new SalesOrderU9AssignmentReq();
        clearSellerReq.setSaleDepartmentCode("200201");

        SalesOrderU9AssignmentResp sellerCleared = clearSeller.service.saveAssignment(clearSellerHeader.getId(), clearSellerReq);

        assertThat(sellerCleared.getSellerCode()).isNull();
        assertThat(sellerCleared.getSellerName()).isNull();
        assertThat(sellerCleared.getSaleDepartmentCode()).isEqualTo("200201");
        assertThat(sellerCleared.getSaleDepartmentName()).isEqualTo("订单运维部");
    }

    @Test
    void lockedU9StatusesCannotModifyAssignment() {
        for (String status : List.of("同步中", "结果待确认", "已同步")) {
            Fixture fixture = fixture();
            SalesOrderHeader header = header(9010L, status);
            fixture.withOrder(header);
            SalesOrderU9AssignmentReq req = new SalesOrderU9AssignmentReq();
            req.setSellerCode("C00150");
            req.setSaleDepartmentCode("200201");

            assertThatThrownBy(() -> fixture.service.saveAssignment(header.getId(), req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(status)
                    .hasMessageContaining("不能修改");

            verify(fixture.headerMapper, never()).update(any(), any());
            verify(fixture.auditService, never()).record(eq("SALES_ORDER_U9_ASSIGNMENT_UPDATE"), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void dataScopeRejectsCraftedAssignmentRequestBeforeUpdate() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9020L, "未同步");
        fixture.withOrder(header);
        doThrow(new RuntimeException("无数据权限"))
                .when(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        SalesOrderU9AssignmentReq req = new SalesOrderU9AssignmentReq();
        req.setSellerCode("C00150");

        assertThatThrownBy(() -> fixture.service.saveAssignment(header.getId(), req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无数据权限");

        verify(fixture.masterDataService, never()).options();
        verify(fixture.headerMapper, never()).update(any(), any());
        verify(fixture.auditService, never()).record(eq("SALES_ORDER_U9_ASSIGNMENT_UPDATE"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listAndPreviewExposePersistedAssignmentSnapshotsAfterRefresh() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9030L, "未同步");
        header.setDocumentNo("DOC-9030");
        header.setU9SellerCode("C00150");
        header.setU9SellerName("周洪");
        header.setU9SaleDepartmentCode("200201");
        header.setU9SaleDepartmentName("订单运维部");
        when(fixture.headerMapper.selectCount(any())).thenReturn(1L);
        when(fixture.headerMapper.selectList(any())).thenReturn(List.of(header));
        when(fixture.headerMapper.selectById(header.getId())).thenReturn(header);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(), List.of());

        PageResult<SalesOrderU9SyncOrderRow> list = fixture.service.listOrders(null, null, null, null, null, 1, 20);
        SalesOrderU9SyncPreviewResp preview = fixture.service.previewSalesOrder(header.getId());

        assertThat(list.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getDocumentNo()).isEqualTo("DOC-9030");
            assertThat(row.getSellerCode()).isEqualTo("C00150");
            assertThat(row.getSellerName()).isEqualTo("周洪");
            assertThat(row.getSaleDepartmentCode()).isEqualTo("200201");
            assertThat(row.getSaleDepartmentName()).isEqualTo("订单运维部");
        });
        assertThat(preview).satisfies(resp -> {
            assertThat(resp.getDocumentNo()).isEqualTo("DOC-9030");
            assertThat(resp.getSellerCode()).isEqualTo("C00150");
            assertThat(resp.getSellerName()).isEqualTo("周洪");
            assertThat(resp.getSaleDepartmentCode()).isEqualTo("200201");
            assertThat(resp.getSaleDepartmentName()).isEqualTo("订单运维部");
        });
    }

    private Fixture fixture() {
        SalesOrderU9SyncService service = new SalesOrderU9SyncService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", fixture.u9Client);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "u9MasterDataService", fixture.masterDataService);
        when(fixture.masterDataService.options()).thenReturn(options());
        when(fixture.headerMapper.update(isNull(), any())).thenReturn(1);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SOQPO-" + id.toString().substring(id.toString().length() - 4));
        header.setDocumentNo("DOC-" + id);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-A");
        header.setCustomerName("客户A");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 11));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(u9Status);
        return header;
    }

    private void withOrder(Fixture fixture, SalesOrderHeader header) {
        fixture.withOrder(header);
    }

    private SalesOrderU9SyncOptionsResp options() {
        SalesOrderU9SyncOptionsResp options = new SalesOrderU9SyncOptionsResp();
        options.setOrgCode("101");
        options.setSellers(List.of(
                new SalesOrderU9SellerOptionDTO("C00150", "周洪", "200201", "订单运维部"),
                new SalesOrderU9SellerOptionDTO("C00999", "树外销售", "900001", "特殊销售部")
        ));
        options.setDepartments(List.of(
                new SalesOrderU9DepartmentOptionDTO("200201", "订单运维部", "市场营销中心/销售部/订单运维部"),
                new SalesOrderU9DepartmentOptionDTO("900001", "特殊销售部", "特殊销售部")
        ));
        return options;
    }

    private static final class Fixture {
        private final SalesOrderU9SyncService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final U9SalesOrderClient u9Client = mock(U9SalesOrderClient.class);
        private final AuditService auditService = mock(AuditService.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final U9MasterDataService masterDataService = mock(U9MasterDataService.class);

        private Fixture(SalesOrderU9SyncService service) {
            this.service = service;
        }

        private void withOrder(SalesOrderHeader header) {
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        }
    }
}
