package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.DataScopeAccessDeniedException;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelParseResult;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementPreviewDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementRowStatus;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.ReleaseItemDataReplacementExcelParser;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import com.machining.quote.service.ReleaseItemMutationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage3PreviewServiceTest {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void previewsMultipleSameCustomerRfqsAndProducesEveryNonErrorOutcomeWithoutWrites() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName", "remark", "acceptedQty"),
                excelRow(2, "P-001", "A", values("materialName", "new material name")),
                excelRow(3, "P-002", "A", values("acceptedQty", 2)),
                excelRow(4, "P-003", "A", values("remark", "same remark")),
                excelRow(5, "P-004", "A", values("materialName", "ignored after release")),
                excelRow(6, "P-999", "A", values("materialName", "unmatched")));

        ItemQuoteVersion changed = item(201, 101, "P-001", "A", 10, QuoteStatusWorkflow.QUOTED);
        changed.setMaterialName("old material name");
        ItemQuoteVersion quantityOnly = item(202, 101, "P-002", "A", 5, QuoteStatusWorkflow.QUOTED);
        ItemQuoteVersion unchanged = item(203, 102, "P-003", "A", 6, QuoteStatusWorkflow.QUOTED);
        unchanged.setRemark("same remark");
        ItemQuoteVersion released = item(204, 102, "P-004", "A", 4, QuoteStatusWorkflow.ITEM_RELEASED);

        TestContext context = context(
                parsed,
                List.of(
                        order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.QUOTED),
                        order(102, "RFQ-102", "ACME", QuoteStatusWorkflow.PARTIALLY_RELEASED)),
                List.of(changed, quantityOnly, unchanged, released),
                List.of(ticket(9001, 102, 204, 4)));
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET_USER"));

        ReleaseItemDataReplacementPreviewDTO result = context.service.preview(
                List.of(ReleaseOrderSource.rfq(102), ReleaseOrderSource.rfq(101)),
                new ByteArrayInputStream(new byte[]{1}));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isCanConfirm()).isTrue();
        assertThat(result.getRecognizedFields()).containsExactly("materialName", "remark", "acceptedQty");
        assertThat(result.getPreviewFingerprint()).matches("[0-9a-f]{64}");
        assertThat(result.getRows()).extracting(ReleaseItemDataReplacementRowDTO::getStatus)
                .containsExactly(
                        ReleaseItemDataReplacementRowStatus.CHANGED,
                        ReleaseItemDataReplacementRowStatus.QUANTITY_ONLY,
                        ReleaseItemDataReplacementRowStatus.UNCHANGED,
                        ReleaseItemDataReplacementRowStatus.CHANGED,
                        ReleaseItemDataReplacementRowStatus.UNMATCHED);

        ReleaseItemDataReplacementRowDTO changedRow = result.getRows().get(0);
        assertThat(changedRow.getRfqId()).isEqualTo(101);
        assertThat(changedRow.getRfqNo()).isEqualTo("RFQ-101");
        assertThat(changedRow.getItemId()).isEqualTo(201);
        assertThat(changedRow.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getField()).isEqualTo("materialName");
            assertThat(change.getLabel()).isEqualTo("材料名称");
            assertThat(change.getBeforeValue()).isEqualTo("old material name");
            assertThat(change.getAfterValue()).isEqualTo("new material name");
        });
        assertThat(result.getRows().get(1).getAcceptedQty()).isEqualTo(2);
        assertThat(result.getRows().get(3).getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getField()).isEqualTo("materialName");
            assertThat(change.getAfterValue()).isEqualTo("ignored after release");
        });
        assertThat(result.getRows().get(3).getReason()).isEmpty();
        assertThat(result.getRows().get(4).getReason()).isEqualTo("当前所选询价单范围内未找到对应零件");

        assertThat(result.getSummary().getImportedCount()).isEqualTo(5);
        assertThat(result.getSummary().getMatchedCount()).isEqualTo(4);
        assertThat(result.getSummary().getChangedCount()).isEqualTo(2);
        assertThat(result.getSummary().getQuantityOnlyCount()).isEqualTo(1);
        assertThat(result.getSummary().getUnchangedCount()).isEqualTo(1);
        assertThat(result.getSummary().getSkippedCount()).isZero();
        assertThat(result.getSummary().getUnmatchedCount()).isEqualTo(1);
        assertThat(result.getSummary().getErrorCount()).isZero();

        verify(context.orderMapper).selectBatchIds(any());
        verify(context.itemMapper).selectList(any(Wrapper.class));
        verify(context.ticketMapper).selectList(any(Wrapper.class));
        verify(context.itemMapper, never()).updateById(any());
        verify(context.ticketMapper, never()).updateById(any());
    }

    @Test
    void parserErrorsRemainVisibleButAcceptedQtyCanExceedInquiryRemainingQty() {
        ReleaseItemDataReplacementExcelRowDTO invalidDate = excelRow(
                2, "P-001", "A", values());
        ReleaseItemDataReplacementExcelErrorDTO dateError = new ReleaseItemDataReplacementExcelErrorDTO();
        dateError.setExcelRowNumber(2);
        dateError.setColumnNumber(3);
        dateError.setField("salesOrderDate");
        dateError.setLabel("下单日期");
        dateError.setMessage("第2行第3列字段[下单日期]不是有效日期");
        invalidDate.getErrors().add(dateError);

        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("salesOrderDate", "acceptedQty"),
                invalidDate,
                excelRow(3, "P-002", "A", values("acceptedQty", 8)));
        TestContext context = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.QUOTED)),
                List.of(
                        item(201, 101, "P-001", "A", 10, QuoteStatusWorkflow.QUOTED),
                        item(202, 101, "P-002", "A", 5, QuoteStatusWorkflow.QUOTED)),
                List.of(ticket(9001, 101, 202, 1)));

        ReleaseItemDataReplacementPreviewDTO result = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101)),
                new ByteArrayInputStream(new byte[]{1}));

        assertThat(result.isCanConfirm()).isTrue();
        assertThat(result.getRows()).extracting(ReleaseItemDataReplacementRowDTO::getStatus)
                .containsExactly(ReleaseItemDataReplacementRowStatus.ERROR, ReleaseItemDataReplacementRowStatus.QUANTITY_ONLY);
        assertThat(result.getRows().get(0).getReason()).contains("第2行第3列").contains("下单日期");
        assertThat(result.getRows().get(1).getAcceptedQty()).isEqualTo(8);
        assertThat(result.getRows().get(1).getReason()).isEmpty();
        assertThat(result.getSummary().getMatchedCount()).isEqualTo(1);
        assertThat(result.getSummary().getQuantityOnlyCount()).isEqualTo(1);
        assertThat(result.getSummary().getErrorCount()).isEqualTo(1);
    }

    @Test
    void duplicateDatabasePartVersionExpandsToAllMatchingItems() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new")));
        TestContext context = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.QUOTED)),
                List.of(
                        item(201, 101, "P-001", "A", 10, QuoteStatusWorkflow.QUOTED),
                        item(202, 101, " p-001 ", " a ", 10, QuoteStatusWorkflow.QUOTED)),
                List.of());

        ReleaseItemDataReplacementPreviewDTO result = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101)),
                new ByteArrayInputStream(new byte[]{1}));

        assertThat(result.isCanConfirm()).isTrue();
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows()).extracting(ReleaseItemDataReplacementRowDTO::getItemId)
                .containsExactly(201, 202);
        assertThat(result.getRows()).extracting(ReleaseItemDataReplacementRowDTO::getStatus)
                .containsExactly(ReleaseItemDataReplacementRowStatus.CHANGED, ReleaseItemDataReplacementRowStatus.CHANGED);
        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(result.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(result.getSummary().getChangedCount()).isEqualTo(2);
        assertThat(result.getSummary().getErrorCount()).isZero();
    }

    @Test
    void unchangedAndUnmatchedRowsCannotBeConfirmedButReleasedRowsRemainMutable() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("remark"),
                excelRow(2, "P-001", "A", values("remark", "same")),
                excelRow(3, "P-002", "A", values("remark", "skip")),
                excelRow(4, "P-999", "A", values("remark", "none")));
        ItemQuoteVersion unchanged = item(201, 101, "P-001", "A", 3, QuoteStatusWorkflow.QUOTED);
        unchanged.setRemark("same");
        ItemQuoteVersion released = item(202, 101, "P-002", "A", 2, QuoteStatusWorkflow.ITEM_RELEASED);
        TestContext context = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.RELEASED)),
                List.of(unchanged, released),
                List.of(ticket(9001, 101, 202, 2)));

        ReleaseItemDataReplacementPreviewDTO result = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101)),
                new ByteArrayInputStream(new byte[]{1}));

        assertThat(result.isCanConfirm()).isTrue();
        assertThat(result.getRows()).extracting(ReleaseItemDataReplacementRowDTO::getStatus)
                .containsExactly(
                        ReleaseItemDataReplacementRowStatus.UNCHANGED,
                        ReleaseItemDataReplacementRowStatus.CHANGED,
                        ReleaseItemDataReplacementRowStatus.UNMATCHED);
        assertThat(result.getRows().get(1).getReason()).isEmpty();
        assertThat(result.getSummary().getChangedCount()).isEqualTo(1);
        assertThat(result.getSummary().getSkippedCount()).isZero();
    }

    @Test
    void fingerprintIsStableForSourceOrderButChangesWithUserOrRelevantItemState() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new")));
        ItemQuoteVersion item = item(201, 101, "P-001", "A", 10, QuoteStatusWorkflow.QUOTED);
        item.setMaterialName("old");
        TestContext context = context(
                parsed,
                List.of(
                        order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.QUOTED),
                        order(102, "RFQ-102", "ACME", QuoteStatusWorkflow.QUOTED)),
                List.of(item),
                List.of());
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET_USER"));

        String first = context.service.preview(
                List.of(ReleaseOrderSource.rfq(102), ReleaseOrderSource.rfq(101)), stream()).getPreviewFingerprint();
        String reordered = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)), stream()).getPreviewFingerprint();
        assertThat(reordered).isEqualTo(first);

        AuthContext.set(new AuthContext.SessionUser(8, "other", "Other", "MARKET_USER"));
        String otherUser = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)), stream()).getPreviewFingerprint();
        assertThat(otherUser).isNotEqualTo(first);

        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET_USER"));
        item.setMaterialName("changed after preview");
        String changedState = context.service.preview(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)), stream()).getPreviewFingerprint();
        assertThat(changedState).isNotEqualTo(first);
    }

    @Test
    void rejectsNonRfqCrossCustomerAndInvalidStatusBeforeLoadingItems() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new")));

        TestContext salesOrder = context(parsed, List.of(), List.of(), List.of());
        assertThatThrownBy(() -> salesOrder.service.preview(
                List.of(ReleaseOrderSource.salesOrder(900L)), stream()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只支持 RFQ");
        verify(salesOrder.orderMapper, never()).selectBatchIds(any());
        verify(salesOrder.itemMapper, never()).selectList(any(Wrapper.class));

        TestContext crossCustomer = context(
                parsed,
                List.of(
                        order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.QUOTED),
                        order(102, "RFQ-102", "OTHER", QuoteStatusWorkflow.QUOTED)),
                List.of(),
                List.of());
        assertThatThrownBy(() -> crossCustomer.service.preview(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)), stream()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一客户");
        verify(crossCustomer.itemMapper, never()).selectList(any(Wrapper.class));

        TestContext invalidStatus = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME", QuoteStatusWorkflow.PRICED)),
                List.of(),
                List.of());
        assertThatThrownBy(() -> invalidStatus.service.preview(
                List.of(ReleaseOrderSource.rfq(101)), stream()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能进入议价提单");
        verify(invalidStatus.itemMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void dataScopeDenialReturnsGenericForbiddenBeforeReadingItemsOrTickets() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "SECRET-PART", "A", values("materialName", "new")));
        TestContext context = context(
                parsed,
                List.of(order(101, "RFQ-SECRET", "SECRET-CUSTOMER", QuoteStatusWorkflow.QUOTED)),
                List.of(),
                List.of());
        doThrow(new RuntimeException("No data permission for order 101"))
                .when(context.dataScopeService).assertCanAccessOrder(any(OrderRfq.class));

        assertThatThrownBy(() -> context.service.preview(
                List.of(ReleaseOrderSource.rfq(101)), stream()))
                .isInstanceOf(DataScopeAccessDeniedException.class)
                .hasMessage("当前账号无权访问所选询价单");

        verify(context.itemMapper, never()).selectList(any(Wrapper.class));
        verify(context.ticketMapper, never()).selectList(any(Wrapper.class));
    }

    private TestContext context(
            ReleaseItemDataReplacementExcelParseResult parsed,
            List<OrderRfq> orders,
            List<ItemQuoteVersion> items,
            List<OrderDeliveryTicket> tickets) {
        ReleaseItemDataReplacementExcelParser parser = mock(ReleaseItemDataReplacementExcelParser.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(parser.parse(any())).thenReturn(parsed);
        when(orderMapper.selectBatchIds(any())).thenReturn(orders);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(items);
        when(ticketMapper.selectList(any(Wrapper.class))).thenReturn(tickets);
        ReleaseItemDataReplacementService service = new ReleaseItemDataReplacementService(
                parser,
                orderMapper,
                itemMapper,
                ticketMapper,
                dataScopeService,
                new ReleaseItemMutationPolicy());
        return new TestContext(service, orderMapper, itemMapper, ticketMapper, dataScopeService);
    }

    private static ReleaseItemDataReplacementExcelParseResult parseResult(
            List<String> fields,
            ReleaseItemDataReplacementExcelRowDTO... rows) {
        ReleaseItemDataReplacementExcelParseResult result = new ReleaseItemDataReplacementExcelParseResult();
        result.setHeaderRowNumber(1);
        result.setRecognizedFields(fields);
        result.setRows(new ArrayList<>(List.of(rows)));
        return result;
    }

    private static ReleaseItemDataReplacementExcelRowDTO excelRow(
            int rowNumber,
            String partNo,
            String version,
            Map<String, Object> values) {
        ReleaseItemDataReplacementExcelRowDTO row = new ReleaseItemDataReplacementExcelRowDTO();
        row.setExcelRowNumber(rowNumber);
        row.setPartNo(partNo);
        row.setVersion(version);
        row.setNormalizedPartNo(partNo == null ? "" : partNo.trim().toUpperCase());
        row.setNormalizedVersion(version == null ? "" : version.trim().toUpperCase());
        row.setValues(new LinkedHashMap<>(values));
        return row;
    }

    private static Map<String, Object> values(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return values;
    }

    private static OrderRfq order(int id, String rfqNo, String customer, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName(customer);
        order.setStatus(status);
        return order;
    }

    private static ItemQuoteVersion item(
            int id,
            int rfqId,
            String partNo,
            String version,
            int qty,
            String status) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(qty);
        item.setStatus(status);
        return item;
    }

    private static OrderDeliveryTicket ticket(int id, int rfqId, int itemId, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(rfqId);
        ticket.setItemId(itemId);
        ticket.setSourceType("RFQ");
        ticket.setSourceOrderId((long) rfqId);
        ticket.setSourceItemId((long) itemId);
        ticket.setQty(qty);
        return ticket;
    }

    private static ByteArrayInputStream stream() {
        return new ByteArrayInputStream(new byte[]{1});
    }

    private record TestContext(
            ReleaseItemDataReplacementService service,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService) {
    }
}
