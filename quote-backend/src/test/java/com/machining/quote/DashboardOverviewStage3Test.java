package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.dto.dashboard.DashboardCustomerCountRow;
import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import com.machining.quote.dto.dashboard.DashboardStatusCountRow;
import com.machining.quote.mapper.DashboardAggregationMapper;
import com.machining.quote.service.DashboardOverviewService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardOverviewStage3Test {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void overviewPrunesUnauthorizedDomainsBeforeReturningCounts() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DashboardOverviewService service = service(mapper, dataScopeService, permissionService,
                permissions(false, "market:delivered-item:view"));

        when(mapper.countDeliveredActive(any())).thenReturn(12L);
        when(mapper.countDeliveredNeedMaterial(any())).thenReturn(7L);
        when(mapper.countDeliveredNeedWms(any())).thenReturn(5L);
        when(mapper.countDeliveredMesCallbackPending(any())).thenReturn(3L);
        when(mapper.countDeliveredMesFailed(any())).thenReturn(2L);
        when(mapper.countDeliveredWmsFailed(any())).thenReturn(1L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getSalesQuotedCustomerDistribution()).isEmpty();
        assertThat(overview.getCustomerMatrix()).isEmpty();
        assertThat(overview.getSummaryCards()).extracting("key").containsExactly("deliveredActive");
        assertThat(overview.getTodoCards()).extracting("key").containsExactly("materialTodo", "wmsTodo");
        assertThat(overview.getAttentionItems()).extracting("key")
                .containsExactly("mesCallbackPending", "mesFailed", "wmsFailed");
        assertThat(overview.getAttentionItems())
                .filteredOn(item -> "mesCallbackPending".equals(item.getKey()))
                .singleElement()
                .extracting("severity")
                .isEqualTo("warning");

        verify(mapper, never()).countRfqByStatus(any());
        verify(mapper, never()).countRfqByCustomerAndStatus(any());
        verify(mapper, never()).countSalesQuotedByCustomer(any());
        verify(mapper, never()).countSalesMatchTodo(any());
        verify(mapper, never()).countU9Ready(any());
        verify(mapper, never()).countU9Failed(any());
        verify(mapper, never()).countLockedProductionTodo(any());
        verify(mapper, never()).countLockedMesProductionFailed(any());
    }

    @Test
    void salesCustomerDistributionKeepsTopEightAndMergesOtherCustomersWhenAllowed() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DashboardOverviewService service = service(mapper, dataScopeService, permissionService,
                permissions(false, "market:sales-order:view"));

        when(mapper.countSalesQuotedByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-01", "Customer 01", "Customer 01", QuoteStatusWorkflow.QUOTED, 20),
                customerRow(2L, "CUS-02", "Customer 02", "Customer 02", QuoteStatusWorkflow.QUOTED, 19),
                customerRow(3L, "CUS-03", "Customer 03", "Customer 03", QuoteStatusWorkflow.QUOTED, 18),
                customerRow(4L, "CUS-04", "Customer 04", "Customer 04", QuoteStatusWorkflow.QUOTED, 17),
                customerRow(5L, "CUS-05", "Customer 05", "Customer 05", QuoteStatusWorkflow.QUOTED, 16),
                customerRow(6L, "CUS-06", "Customer 06", "Customer 06", QuoteStatusWorkflow.QUOTED, 15),
                customerRow(7L, "CUS-07", "Customer 07", "Customer 07", QuoteStatusWorkflow.QUOTED, 14),
                customerRow(8L, "CUS-08", "Customer 08", "Customer 08", QuoteStatusWorkflow.QUOTED, 13),
                customerRow(9L, "CUS-09", "Customer 09", "Customer 09", QuoteStatusWorkflow.QUOTED, 12),
                customerRow(10L, "CUS-10", "Customer 10", "Customer 10", QuoteStatusWorkflow.QUOTED, 11)
        ));

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getSalesQuotedCustomerDistribution()).hasSize(9);
        assertThat(overview.getSalesQuotedCustomerDistribution().get(0).getName()).isEqualTo("Customer 01");
        assertThat(overview.getSalesQuotedCustomerDistribution().get(8).getCustomerId()).isNull();
        assertThat(overview.getSalesQuotedCustomerDistribution().get(8).getValue()).isEqualTo(23L);
        assertThat(overview.getCustomerMatrix()).hasSize(10);
        assertThat(overview.getSummaryCards()).extracting("key").containsExactly("salesQuoted");
        assertThat(overview.getTodoCards()).isEmpty();
        assertThat(overview.getAttentionItems()).isEmpty();

        verify(mapper, never()).countRfqByStatus(any());
        verify(mapper, never()).countRfqByCustomerAndStatus(any());
        verify(mapper, never()).countDeliveredActive(any());
        verify(mapper, never()).countLockedProductionTodo(any());
    }

    @Test
    void superAdminKeepsAllDashboardDomainsVisible() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DashboardOverviewService service = service(mapper, dataScopeService, permissionService, permissions(true));

        when(mapper.countRfqByStatus(any())).thenReturn(List.of(
                statusRow(QuoteStatusWorkflow.UNPRICED, 1),
                statusRow(QuoteStatusWorkflow.PRICED, 2),
                statusRow(QuoteStatusWorkflow.QUOTED, 3)
        ));
        when(mapper.countRfqByCustomerAndStatus(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "Customer A", "Customer A", QuoteStatusWorkflow.UNPRICED, 1)
        ));
        when(mapper.countSalesQuotedByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "Customer A", "Customer A", QuoteStatusWorkflow.QUOTED, 4)
        ));
        when(mapper.countDeliveredActive(any())).thenReturn(5L);
        when(mapper.countDeliveredNeedMaterial(any())).thenReturn(6L);
        when(mapper.countDeliveredNeedWms(any())).thenReturn(7L);
        when(mapper.countDeliveredMesCallbackPending(any())).thenReturn(8L);
        when(mapper.countDeliveredMesFailed(any())).thenReturn(9L);
        when(mapper.countDeliveredWmsFailed(any())).thenReturn(10L);
        when(mapper.countLockedProductionTodo(any())).thenReturn(11L);
        when(mapper.countLockedMesProductionFailed(any())).thenReturn(12L);
        when(mapper.countSalesMatchTodo(any())).thenReturn(13L);
        when(mapper.countU9Ready(any())).thenReturn(14L);
        when(mapper.countU9Failed(any())).thenReturn(15L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).hasSize(3);
        assertThat(overview.getSalesQuotedCustomerDistribution()).hasSize(1);
        assertThat(overview.getCustomerMatrix()).hasSize(1);
        assertThat(overview.getSummaryCards()).extracting("key")
                .containsExactly("rfqTodo", "salesQuoted", "deliveredActive", "lockedProduction");
        assertThat(overview.getTodoCards()).extracting("key")
                .contains("rfqPricing", "rfqQuote", "rfqRelease", "salesMatch", "materialTodo",
                        "wmsTodo", "mesProductionTodo", "u9SyncReady");
        assertThat(overview.getAttentionItems()).extracting("key")
                .contains("mesCallbackPending", "mesFailed", "wmsFailed", "u9Failed", "mesProductionFailed");
    }

    private DashboardOverviewService service(
            DashboardAggregationMapper mapper,
            DataScopeService dataScopeService,
            PermissionService permissionService,
            PermissionInfoDTO permissionInfo) {
        AuthContext.set(new AuthContext.SessionUser(42, "dashboard-user", "Dashboard User", "MARKET"));
        when(permissionService.getPermissionInfo(42, "MARKET")).thenReturn(permissionInfo);
        DashboardOverviewService service = new DashboardOverviewService(mapper, dataScopeService);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        return service;
    }

    private PermissionInfoDTO permissions(boolean superAdmin, String... permissions) {
        PermissionInfoDTO info = new PermissionInfoDTO();
        info.setSuperAdmin(superAdmin);
        info.setPermissions(new ArrayList<>(List.of(permissions)));
        return info;
    }

    private DashboardStatusCountRow statusRow(String status, long count) {
        DashboardStatusCountRow row = new DashboardStatusCountRow();
        row.setStatus(status);
        row.setCount(count);
        return row;
    }

    private DashboardCustomerCountRow customerRow(
            Long customerId,
            String customerCode,
            String customerName,
            String customerShortName,
            String status,
            long count) {
        DashboardCustomerCountRow row = new DashboardCustomerCountRow();
        row.setCustomerId(customerId);
        row.setCustomerCode(customerCode);
        row.setCustomerName(customerName);
        row.setCustomerShortName(customerShortName);
        row.setStatus(status);
        row.setCount(count);
        return row;
    }
}
