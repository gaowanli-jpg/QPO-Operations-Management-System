package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderReleaseAppendResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderAutoSnapshotCompletionService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderReleaseAppendService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderReleaseAppendServiceTest extends SalesOrderQpoImportTestSupport {

    @Test
    void appendsParsedExcelRowsToExistingUnsyncedSalesOrderAndOnlyTicketsNewItems() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        SalesOrderAutoSnapshotCompletionService snapshotCompletionService = mock(SalesOrderAutoSnapshotCompletionService.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        AuditService auditService = mock(AuditService.class);
        BusinessFileService fileService = salesOrderImportDrawingStorage();
        SalesOrderReleaseAppendService service = service(
                parser,
                headerMapper,
                itemMapper,
                dataScopeService,
                snapshotCompletionService,
                deliveryService,
                auditService,
                fileService
        );
        SalesOrderHeader target = targetSalesOrder(301L, "未同步");
        target.setStatus(QuoteStatusWorkflow.QUOTED);
        SalesOrderItem existing = existingSalesItem(701L, 7, 3, 1);
        SalesOrderQpoExcelParseResult parsed = appendParseResult();
        when(headerMapper.selectByIdForUpdate(301L)).thenReturn(target);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(parser.parse(any(InputStream.class), any(SalesOrderQpoImportDefaultsDTO.class))).thenReturn(parsed);
        ArgumentCaptor<SalesOrderItem> insertedCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        AtomicLong nextInsertedId = new AtomicLong(501L);
        org.mockito.Mockito.doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId(nextInsertedId.getAndIncrement());
            return 1;
        }).when(itemMapper).insert(insertedCaptor.capture());
        when(snapshotCompletionService.completeImportedOrderItems(301L, List.of(501L, 502L)))
                .thenReturn(matchResp(2, 1, 1, "auto unmatched 1"));
        when(deliveryService.generateImportedSalesOrderTicketsForItems(301L, List.of(501L, 502L), "SALES_ORDER_APPEND_RELEASE"))
                .thenReturn(List.of(ticket(9001, 501L, 4), ticket(9002, 502L, 6)));

        SalesOrderReleaseAppendResp resp = service.appendItems(
                301L,
                new MockMultipartFile(
                        "excelFile",
                        "append-sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "EXCEL-RAW-CONTENT-SHOULD-NOT-BE-LOGGED".getBytes(StandardCharsets.UTF_8)
                ),
                List.of(),
                List.of()
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getTargetSalesOrderId()).isEqualTo(301L);
        assertThat(resp.getTargetSalesOrderNo()).isEqualTo("SO-TARGET-001");
        assertThat(resp.getAppendedItemCount()).isEqualTo(2);
        assertThat(resp.getTicketCount()).isEqualTo(2);
        assertThat(resp.getTicketQty()).isEqualTo(10);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows()).extracting("lineNo").containsExactly(8, 9);
        assertThat(resp.getRows()).extracting("partNo").containsExactly("P-APPEND-1", "P-APPEND-1");
        assertThat(resp.getRows()).extracting("version").containsExactly("A", "A");

        ArgumentCaptor<SalesOrderQpoImportDefaultsDTO> defaultsCaptor = ArgumentCaptor.forClass(SalesOrderQpoImportDefaultsDTO.class);
        verify(parser).parse(any(InputStream.class), defaultsCaptor.capture());
        assertThat(defaultsCaptor.getValue().getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(defaultsCaptor.getValue().getCustomerOrderNo()).isEqualTo("TARGET-PO");
        assertThat(defaultsCaptor.getValue().getCustomerProjectNo()).isEqualTo("TARGET-CP");
        assertThat(defaultsCaptor.getValue().getCustomerOrderTitle()).isEqualTo("TARGET-TITLE");
        assertThat(defaultsCaptor.getValue().getOrderType()).isEqualTo("备货订单");
        assertThat(defaultsCaptor.getValue().getInternalProjectNo()).isEqualTo("TARGET-INTERNAL");

        verify(headerMapper, never()).insert(any(SalesOrderHeader.class));
        assertThat(insertedCaptor.getAllValues()).hasSize(2);
        SalesOrderItem first = insertedCaptor.getAllValues().get(0);
        SalesOrderItem second = insertedCaptor.getAllValues().get(1);
        assertThat(first.getSalesOrderId()).isEqualTo(301L);
        assertThat(first.getLineNo()).isEqualTo(8);
        assertThat(second.getLineNo()).isEqualTo(9);
        assertThat(first.getCustomerOrderNo()).isEqualTo("TARGET-PO");
        assertThat(first.getCustomerProjectNo()).isEqualTo("TARGET-CP");
        assertThat(first.getCustomerOrderTitle()).isEqualTo("TARGET-TITLE");
        assertThat(first.getOrderType()).isEqualTo("备货订单");
        assertThat(first.getInternalProjectNo()).isEqualTo("TARGET-INTERNAL");
        assertThat(first.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(first.getReleasedQty()).isZero();
        assertThat(first.getRemainingQty()).isEqualTo(4);
        assertThat(first.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(first.getSourceType()).isEqualTo("DIRECT_IMPORT_APPEND");
        assertThat(first.getSourceOrderId()).isEqualTo(301L);
        assertThat(first.getMaterialNo()).isNull();
        assertThat(first.getMaterialNoStatus()).isEqualTo("未确认");
        assertThat(second.getPartNo()).isEqualTo("P-APPEND-1");
        assertThat(second.getVersion()).isEqualTo("A");

        verify(snapshotCompletionService).completeImportedOrderItems(301L, List.of(501L, 502L));
        verify(deliveryService).generateImportedSalesOrderTicketsForItems(301L, List.of(501L, 502L), "SALES_ORDER_APPEND_RELEASE");
        verify(dataScopeService).assertCanAccessSalesOrder(target);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SALES_ORDER_APPEND_RELEASE"),
                eq("sales_order_header"),
                eq(301L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq("追加销售提单"),
                isNull()
        );
        Map<String, Object> summary = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(summary)
                .containsEntry("targetSalesOrderId", 301L)
                .containsEntry("targetSalesOrderNo", "SO-TARGET-001")
                .containsEntry("appendedItemCount", 2)
                .containsEntry("ticketCount", 2)
                .containsEntry("ticketQty", 10);
        assertThat(String.valueOf(summary))
                .doesNotContain("EXCEL-RAW-CONTENT-SHOULD-NOT-BE-LOGGED")
                .doesNotContain("drawingUrl")
                .doesNotContain("Authorization")
                .doesNotContain("token");
    }

    @Test
    void rejectsTargetsThatAlreadyEnteredU9SyncFlow() {
        SalesOrderReleaseAppendService service = service(
                mock(SalesOrderQpoExcelParser.class),
                mock(SalesOrderHeaderMapper.class),
                mock(SalesOrderItemMapper.class),
                mock(DataScopeService.class),
                mock(SalesOrderAutoSnapshotCompletionService.class),
                mock(DeliveryService.class),
                mock(AuditService.class),
                mock(BusinessFileService.class)
        );
        SalesOrderHeaderMapper headerMapper = (SalesOrderHeaderMapper) ReflectionTestUtils.getField(service, "headerMapper");
        when(headerMapper.selectByIdForUpdate(302L)).thenReturn(targetSalesOrder(302L, "已同步"));

        assertThatThrownBy(() -> service.appendItems(
                302L,
                new MockMultipartFile("excelFile", "append.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes(StandardCharsets.UTF_8)),
                List.of(),
                List.of()
        )).hasMessageContaining("不能追加到已同步或同步中的销售订单");
    }

    private SalesOrderReleaseAppendService service(
            SalesOrderQpoExcelParser parser,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            SalesOrderAutoSnapshotCompletionService snapshotCompletionService,
            DeliveryService deliveryService,
            AuditService auditService,
            BusinessFileService businessFileService) {
        SalesOrderReleaseAppendService service = new SalesOrderReleaseAppendService();
        ReflectionTestUtils.setField(service, "excelParser", parser);
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "snapshotCompletionService", snapshotCompletionService);
        ReflectionTestUtils.setField(service, "deliveryService", deliveryService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        return service;
    }

    private SalesOrderHeader targetSalesOrder(Long id, String u9SyncStatus) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SO-TARGET-001");
        header.setDocumentNo("SO-TARGET-001");
        header.setDocumentTypeCode("SO06");
        header.setCustomerId(7L);
        header.setCustomerCode("C001");
        header.setCustomerShortName("ACME");
        header.setCustomerName("ACME Manufacturing");
        header.setSourceType("DIRECT_IMPORT");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 20));
        header.setCustomerOrderNo("TARGET-PO");
        header.setCustomerProjectNo("TARGET-CP");
        header.setCustomerOrderTitle("TARGET-TITLE");
        header.setOrderType("备货订单");
        header.setInternalProjectNo("TARGET-INTERNAL");
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(u9SyncStatus);
        header.setTotalItemCount(1);
        header.setCreatedAt(LocalDateTime.now());
        header.setUpdatedAt(LocalDateTime.now());
        return header;
    }

    private SalesOrderItem existingSalesItem(Long id, int lineNo, int qty, int releasedQty) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(301L);
        item.setLineNo(lineNo);
        item.setPartNo("P-EXISTING");
        item.setVersion("A");
        item.setQty(qty);
        item.setReleasedQty(releasedQty);
        item.setRemainingQty(Math.max(0, qty - releasedQty));
        item.setStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        return item;
    }

    private SalesOrderQpoExcelParseResult appendParseResult() {
        SalesOrderQpoExcelLineDTO first = line(1, "P-APPEND-1", "A", "", 4);
        first.setSalesOrderLineNo(100);
        first.setSalesOrderDate(null);
        first.setCustomerOrderNo(null);
        first.setCustomerProjectNo(null);
        first.setCustomerOrderTitle(null);
        first.setOrderType(null);
        first.setInternalProjectNo(null);
        first.setUntaxedUnitPrice(new BigDecimal("10.00"));
        first.setUntaxedTotalPrice(new BigDecimal("40.00"));
        SalesOrderQpoExcelLineDTO second = line(2, "P-APPEND-1", "A", "", 6);
        second.setSalesOrderLineNo(101);
        second.setSalesOrderDate(null);
        second.setCustomerOrderNo(null);
        second.setCustomerProjectNo(null);
        second.setCustomerOrderTitle(null);
        second.setOrderType(null);
        second.setInternalProjectNo(null);
        second.setUntaxedUnitPrice(new BigDecimal("12.00"));
        second.setUntaxedTotalPrice(new BigDecimal("72.00"));

        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("TARGET-PO|TARGET-INTERNAL");
        group.setSalesOrderDate(LocalDate.of(2026, 8, 20));
        group.setCustomerOrderNo("TARGET-PO");
        group.setCustomerProjectNo("TARGET-CP");
        group.setCustomerOrderTitle("TARGET-TITLE");
        group.setOrderType("备货订单");
        group.setInternalProjectNo("TARGET-INTERNAL");
        group.setLines(List.of(first, second));

        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(2);
        result.setSourceRowCount(2);
        return result;
    }

    private OrderDeliveryTicket ticket(Integer id, Long sourceItemId, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(301L);
        ticket.setSourceItemId(sourceItemId);
        ticket.setQty(qty);
        return ticket;
    }
}
