package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderManageService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SalesOrderManagementStage4Test {
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderManageController.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "SalesOrderManageService.java");

    @Test
    void controllerExposesSecuredGovernanceAndDrawingEndpointsWithoutDelete() throws IOException {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@PostMapping(\"/orders/{salesOrderId}/discard\")",
                        "@RequirePermission(\"market:sales-order:discard\")",
                        "@PostMapping(\"/orders/{salesOrderId}/restore\")",
                        "@RequirePermission(\"market:sales-order:restore\")",
                        "@PostMapping(value = \"/items/{itemId}/drawings\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)",
                        "@RequirePermission(\"market:sales-order:supplement-drawing\")"
                )
                .doesNotContain("@DeleteMapping")
                .doesNotContain("market:order:delete")
                .doesNotContain("market:history-order:");
    }

    @Test
    void serviceDefinesSalesOrderOnlyStatusAndDrawingWrites() throws IOException {
        String source = read(SERVICE);

        assertThat(source)
                .contains(
                        "selectByIdForUpdate",
                        "SALES_ORDER_DISCARD",
                        "SALES_ORDER_RESTORE",
                        "SALES_ORDER_DRAWING_SUPPLEMENT",
                        "Sales order status change started",
                        "Sales order status change completed",
                        "Sales order drawing supplement completed",
                        "saveSalesOrderImportDrawing",
                        "sales_order_header",
                        "sales_order_item"
                )
                .doesNotContain(
                        "ItemQuoteVersionMapper",
                        "OrderRfqMapper",
                        "\"item_quote_version\"",
                        "\"order_rfq\""
                );
    }

    @Test
    void quotedSalesOrderCanBeDiscardedAndDiscardedSalesOrderCanBeRestored() throws Exception {
        SalesOrderItem quotedItem = item(501L, 100L, QuoteStatusWorkflow.QUOTED);
        Fixture discardFixture = fixture(header(100L, QuoteStatusWorkflow.QUOTED), List.of(quotedItem), 0L);

        Object discardResult = invokeStatusAction(discardFixture.service, "discard", 100L, "客户取消");

        assertThat(String.valueOf(discardResult)).contains(QuoteStatusWorkflow.DISCARDED);
        verify(discardFixture.dataScopeService).assertCanAccessSalesOrder(discardFixture.header);
        ArgumentCaptor<SalesOrderHeader> discardedHeader = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(discardFixture.headerMapper).updateById(discardedHeader.capture());
        assertThat(discardedHeader.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        ArgumentCaptor<SalesOrderItem> discardedItem = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(discardFixture.itemMapper).updateById(discardedItem.capture());
        assertThat(discardedItem.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.DISCARDED);
        verify(discardFixture.auditService).record(eq("SALES_ORDER_DISCARD"), eq("sales_order_header"), eq(100L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());

        SalesOrderItem discardedLine = item(601L, 200L, QuoteStatusWorkflow.DISCARDED);
        Fixture restoreFixture = fixture(header(200L, QuoteStatusWorkflow.DISCARDED), List.of(discardedLine), 0L);

        Object restoreResult = invokeStatusAction(restoreFixture.service, "restore", 200L, "恢复订单");

        assertThat(String.valueOf(restoreResult)).contains(QuoteStatusWorkflow.QUOTED);
        ArgumentCaptor<SalesOrderHeader> restoredHeader = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(restoreFixture.headerMapper).updateById(restoredHeader.capture());
        assertThat(restoredHeader.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        ArgumentCaptor<SalesOrderItem> restoredItem = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(restoreFixture.itemMapper).updateById(restoredItem.capture());
        assertThat(restoredItem.getValue().getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);
        verify(restoreFixture.auditService).record(eq("SALES_ORDER_RESTORE"), eq("sales_order_header"), eq(200L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    @Test
    void releasedOrTicketedSalesOrdersCannotBeDiscarded() {
        Fixture partiallyReleased = fixture(header(300L, QuoteStatusWorkflow.PARTIALLY_RELEASED), List.of(), 0L);
        assertThatThrownBy(() -> invokeStatusAction(partiallyReleased.service, "discard", 300L, "不能废弃"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已报价且未生成提单的销售订单可以废弃");
        verify(partiallyReleased.headerMapper, never()).updateById(any());
        verify(partiallyReleased.itemMapper, never()).updateById(any());

        Fixture released = fixture(header(301L, QuoteStatusWorkflow.RELEASED), List.of(), 0L);
        assertThatThrownBy(() -> invokeStatusAction(released.service, "discard", 301L, "不能废弃"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已报价且未生成提单的销售订单可以废弃");
        verify(released.headerMapper, never()).updateById(any());
        verify(released.itemMapper, never()).updateById(any());

        Fixture ticketed = fixture(header(302L, QuoteStatusWorkflow.QUOTED), List.of(), 1L);
        assertThatThrownBy(() -> invokeStatusAction(ticketed.service, "discard", 302L, "已有提单"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有已报价且未生成提单的销售订单可以废弃");
        verify(ticketed.headerMapper, never()).updateById(any());
        verify(ticketed.itemMapper, never()).updateById(any());
    }

    @Test
    void supplementDrawingBindsToSalesOrderItem() throws Exception {
        SalesOrderHeader header = header(400L, QuoteStatusWorkflow.QUOTED);
        SalesOrderItem item = item(700L, 400L, QuoteStatusWorkflow.QUOTED);
        Fixture fixture = fixture(header, List.of(item), 0L);
        when(fixture.itemMapper.selectByIdForUpdate(700L)).thenReturn(item);
        when(fixture.headerMapper.selectById(400L)).thenReturn(header);
        when(fixture.businessFileService.saveSalesOrderImportDrawing(eq("客户A"), eq("SO-400"), any(), eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("700")))
                .thenReturn(new BusinessFileService.StoredBusinessFile(
                        "/uploads/客户A/SO-400/P-700.pdf",
                        StorageType.LOCAL,
                        1L,
                        "客户A/SO-400/P-700.pdf",
                        "P-700.pdf",
                        "application/pdf",
                        5L,
                        null
                ));
        MockMultipartFile drawing = new MockMultipartFile("drawings", "P-700.pdf", "application/pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        Object result = invokeSupplement(fixture.service, 700L, List.of(drawing));

        assertThat(String.valueOf(result)).contains("成功绑定 1 份");
        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getDrawingUrl()).isEqualTo("/uploads/客户A/SO-400/P-700.pdf");
        assertThat(itemCaptor.getValue().getHasDrawing()).isTrue();
        verify(fixture.auditService).record(eq("SALES_ORDER_DRAWING_SUPPLEMENT"), eq("sales_order_item"), eq(700L), any(), any(), eq("MARKET"), eq("SUCCESS"), any(), isNull());
    }

    private Object invokeStatusAction(SalesOrderManageService service, String action, Long salesOrderId, String reason) throws Exception {
        Method method = SalesOrderManageService.class.getMethod(action, Long.class, OrderStatusChangeReq.class);
        OrderStatusChangeReq req = new OrderStatusChangeReq();
        req.setReason(reason);
        try {
            return method.invoke(service, salesOrderId, req);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Object invokeSupplement(SalesOrderManageService service, Long itemId, List<MultipartFile> drawings) throws Exception {
        Method method = SalesOrderManageService.class.getMethod("supplementItemDrawings", Long.class, List.class);
        try {
            return method.invoke(service, itemId, drawings);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Fixture fixture(SalesOrderHeader header, List<SalesOrderItem> items, Long ticketCount) {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
        when(headerMapper.selectById(header.getId())).thenReturn(header);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
        when(ticketMapper.selectCount(any(QueryWrapper.class))).thenReturn(ticketCount);
        SalesOrderManageService service = new SalesOrderManageService();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        return new Fixture(service, headerMapper, itemMapper, ticketMapper, dataScopeService, auditService, businessFileService, header);
    }

    private SalesOrderHeader header(Long id, String status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo("SO-" + id);
        header.setCustomerId(88L);
        header.setCustomerName("客户A");
        header.setStatus(status);
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setQty(10);
        item.setReleasedQty(0);
        item.setStatus(status);
        return item;
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("%s should exist", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private record Fixture(
            SalesOrderManageService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            BusinessFileService businessFileService,
            SalesOrderHeader header) {
    }
}
