package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
