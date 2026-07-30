package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketReleaseBatchPagingTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");

    @Test
    void batchItemPagingUsesSelectedOrdersAndBackendLimitOffset() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OrderItemPageService service = new OrderItemPageService(orderMapper, itemMapper, dataScopeService, jdbcTemplate);
        OrderRfq firstOrder = order(101);
        OrderRfq secondOrder = order(102);
        OrderRfq hiddenOrder = order(103);
        when(orderMapper.selectBatchIds(List.of(101, 102, 103))).thenReturn(List.of(firstOrder, secondOrder, hiddenOrder));
        doThrow(new RuntimeException("No data permission for order 103"))
                .when(dataScopeService).assertCanAccessOrder(hiddenOrder);
        when(itemMapper.selectCount(any(QueryWrapper.class))).thenReturn(4L);
        ItemQuoteVersion firstItem = item(201, 102, "BATCH-B-01");
        ItemQuoteVersion secondItem = item(202, 102, "BATCH-B-02");
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(firstItem, secondItem));

        ReleaseItemBatchQueryReq req = new ReleaseItemBatchQueryReq();
        req.setRfqIds(List.of(101, 102, 103, 101));
        req.setPage(2);
        req.setPageSize(2);

        OrderItemPageDTO page = service.pageItemsBatch(req);

        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(ItemQuoteVersion::getId).containsExactly(201, 202);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<ItemQuoteVersion>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(itemMapper).selectCount(queryCaptor.capture());
        verify(itemMapper).selectList(queryCaptor.capture());
        QueryWrapper<ItemQuoteVersion> countQuery = queryCaptor.getAllValues().get(0);
        QueryWrapper<ItemQuoteVersion> pageQuery = queryCaptor.getAllValues().get(1);
        assertThat(countQuery.getSqlSegment()).contains("rfq_id IN").doesNotContain("LIMIT");
        assertThat(pageQuery.getSqlSegment())
                .contains("rfq_id IN")
                .contains("ORDER BY rfq_id ASC,id ASC")
                .contains("LIMIT 2 OFFSET 2");
        assertThat(pageQuery.getParamNameValuePairs().values())
                .contains(101, 102)
                .doesNotContain(103);
    }

    @Test
    void batchReleaseContractsExposeStableServerSidePagingApi() throws Exception {
        assertThat(MAIN_JAVA.resolve("dto").resolve("ReleaseItemBatchQueryReq.java")).exists();

        String marketController = source("controller", "MarketController.java");
        assertThat(marketController)
                .contains("@PostMapping(\"/release/items/batch\")")
                .contains("@RequirePermission(\"market:release:view\")")
                .contains("internalTargetLeadTimeService.pageItemRowsBatch(req)");

        String releaseService = source("service", "InternalTargetLeadTimeService.java");
        assertThat(releaseService)
                .contains("public PageResult<Map<String, Object>> pageItemRowsBatch(ReleaseItemBatchQueryReq req)")
                .contains("orderItemPageService.pageItemsBatch(queryReq)");
        assertThat(javaMethod(releaseService, "pageItemRowsBatch"))
                .doesNotContain("buildItemRows(")
                .doesNotContain("listItems(");

        String itemPageService = source("service", "OrderItemPageService.java");
        assertThat(itemPageService)
                .contains("public OrderItemPageDTO pageItemsBatch(ReleaseItemBatchQueryReq req)")
                .contains("selectCount(buildFilteredBatchQuery")
                .contains("LIMIT \" + pageSize + \" OFFSET \" + offset")
                .contains(".in(\"rfq_id\", accessibleRfqIds)")
                .contains(".orderByAsc(\"rfq_id\")")
                .contains(".orderByAsc(\"id\")");
    }

    @Test
    void singleReleaseItemPagingReturnsDrawingStatusWithoutChangingBackendPaging() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderItemPageService pageService = mock(OrderItemPageService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, pageService);
        OrderRfq order = releaseOrder(101, "RFQ-DRAWING-A");
        ItemQuoteVersion withDrawing = releaseItem(201, 101, "DRAW-001", "A");
        withDrawing.setDrawingUrl("/uploads/ACME/RFQ-DRAWING-A/DRAW-001.pdf");
        ItemQuoteVersion missingDrawing = releaseItem(202, 101, "DRAW-002", "A");
        missingDrawing.setDrawingUrl(" ");
        when(orderMapper.selectById(101)).thenReturn(order);
        when(pageService.pageItems(any(OrderItemPageReq.class)))
                .thenReturn(OrderItemPageDTO.of(List.of(withDrawing, missingDrawing), 7, 3, 2, null));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        PageResult<Map<String, Object>> page = service.pageItemRows(101, 3, 2, "DRAW", QuoteStatusWorkflow.QUOTED, false);

        assertThat(page.getTotal()).isEqualTo(7);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getRecords().get(0))
                .containsEntry("id", 201)
                .containsEntry("drawingUrl", "/uploads/ACME/RFQ-DRAWING-A/DRAW-001.pdf")
                .containsEntry("hasDrawing", true);
        assertThat(page.getRecords().get(1))
                .containsEntry("id", 202)
                .containsEntry("drawingUrl", " ")
                .containsEntry("hasDrawing", false);
        ArgumentCaptor<OrderItemPageReq> reqCaptor = ArgumentCaptor.forClass(OrderItemPageReq.class);
        verify(pageService).pageItems(reqCaptor.capture());
        assertThat(reqCaptor.getValue())
                .extracting(OrderItemPageReq::getRfqId, OrderItemPageReq::getPage, OrderItemPageReq::getPageSize,
                        OrderItemPageReq::getKeyword, OrderItemPageReq::getStatus)
                .containsExactly(101, 3, 2, "DRAW", QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void batchReleaseItemPagingReturnsDrawingStatusWithoutChangingBackendPaging() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        OrderItemPageService pageService = mock(OrderItemPageService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, pageService);
        OrderRfq firstOrder = releaseOrder(101, "RFQ-DRAWING-A");
        OrderRfq secondOrder = releaseOrder(102, "RFQ-DRAWING-B");
        ItemQuoteVersion row = releaseItem(203, 102, "DRAW-B-001", "B");
        row.setDrawingUrl("/uploads/ACME/RFQ-DRAWING-B/DRAW-B-001.pdf");
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(firstOrder, secondOrder));
        when(pageService.pageItemsBatch(any(ReleaseItemBatchQueryReq.class)))
                .thenReturn(OrderItemPageDTO.of(List.of(row), 5, 2, 1, null));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        ReleaseItemBatchQueryReq req = new ReleaseItemBatchQueryReq();
        req.setRfqIds(List.of(101, 102));
        req.setPage(2);
        req.setPageSize(1);
        req.setRemainingOnly(false);

        PageResult<Map<String, Object>> page = service.pageItemRowsBatch(req);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0))
                .containsEntry("id", 203)
                .containsEntry("rfqId", 102)
                .containsEntry("rfqNo", "RFQ-DRAWING-B")
                .containsEntry("drawingUrl", "/uploads/ACME/RFQ-DRAWING-B/DRAW-B-001.pdf")
                .containsEntry("hasDrawing", true);
        ArgumentCaptor<ReleaseItemBatchQueryReq> reqCaptor = ArgumentCaptor.forClass(ReleaseItemBatchQueryReq.class);
        verify(pageService).pageItemsBatch(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getRfqIds()).containsExactly(101, 102);
        assertThat(reqCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(reqCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchPartSelectionWithoutRfqNoMatchesAcrossAllSelectedOrders() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper);
        OrderRfq firstOrder = releaseOrder(101, "RFQ-BATCH-A");
        OrderRfq secondOrder = releaseOrder(102, "RFQ-BATCH-B");
        ItemQuoteVersion firstItem = releaseItem(201, 101, "SHARED-IMPORT-01", "A");
        ItemQuoteVersion secondItem = releaseItem(202, 102, "SHARED-IMPORT-01", "A");
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(firstOrder, secondOrder));
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(firstItem, secondItem));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = service.matchPartSelectionBatch(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)),
                List.of(importedSelection("", "SHARED-IMPORT-01", "A")));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("data");

        assertThat(result)
                .containsEntry("importedCount", 1)
                .containsEntry("matchedCount", 2)
                .containsEntry("selectedCount", 2)
                .containsEntry("skippedCount", 0);
        assertThat(rows)
                .extracting(row -> row.get("id"))
                .containsExactly(201, 202);
        assertThat(rows)
                .extracting(row -> row.get("rfqNo"))
                .containsExactly("RFQ-BATCH-A", "RFQ-BATCH-B");
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchPartSelectionWithRfqNoMatchesOnlySpecifiedOrder() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper);
        OrderRfq firstOrder = releaseOrder(101, "RFQ-BATCH-A");
        OrderRfq secondOrder = releaseOrder(102, "RFQ-BATCH-B");
        ItemQuoteVersion firstItem = releaseItem(201, 101, "RFQ-SPECIFIC-01", "A");
        ItemQuoteVersion secondItem = releaseItem(202, 102, "RFQ-SPECIFIC-01", "A");
        when(orderMapper.selectBatchIds(List.of(101, 102))).thenReturn(List.of(firstOrder, secondOrder));
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(firstItem, secondItem));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = service.matchPartSelectionBatch(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)),
                List.of(importedSelection("RFQ-BATCH-B", "RFQ-SPECIFIC-01", "A")));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("data");

        assertThat(result)
                .containsEntry("importedCount", 1)
                .containsEntry("matchedCount", 1)
                .containsEntry("selectedCount", 1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("id", 202)
                .containsEntry("rfqId", 102)
                .containsEntry("rfqNo", "RFQ-BATCH-B")
                .containsEntry("customerName", "ACME")
                .containsEntry("orderStatus", QuoteStatusWorkflow.QUOTED);
    }

    private OrderRfq order(Integer id) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerName("ACME");
        return order;
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, String partNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        return item;
    }

    private InternalTargetLeadTimeService releaseService(
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper) {
        return releaseService(orderMapper, itemMapper, ticketMapper, null);
    }

    private InternalTargetLeadTimeService releaseService(
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            OrderItemPageService orderItemPageService) {
        InternalTargetLeadTimeService service = new InternalTargetLeadTimeService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        if (orderItemPageService != null) {
            ReflectionTestUtils.setField(service, "orderItemPageService", orderItemPageService);
        }
        return service;
    }

    private OrderRfq releaseOrder(Integer id, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private ItemQuoteVersion releaseItem(Integer id, Integer rfqId, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(1);
        item.setFinalUnitPrice(10.0);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private Map<String, String> importedSelection(String rfqNo, String partNo, String version) {
        return Map.of(
                "rfqNo", rfqNo,
                "normalizedRfqNo", rfqNo == null ? "" : rfqNo.trim().toUpperCase(),
                "partNo", partNo,
                "normalizedPartNo", partNo == null ? "" : partNo.trim().toUpperCase(),
                "version", version,
                "normalizedVersion", version == null ? "" : version.trim().toUpperCase()
        );
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }

    private String javaMethod(String source, String methodName) {
        int nameIndex = source.indexOf(methodName + "(");
        assertThat(nameIndex).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int start = source.lastIndexOf('\n', nameIndex);
        int firstBrace = source.indexOf('{', nameIndex);
        assertThat(firstBrace).as("method %s has a body", methodName).isGreaterThanOrEqualTo(0);
        int depth = 0;
        for (int index = firstBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(Math.max(0, start), index + 1);
                }
            }
        }
        throw new AssertionError("method body not closed: " + methodName);
    }
}
