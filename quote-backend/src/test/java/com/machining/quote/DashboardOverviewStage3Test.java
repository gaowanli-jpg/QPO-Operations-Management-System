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
import java.util.Map;

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
                permissions(false, "market:delivered-item:view", "market:sales-order:wms-allocate"));

        when(mapper.countDeliveredActive(any())).thenReturn(12L);
        when(mapper.countDeliveredNeedMaterial(any())).thenReturn(7L);
        when(mapper.countDeliveredNeedWms(any())).thenReturn(5L);
        when(mapper.countDeliveredMesCallbackPending(any())).thenReturn(3L);
        when(mapper.countDeliveredMesFailed(any())).thenReturn(2L);
        when(mapper.countDeliveredWmsFailed(any())).thenReturn(1L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getU9ReadyCustomerDistribution()).isEmpty();
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
        verify(mapper, never()).countU9ReadyByCustomer(any());
        verify(mapper, never()).countU9Ready(any());
        verify(mapper, never()).countU9Failed(any());
        verify(mapper, never()).countLockedProductionTodo(any());
        verify(mapper, never()).countLockedMesProductionFailed(any());
    }

    @Test
    void u9ReadyCustomerDistributionKeepsTopEightAndMergesOtherCustomersWhenAllowed() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DashboardOverviewService service = service(mapper, dataScopeService, permissionService,
                permissions(false, "market:u9-sync:view"));

        when(mapper.countU9ReadyByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-01", "Customer 01", "Customer 01", QuoteStatusWorkflow.RELEASED, 20),
                customerRow(2L, "CUS-02", "Customer 02", "Customer 02", QuoteStatusWorkflow.RELEASED, 19),
                customerRow(3L, "CUS-03", "Customer 03", "Customer 03", QuoteStatusWorkflow.RELEASED, 18),
                customerRow(4L, "CUS-04", "Customer 04", "Customer 04", QuoteStatusWorkflow.RELEASED, 17),
                customerRow(5L, "CUS-05", "Customer 05", "Customer 05", QuoteStatusWorkflow.RELEASED, 16),
                customerRow(6L, "CUS-06", "Customer 06", "Customer 06", QuoteStatusWorkflow.RELEASED, 15),
                customerRow(7L, "CUS-07", "Customer 07", "Customer 07", QuoteStatusWorkflow.RELEASED, 14),
                customerRow(8L, "CUS-08", "Customer 08", "Customer 08", QuoteStatusWorkflow.RELEASED, 13),
                customerRow(9L, "CUS-09", "Customer 09", "Customer 09", QuoteStatusWorkflow.RELEASED, 12),
                customerRow(10L, "CUS-10", "Customer 10", "Customer 10", QuoteStatusWorkflow.RELEASED, 11)
        ));

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).isEmpty();
        assertThat(overview.getU9ReadyCustomerDistribution()).hasSize(9);
        assertThat(overview.getU9ReadyCustomerDistribution().get(0).getName()).isEqualTo("Customer 01");
        assertThat(overview.getU9ReadyCustomerDistribution().get(8).getCustomerId()).isNull();
        assertThat(overview.getU9ReadyCustomerDistribution().get(8).getValue()).isEqualTo(23L);
        assertThat(overview.getCustomerMatrix()).hasSize(10);
        assertThat(overview.getSummaryCards()).singleElement().satisfies(card -> {
            assertThat(card.getKey()).isEqualTo("salesReleased");
            assertThat(card.getTitle()).isEqualTo("销售已提单");
            assertThat(card.getPermission()).isEqualTo("market:u9-sync:view");
            assertThat(card.getTarget()).containsEntry("path", "/u9-sync");
            assertThat(card.getTarget()).extractingByKey("query").isEqualTo(Map.of("materialReadyStatus", "READY"));
        });
        assertThat(overview.getTodoCards()).extracting("key").containsExactly("u9SyncReady");
        assertThat(overview.getAttentionItems()).extracting("key").containsExactly("u9Failed");

        verify(mapper, never()).countRfqByStatus(any());
        verify(mapper, never()).countRfqByCustomerAndStatus(any());
        verify(mapper, never()).countDeliveredActive(any());
        verify(mapper, never()).countLockedProductionTodo(any());
    }

    @Test
    void productionItemPermissionOwnsDashboardProductionEntries() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        DashboardOverviewService service = service(mapper, dataScopeService, permissionService,
                permissions(false, "market:production-item:view"));

        when(mapper.countLockedProductionTodo(any())).thenReturn(4L);
        when(mapper.countLockedMesProductionFailed(any())).thenReturn(2L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getSummaryCards()).singleElement().satisfies(card -> {
            assertThat(card.getKey()).isEqualTo("lockedProduction");
            assertThat(card.getPermission()).isEqualTo("market:production-item:view");
            assertThat(card.getTarget()).containsEntry("path", "/production-items");
            assertThat(card.getTarget()).extractingByKey("query").isEqualTo(Map.of("mesProductionStatus", "PENDING"));
        });
        assertThat(overview.getTodoCards()).singleElement().satisfies(card -> {
            assertThat(card.getKey()).isEqualTo("mesProductionTodo");
            assertThat(card.getPath()).isEqualTo("/production-items");
            assertThat(card.getQuery()).containsEntry("mesProductionStatus", "PENDING")
                    .doesNotContainKey("allocationType");
            assertThat(card.getPermission()).isEqualTo("market:production-item:view");
        });
        assertThat(overview.getAttentionItems()).singleElement().satisfies(item -> {
            assertThat(item.getKey()).isEqualTo("mesProductionFailed");
            assertThat(item.getPath()).isEqualTo("/production-items");
            assertThat(item.getQuery()).containsEntry("mesProductionStatus", "FAILED")
                    .doesNotContainKey("allocationType");
            assertThat(item.getPermission()).isEqualTo("market:production-item:view");
            assertThat(item.getSeverity()).isEqualTo("danger");
        });

        verify(mapper).countLockedProductionTodo(any());
        verify(mapper).countLockedMesProductionFailed(any());
        verify(mapper, never()).countDeliveredActive(any());
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
        when(mapper.countU9ReadyByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "Customer A", "Customer A", QuoteStatusWorkflow.RELEASED, 14)
        ));
        when(mapper.countDeliveredActive(any())).thenReturn(5L);
        when(mapper.countDeliveredNeedMaterial(any())).thenReturn(6L);
        when(mapper.countDeliveredNeedWms(any())).thenReturn(7L);
        when(mapper.countDeliveredMesCallbackPending(any())).thenReturn(8L);
        when(mapper.countDeliveredMesFailed(any())).thenReturn(9L);
        when(mapper.countDeliveredWmsFailed(any())).thenReturn(10L);
        when(mapper.countLockedProductionTodo(any())).thenReturn(11L);
        when(mapper.countLockedMesProductionFailed(any())).thenReturn(12L);
        when(mapper.countU9Ready(any())).thenReturn(14L);
        when(mapper.countU9Failed(any())).thenReturn(15L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution()).hasSize(3);
        assertThat(overview.getU9ReadyCustomerDistribution()).hasSize(1);
        assertThat(overview.getCustomerMatrix()).hasSize(1);
        assertThat(overview.getSummaryCards()).extracting("key")
                .containsExactly("rfqTodo", "salesReleased", "deliveredActive", "lockedProduction");
        assertThat(overview.getTodoCards()).extracting("key")
                .contains("rfqPricing", "rfqQuote", "rfqRelease", "materialTodo",
                        "wmsTodo", "mesProductionTodo", "u9SyncReady")
                .doesNotContain("salesMatch");
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
