package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.dto.aps.ApsDeliveryItemDTO;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.DictProcessCategory;
import com.machining.quote.entity.DictProcessDetail;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.DictProcessCategoryMapper;
import com.machining.quote.mapper.DictProcessDetailMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.aps.ApsSourceService;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.EngineerService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CoreOrderSelectionIntegrationTest extends CoreWorkflowIntegrationTestSupport {

    @Test
    void orderSummaryPaginationUsesAggregatedCountsAndFilters() {
        String customerName = "分页客户-" + System.nanoTime();
        OrderRfq first = orderWithItemsTicketsAndSheets(customerName, "PAGE-A", "已报价", 2, 1, 1, 1, 2);
        OrderRfq second = orderWithItemsTicketsAndSheets(customerName, "PAGE-B", "已生成提单", 1, 1, 0, 0, 0);
        orderWithItemsTicketsAndSheets("其他分页客户-" + System.nanoTime(), "PAGE-C", "已报价", 1, 0, 0, 0, 0);

        PageResult<Map<String, Object>> firstPage = orderManagementService.listOrderSummaries(
                customerName, null, null, 1, 1);

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getPage()).isEqualTo(1);
        assertThat(firstPage.getPageSize()).isEqualTo(1);
        assertThat(firstPage.getRecords()).hasSize(1);
        assertThat(firstPage.getRecords().get(0).get("id")).isEqualTo(second.getId());

        PageResult<Map<String, Object>> secondPage = orderManagementService.listOrderSummaries(
                customerName, null, null, 2, 1);
        Map<String, Object> summary = secondPage.getRecords().get(0);
        assertThat(summary.get("id")).isEqualTo(first.getId());
        assertThat(summary.get("itemCount")).isEqualTo(2);
        assertThat(summary.get("quotedCount")).isEqualTo(1);
        assertThat(summary.get("missingDrawingCount")).isEqualTo(1);
        assertThat(summary.get("ticketCount")).isEqualTo(1);
        assertThat(summary.get("ticketQty")).isEqualTo(3);
        assertThat(summary.get("processSheetCount")).isEqualTo(1);

        PageResult<Map<String, Object>> keywordFiltered = orderManagementService.listOrderSummaries(
                customerName, "PAGE-A", null, 1, 20);
        assertThat(keywordFiltered.getTotal()).isEqualTo(1);
        assertThat(keywordFiltered.getRecords().get(0).get("id")).isEqualTo(first.getId());

        PageResult<Map<String, Object>> statusFiltered = orderManagementService.listOrderSummaries(
                customerName, null, "已生成提单", 1, 20);
        assertThat(statusFiltered.getTotal()).isEqualTo(1);
        assertThat(statusFiltered.getRecords().get(0).get("id")).isEqualTo(second.getId());
    }

    @Test
    void releaseOrderSelectionOnlyReturnsOrdersThatCanStillGenerateTickets() {
        String customerName = "提单候选客户-" + System.nanoTime();
        OrderRfq quoted = orderWithItemsTicketsAndSheets(customerName, "REL-A", "已报价", 1, 1, 0, 0, 0);
        OrderRfq partial = orderWithItemsTicketsAndSheets(customerName, "REL-B", QuoteStatusWorkflow.PARTIALLY_RELEASED, 1, 1, 0, 1, 1);
        OrderRfq released = orderWithItemsTicketsAndSheets(customerName, "REL-C", QuoteStatusWorkflow.RELEASED, 1, 1, 0, 1, 1);

        PageResult<OrderRfq> candidates = orderManagementService.listReleaseCandidateOrders(
                customerName, null, null, 1, 20);

        assertThat(candidates.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(released.getId(), partial.getId(), quoted.getId());

        PageResult<OrderRfq> fullyReleased = orderManagementService.listReleaseCandidateOrders(
                customerName, null, QuoteStatusWorkflow.RELEASED, 1, 20);
        assertThat(fullyReleased.getTotal()).isEqualTo(1);
        assertThat(fullyReleased.getRecords()).extracting(OrderRfq::getId).containsExactly(released.getId());
    }

    @Test
    void quoteProcessOrderSelectionReturnsPricedAndQuotedOrders() {
        String customerName = "报价处理候选客户-" + System.nanoTime();
        OrderRfq priced = orderWithItemsTicketsAndSheets(customerName, "QP-A", "已核价", 1, 1, 0, 0, 0);
        OrderRfq quoted = orderWithItemsTicketsAndSheets(customerName, "QP-B", "已报价", 1, 1, 0, 0, 0);
        orderWithItemsTicketsAndSheets(customerName, "QP-C", "未核价", 1, 0, 0, 0, 0);
        OrderRfq partial = orderWithItemsTicketsAndSheets(customerName, "QP-D", "部分已提单", 1, 1, 0, 1, 1);

        PageResult<OrderRfq> candidates = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, null, 1, 20);

        assertThat(candidates.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(quoted.getId(), priced.getId());

        PageResult<OrderRfq> pricedOnly = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, "已核价", 1, 20);
        assertThat(pricedOnly.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(priced.getId());

        PageResult<OrderRfq> partialReleased = orderManagementService.listQuoteProcessCandidateOrders(
                customerName, null, "部分已提单", 1, 20);
        assertThat(partialReleased.getTotal()).isEqualTo(1);
        assertThat(partialReleased.getRecords())
                .extracting(OrderRfq::getId)
                .containsExactly(partial.getId());
    }
}
