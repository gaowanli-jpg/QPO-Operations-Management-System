package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import com.machining.quote.dto.dashboard.DashboardStatusCountRow;
import com.machining.quote.dto.dashboard.DashboardCustomerCountRow;
import com.machining.quote.mapper.DashboardAggregationMapper;
import com.machining.quote.service.DashboardOverviewService;
import com.machining.quote.service.DataScopeService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardOverviewStage2Test {
    @Test
    void overviewAggregatesCoreCountsWithDataScopeFilters() {
        DashboardAggregationMapper mapper = mock(DashboardAggregationMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        DashboardOverviewService service = new DashboardOverviewService(mapper, dataScopeService);

        when(mapper.countRfqByStatus(any())).thenReturn(List.of(
                statusRow(QuoteStatusWorkflow.UNPRICED, 3),
                statusRow(QuoteStatusWorkflow.PRICED, 2),
                statusRow(QuoteStatusWorkflow.QUOTED, 4),
                statusRow(QuoteStatusWorkflow.RELEASED, 99)
        ));
        when(mapper.countSalesQuotedByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.QUOTED, 7)
        ));
        when(mapper.countU9ReadyByCustomer(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.RELEASED, 5),
                customerRow(2L, "CUS-B", "客户B", "客户B", QuoteStatusWorkflow.RELEASED, 4)
        ));
        when(mapper.countRfqByCustomerAndStatus(any())).thenReturn(List.of(
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.UNPRICED, 1),
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.PRICED, 2),
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.QUOTED, 3),
                customerRow(1L, "CUS-A", "客户A", "客户A", QuoteStatusWorkflow.RELEASED, 88)
        ));
        when(mapper.countDeliveredActive(any())).thenReturn(11L);
        when(mapper.countDeliveredNeedMaterial(any())).thenReturn(7L);
        when(mapper.countDeliveredNeedWms(any())).thenReturn(6L);
        when(mapper.countDeliveredMesCallbackPending(any())).thenReturn(5L);
        when(mapper.countDeliveredMesFailed(any())).thenReturn(4L);
        when(mapper.countDeliveredWmsFailed(any())).thenReturn(3L);
        when(mapper.countLockedProductionTodo(any())).thenReturn(2L);
        when(mapper.countLockedMesProductionFailed(any())).thenReturn(1L);
        when(mapper.countU9Ready(any())).thenReturn(8L);
        when(mapper.countU9Failed(any())).thenReturn(6L);

        DashboardOverviewDTO overview = service.overview();

        assertThat(overview.getRfqStatusDistribution())
                .extracting("name")
                .containsExactly(QuoteStatusWorkflow.UNPRICED, QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.QUOTED);
        assertThat(overview.getRfqStatusDistribution())
                .extracting("value")
                .containsExactly(3L, 2L, 4L);
        assertThat(overview.getU9ReadyCustomerDistribution())
                .extracting("name")
                .containsExactly("客户A", "客户B");
        assertThat(overview.getCustomerMatrix()).hasSize(2);
        assertThat(overview.getCustomerMatrix().get(0).getCustomerName()).isEqualTo("客户A");
        assertThat(overview.getCustomerMatrix().get(0).getRfqUnpriced()).isEqualTo(1L);
        assertThat(overview.getCustomerMatrix().get(0).getRfqPriced()).isEqualTo(2L);
        assertThat(overview.getCustomerMatrix().get(0).getRfqQuoted()).isEqualTo(3L);
        assertThat(overview.getCustomerMatrix().get(0).getU9Ready()).isEqualTo(5L);
        assertThat(overview.getSummaryCards()).extracting("key")
                .contains("rfqTodo", "salesReleased", "deliveredActive", "lockedProduction");
        assertThat(overview.getSummaryCards())
                .filteredOn(card -> "salesReleased".equals(card.getKey()))
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.getTitle()).isEqualTo("销售已提单");
                    assertThat(card.getValue()).isEqualTo(8L);
                    assertThat(card.getHelper()).isEqualTo("已提单且待同步 U9");
                    assertThat(card.getTrendText()).isEqualTo("进入 U9 同步选择订单");
                    assertThat(card.getPermission()).isEqualTo("market:u9-sync:view");
                    assertThat(card.getTarget()).containsEntry("path", "/u9-sync");
                    assertThat(card.getTarget()).extractingByKey("query")
                            .isEqualTo(java.util.Map.of("materialReadyStatus", "READY"));
                });
        assertThat(overview.getTodoCards()).extracting("key")
                .contains("materialTodo", "wmsTodo", "mesProductionTodo", "u9SyncReady")
                .doesNotContain("salesMatch");
        assertThat(overview.getAttentionItems()).extracting("key")
                .contains("mesCallbackPending", "mesFailed", "wmsFailed", "u9Failed", "mesProductionFailed");

        verify(dataScopeService, atLeastOnce()).applyOrderScope(any(QueryWrapper.class), eq("rfq"));
        verify(dataScopeService, atLeastOnce()).applySalesOrderScope(any(QueryWrapper.class), eq("so"));
        verify(dataScopeService, atLeastOnce()).applyDeliveryTicketScope(any(QueryWrapper.class), eq("ticket"));
        verify(mapper, never()).countSalesQuotedByCustomer(any());
    }

    @Test
    void dashboardMapperUsesAggregateQueriesInsteadOfDetailLoading() throws Exception {
        String mapperSource = Files.readString(Path.of(
                "src/main/java/com/machining/quote/mapper/DashboardAggregationMapper.java"), StandardCharsets.UTF_8);
        String serviceSource = Files.readString(Path.of(
                "src/main/java/com/machining/quote/service/DashboardOverviewService.java"), StandardCharsets.UTF_8);
        String source = mapperSource + "\n" + serviceSource;

        assertThat(source).contains("GROUP BY rfq.status");
        assertThat(source).contains("GROUP BY");
        assertThat(source).contains("COUNT(*)");
        assertThat(source).contains("order_rfq rfq");
        assertThat(source).contains("sales_order_header so");
        assertThat(source).contains("order_delivery_ticket ticket");
        assertThat(source).contains("order_delivery_ticket_allocation alloc");
        assertThat(source).doesNotContain("SELECT *");
        assertThat(source).doesNotContain("item_quote_version");
        assertThat(source).contains("NOT EXISTS (SELECT 1 FROM order_delivery_ticket_allocation");
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
