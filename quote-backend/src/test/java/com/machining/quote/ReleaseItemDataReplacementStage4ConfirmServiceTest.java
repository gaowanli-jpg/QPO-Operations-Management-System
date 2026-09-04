package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemDataReplacementConfirmDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelParseResult;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.ReleaseItemDataReplacementExcelParser;
import com.machining.quote.service.ReleaseItemDataReplacementService;
import com.machining.quote.service.ReleaseItemMutationPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage4ConfirmServiceTest {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void confirmIsTransactionalLocksInOrderUpdatesEachChangedItemOnceAndReturnsDraftInstructions() throws Exception {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName", "remark", "salesOrderDate", "acceptedQty"),
                excelRow(2, "P-001", "A", values(
                        "materialName", "new material name",
                        "remark", "new remark",
                        "salesOrderDate", LocalDate.of(2026, 8, 20))),
                excelRow(3, "P-002", "A", values("acceptedQty", 2)));
        ItemQuoteVersion changed = item(201, 101, "P-001", "A", 10);
        changed.setDescription("old description");
        changed.setMaterialName("old material name");
        changed.setRemark("old remark");
        ItemQuoteVersion quantityOnly = item(202, 102, "P-002", "A", 5);
        TestContext context = context(
                parsed,
                List.of(
                        order(101, "RFQ-101", "ACME"),
                        order(102, "RFQ-102", "ACME")),
                List.of(changed, quantityOnly),
                List.of());
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET_USER"));

        String fingerprint = context.service.preview(
                List.of(ReleaseOrderSource.rfq(102), ReleaseOrderSource.rfq(101)), stream())
                .getPreviewFingerprint();
        ReleaseItemDataReplacementConfirmDTO result = context.service.confirm(
                List.of(ReleaseOrderSource.rfq(102), ReleaseOrderSource.rfq(101)),
                stream(),
                fingerprint);

        Method confirm = ReleaseItemDataReplacementService.class.getDeclaredMethod(
                "confirm", List.class, java.io.InputStream.class, String.class);
        assertThat(confirm.getAnnotation(Transactional.class)).isNotNull();
        var order = inOrder(context.orderMapper, context.itemMapper);
        order.verify(context.orderMapper).selectByIdForUpdate(101);
        order.verify(context.orderMapper).selectByIdForUpdate(102);
        order.verify(context.itemMapper).selectByIdForUpdate(201);
        order.verify(context.itemMapper).selectByIdForUpdate(202);

        assertThat(changed.getDescription()).isEqualTo("old description");
        assertThat(changed.getMaterialName()).isEqualTo("new material name");
        assertThat(changed.getRemark()).isEqualTo("new remark");
        assertThat(changed.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(quantityOnly.getQty()).isEqualTo(5);
        verify(context.itemMapper, times(1)).updateById(argThat(item -> item.getId().equals(201)));
        verify(context.itemMapper, never()).updateById(argThat(item -> item.getId().equals(202)));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary().getUpdatedItemCount()).isEqualTo(1);
        assertThat(result.getSummary().getQuantityAppliedCount()).isEqualTo(1);
        assertThat(result.getAffectedRows()).hasSize(2)
                .extracting(row -> row.get("releaseItemKey"))
                .containsExactly("RFQ:201", "RFQ:202");
        assertThat(result.getAcceptedQtyUpdates()).singleElement().satisfies(update -> {
            assertThat(update.getReleaseItemKey()).isEqualTo("RFQ:202");
            assertThat(update.getItemId()).isEqualTo(202);
            assertThat(update.getAcceptedQty()).isEqualTo(2);
        });

        verify(context.auditService).record(
                eq("RELEASE_ITEM_DATA_REPLACEMENT"),
                eq("item_quote_version"),
                eq("101,102"),
                isNull(),
                argThat(summary -> safeSummary(summary)));
    }

    @Test
    void changedTargetOrTicketQuantityRejectsStaleFingerprintBeforeUpdates() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new")));
        ItemQuoteVersion item = item(201, 101, "P-001", "A", 5);
        item.setMaterialName("old");
        TestContext changedTarget = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME")),
                List.of(item),
                List.of());
        String fingerprint = changedTarget.service.preview(List.of(ReleaseOrderSource.rfq(101)), stream())
                .getPreviewFingerprint();
        item.setMaterialName("changed after preview");

        assertThatThrownBy(() -> changedTarget.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), fingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class)
                .hasMessage("数据已发生变化，请重新预览");
        verify(changedTarget.itemMapper, never()).updateById(any());
        verify(changedTarget.auditService, never()).record(any(), any(), any(), any(), any());

        ItemQuoteVersion ticketedItem = item(202, 101, "P-002", "A", 5);
        ReleaseItemDataReplacementExcelParseResult ticketParsed = parseResult(
                List.of("acceptedQty"),
                excelRow(2, "P-002", "A", values("acceptedQty", 2)));
        TestContext changedTickets = context(
                ticketParsed,
                List.of(order(101, "RFQ-101", "ACME")),
                List.of(ticketedItem),
                List.of());
        String ticketFingerprint = changedTickets.service.preview(
                List.of(ReleaseOrderSource.rfq(101)), stream()).getPreviewFingerprint();
        when(changedTickets.ticketMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(ticket(9001, 101, 202, 1)));

        assertThatThrownBy(() -> changedTickets.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), ticketFingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class);
        verify(changedTickets.itemMapper, never()).updateById(any());
    }

    @Test
    void changedOrderStatusOrCustomerRejectsAsFingerprintConflict() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new")));
        OrderRfq order = order(101, "RFQ-101", "ACME");
        ItemQuoteVersion item = item(201, 101, "P-001", "A", 5);
        item.setMaterialName("old");
        TestContext context = context(parsed, List.of(order), List.of(item), List.of());
        String fingerprint = context.service.preview(List.of(ReleaseOrderSource.rfq(101)), stream())
                .getPreviewFingerprint();

        order.setStatus(QuoteStatusWorkflow.PRICED);
        assertThatThrownBy(() -> context.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), fingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class);

        order.setStatus(QuoteStatusWorkflow.QUOTED);
        order.setCustomerName("CHANGED-CUSTOMER");
        assertThatThrownBy(() -> context.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), fingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class);
        verify(context.itemMapper, never()).updateById(any());
    }

    @Test
    void updateFailureStopsBatchBeforeAuditAndQuantityOnlyConfirmationDoesNotWriteAudit() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-001", "A", values("materialName", "new-1")),
                excelRow(3, "P-002", "A", values("materialName", "new-2")));
        ItemQuoteVersion first = item(201, 101, "P-001", "A", 5);
        first.setMaterialName("old-1");
        ItemQuoteVersion second = item(202, 101, "P-002", "A", 5);
        second.setMaterialName("old-2");
        TestContext failing = context(
                parsed,
                List.of(order(101, "RFQ-101", "ACME")),
                List.of(first, second),
                List.of());
        when(failing.itemMapper.updateById(any())).thenAnswer(invocation -> {
            ItemQuoteVersion updating = invocation.getArgument(0);
            if (updating.getId().equals(202)) {
                throw new RuntimeException("database failure");
            }
            return 1;
        });
        String fingerprint = failing.service.preview(List.of(ReleaseOrderSource.rfq(101)), stream())
                .getPreviewFingerprint();

        assertThatThrownBy(() -> failing.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), fingerprint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database failure");
        verify(failing.auditService, never()).record(any(), any(), any(), any(), any());

        ReleaseItemDataReplacementExcelParseResult quantityParsed = parseResult(
                List.of("acceptedQty"),
                excelRow(2, "P-003", "A", values("acceptedQty", 2)));
        TestContext quantityOnly = context(
                quantityParsed,
                List.of(order(101, "RFQ-101", "ACME")),
                List.of(item(203, 101, "P-003", "A", 5)),
                List.of());
        String quantityFingerprint = quantityOnly.service.preview(
                List.of(ReleaseOrderSource.rfq(101)), stream()).getPreviewFingerprint();

        ReleaseItemDataReplacementConfirmDTO result = quantityOnly.service.confirm(
                List.of(ReleaseOrderSource.rfq(101)), stream(), quantityFingerprint);

        assertThat(result.getSummary().getUpdatedItemCount()).isZero();
        assertThat(result.getSummary().getQuantityAppliedCount()).isEqualTo(1);
        verify(quantityOnly.itemMapper, never()).updateById(any());
        verify(quantityOnly.auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void auditTargetIdUsesBoundedBatchIdentifierForLargeRfqSets() {
        ReleaseItemDataReplacementExcelParseResult parsed = parseResult(
                List.of("materialName"),
                excelRow(2, "P-900", "A", values("materialName", "new")));
        List<OrderRfq> orders = new ArrayList<>();
        List<ReleaseOrderSource> sources = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            int rfqId = 100000 + index;
            orders.add(order(rfqId, "RFQ-" + rfqId, "ACME"));
            sources.add(ReleaseOrderSource.rfq(rfqId));
        }
        ItemQuoteVersion changed = item(9001, 100000, "P-900", "A", 5);
        changed.setMaterialName("old");
        TestContext context = context(parsed, orders, List.of(changed), List.of());
        String fingerprint = context.service.preview(sources, stream()).getPreviewFingerprint();

        context.service.confirm(sources, stream(), fingerprint);

        verify(context.auditService).record(
                eq("RELEASE_ITEM_DATA_REPLACEMENT"),
                eq("item_quote_version"),
                argThat(targetId -> {
                    String text = String.valueOf(targetId);
                    return text.length() <= 120 && text.matches("rfq-batch-[0-9a-f]{24}");
                }),
                isNull(),
                argThat(summary -> summary instanceof Map<?, ?> map
                        && map.get("rfqIds") instanceof List<?> ids
                        && ids.size() == 40));
    }

    private boolean safeSummary(Object value) {
        if (!(value instanceof Map<?, ?> summary)) {
            return false;
        }
        String text = summary.toString();
        return summary.get("updatedItemCount").equals(1)
                && summary.get("quantityAppliedCount").equals(1)
                && summary.containsKey("changedFieldCounts")
                && !text.contains("new material name")
                && !text.contains("old material name")
                && !text.contains("beforeValue")
                && !text.contains("afterValue")
                && !text.contains("rows");
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
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService rowService = mock(InternalTargetLeadTimeService.class);
        when(parser.parse(any())).thenReturn(parsed);
        when(orderMapper.selectBatchIds(any())).thenReturn(orders);
        for (OrderRfq order : orders) {
            when(orderMapper.selectByIdForUpdate(order.getId())).thenReturn(order);
        }
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(items);
        for (ItemQuoteVersion item : items) {
            when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        }
        when(ticketMapper.selectList(any(Wrapper.class))).thenReturn(tickets);
        when(itemMapper.updateById(any())).thenReturn(1);
        when(rowService.buildReplacementAffectedRow(any(), any(Integer.class), any()))
                .thenAnswer(invocation -> {
                    ItemQuoteVersion item = invocation.getArgument(0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("releaseItemKey", "RFQ:" + item.getId());
                    row.put("description", item.getDescription());
                    row.put("materialName", item.getMaterialName());
                    row.put("remark", item.getRemark());
                    row.put("salesOrderDate", item.getSalesOrderDate());
                    row.put("qty", item.getQty());
                    return row;
                });
        ReleaseItemDataReplacementService service = new ReleaseItemDataReplacementService(
                parser,
                orderMapper,
                itemMapper,
                ticketMapper,
                dataScopeService,
                new ReleaseItemMutationPolicy(),
                auditService,
                rowService);
        return new TestContext(service, orderMapper, itemMapper, ticketMapper, auditService);
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
        row.setNormalizedPartNo(partNo);
        row.setNormalizedVersion(version);
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

    private static OrderRfq order(int id, String rfqNo, String customerName) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName(customerName);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private static ItemQuoteVersion item(int id, int rfqId, String partNo, String version, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(qty);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
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
            AuditService auditService) {
    }
}
