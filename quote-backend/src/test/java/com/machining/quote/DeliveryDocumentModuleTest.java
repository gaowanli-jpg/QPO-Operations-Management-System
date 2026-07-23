package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.DeliveryDocumentService;
import com.machining.quote.service.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryDocumentModuleTest {
    private static final Path DELIVERY_DOCUMENT_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "DeliveryDocumentController.java");
    private static final Path DELIVERY_DOCUMENT_SERVICE = Path.of(
            "src", "main", "java", "com", "machining", "quote", "service", "DeliveryDocumentService.java");
    private static final Path DELIVERY_DOCUMENT_MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration", "V57__delivery_document_module_permissions.sql");
    private OrderRfqMapper orderRfqMapper;
    private DeliveryService deliveryService;
    private ExcelExportService excelExportService;
    private AuditService auditService;
    private DataScopeService dataScopeService;
    private DeliveryDocumentService service;

    @BeforeEach
    void setUp() {
        orderRfqMapper = mock(OrderRfqMapper.class);
        deliveryService = mock(DeliveryService.class);
        excelExportService = mock(ExcelExportService.class);
        auditService = mock(AuditService.class);
        dataScopeService = mock(DataScopeService.class);
        service = new DeliveryDocumentService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "deliveryService", deliveryService);
        ReflectionTestUtils.setField(service, "excelExportService", excelExportService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
    }

    @Test
    void deliveryDocumentControllerUsesIndependentPermissionBoundary() throws IOException {
        String controller = read(DELIVERY_DOCUMENT_CONTROLLER);

        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/market/delivery-documents\")",
                        "@RequirePermission(\"market:delivery-document:customer-select\")",
                        "@RequirePermission(\"market:delivery-document:order-select\")",
                        "@RequirePermission(\"market:delivery-document:view\")",
                        "@RequirePermission(\"market:delivery-document:export\")",
                        "return deliveryDocumentService.listDocumentCustomers()",
                        "return deliveryDocumentService.listDocumentOrders(customerName, keyword, status, page, pageSize)",
                        "return deliveryDocumentService.documentSummary(rfqId)",
                        "return deliveryDocumentService.pageTickets(rfqId, page, pageSize)",
                        "return deliveryDocumentService.pageProcessSheets(rfqId, page, pageSize)",
                        "deliveryDocumentService.exportTickets(rfqId, response)",
                        "deliveryDocumentService.exportProcessSheets(rfqId, response)"
                )
                .doesNotContain(
                        "@RequirePermission(\"market:release:view\")",
                        "@RequirePermission(\"market:release:order-select\")",
                        "@RequirePermission(\"market:release:customer-select\")"
                );
    }

    @Test
    void deliveryDocumentPermissionMigrationAddsIndependentMenuAndExportGrants() throws IOException {
        String migration = read(DELIVERY_DOCUMENT_MIGRATION);

        assertThat(migration)
                .contains(
                        "market:delivery-document:view",
                        "market:delivery-document:customer-select",
                        "market:delivery-document:order-select",
                        "market:delivery-document:export",
                        "/delivery-documents",
                        "DeliveryDocumentManage",
                        "提单表据",
                        "SUPER_ADMIN",
                        "MARKET_USER",
                        "MARKET_MANAGER",
                        "READONLY"
                )
                .doesNotContain(
                        "market:release:view",
                        "market:release:reset"
                );
        assertThat(migration)
                .as("READONLY can view and select, but must not receive delivery document export")
                .contains(
                        "p.permission_code IN ('market:delivery-document:view', 'market:delivery-document:customer-select', 'market:delivery-document:order-select')",
                        "WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')",
                        "AND r.role_code = 'READONLY'",
                        "AND p.permission_code = 'market:delivery-document:export'"
                );
    }

    @Test
    void deliveryDocumentServiceRejectsNonReleasedOrdersBeforeReadingRows() {
        for (String status : List.of(
                QuoteStatusWorkflow.UNPRICED,
                QuoteStatusWorkflow.PRICED,
                QuoteStatusWorkflow.QUOTED,
                QuoteStatusWorkflow.DISCARDED)) {
            when(orderRfqMapper.selectById(42)).thenReturn(order(42, status));

            assertThatThrownBy(() -> service.documentSummary(42))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("只有部分已提单或已生成提单订单可以查看提单表据");
        }
        verifyNoInteractions(deliveryService, excelExportService, auditService);
    }

    @Test
    void documentOrderListUsesDataScopeAndOnlyAllowedStatuses() {
        when(orderRfqMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        PageResult<OrderRfq> result = service.listDocumentOrders(null, null, null, 1, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<OrderRfq>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(dataScopeService).applyOrderScope(any(QueryWrapper.class));
        verify(orderRfqMapper).selectCount(queryCaptor.capture());
        verify(orderRfqMapper, never()).selectList(any(QueryWrapper.class));
        QueryWrapper<OrderRfq> capturedQuery = queryCaptor.getValue();
        assertThat(capturedQuery.getSqlSegment()).contains("status IN");
        assertThat(capturedQuery.getParamNameValuePairs().values())
                .contains(QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void documentOrderListReturnsEmptyForEveryDisallowedStatusFilter() {
        for (String status : List.of(
                QuoteStatusWorkflow.UNPRICED,
                QuoteStatusWorkflow.PRICED,
                QuoteStatusWorkflow.QUOTED,
                QuoteStatusWorkflow.DISCARDED)) {
            PageResult<OrderRfq> result = service.listDocumentOrders(null, null, status, 2, 50);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(50);
        }
        verifyNoInteractions(orderRfqMapper, dataScopeService);
    }

    @Test
    void documentOrderListReturnsEmptyWhenDataScopeProducesNoRows() {
        doAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0);
            query.apply("1 = 0");
            return null;
        }).when(dataScopeService).applyOrderScope(any(QueryWrapper.class));
        when(orderRfqMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        PageResult<OrderRfq> result = service.listDocumentOrders(null, null, null, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(orderRfqMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void pagedDocumentRowsDelegateRequestedPageArguments() {
        OrderRfq released = order(42, QuoteStatusWorkflow.RELEASED);
        PageResult<com.machining.quote.entity.OrderDeliveryTicket> ticketPage =
                PageResult.of(List.of(), 12, 3, 50);
        PageResult<ProcessSheetDTO> processSheetPage = PageResult.of(List.of(), 9, 4, 25);
        when(orderRfqMapper.selectById(42)).thenReturn(released);
        when(deliveryService.pageTickets(42, 3, 50)).thenReturn(ticketPage);
        when(deliveryService.pageProcessSheets(42, 4, 25)).thenReturn(processSheetPage);

        assertThat(service.pageTickets(42, 3, 50)).isSameAs(ticketPage);
        assertThat(service.pageProcessSheets(42, 4, 25)).isSameAs(processSheetPage);

        verify(deliveryService).pageTickets(42, 3, 50);
        verify(deliveryService).pageProcessSheets(42, 4, 25);
    }

    @Test
    void documentRowsAndExportsStopWhenDataScopeRejectsOrder() throws IOException {
        OrderRfq released = order(42, QuoteStatusWorkflow.RELEASED);
        when(orderRfqMapper.selectById(42)).thenReturn(released);
        doThrow(new RuntimeException("No data permission for order 42"))
                .when(dataScopeService).assertCanAccessOrder(released);

        assertThatThrownBy(() -> service.pageTickets(42, 1, 20))
                .hasMessageContaining("No data permission");
        assertThatThrownBy(() -> service.exportTickets(42, mock(HttpServletResponse.class)))
                .hasMessageContaining("No data permission");

        verifyNoInteractions(deliveryService, excelExportService, auditService);
    }

    @Test
    void processSheetExportRecordsProcessSheetRowCount() throws IOException {
        OrderRfq released = order(42, QuoteStatusWorkflow.RELEASED);
        when(orderRfqMapper.selectById(42)).thenReturn(released);
        when(deliveryService.pageProcessSheets(42, 1, 1))
                .thenReturn(PageResult.<ProcessSheetDTO>of(List.of(), 7, 1, 1));
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.exportProcessSheets(42, response);

        verify(deliveryService).pageProcessSheets(42, 1, 1);
        verify(deliveryService, never()).pageTickets(42, 1, 1);
        verify(excelExportService).exportProcessSheets(42, response);
        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("DELIVERY_PROCESS_SHEET_EXPORT"), eq("order_rfq"), eq(42), isNull(), afterDataCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData).containsEntry("rowCount", 7L);
    }

    @Test
    void deliveryDocumentAuditActionsExposeStableChineseLabels() {
        assertThat(AuditService.inferModule("DELIVERY_TICKET_EXPORT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("DELIVERY_PROCESS_SHEET_EXPORT")).isEqualTo("DELIVERY");
        assertThat(AuditService.labelForAction("DELIVERY_TICKET_EXPORT")).isEqualTo("导出提单表");
        assertThat(AuditService.labelForAction("DELIVERY_PROCESS_SHEET_EXPORT")).isEqualTo("导出工艺表");
    }

    @Test
    void deliveryDocumentServiceKeepsExpectedPublicApiNames() throws IOException {
        String service = read(DELIVERY_DOCUMENT_SERVICE);

        assertThat(service)
                .contains(
                        "listDocumentCustomers",
                        "listDocumentOrders",
                        "documentSummary",
                        "pageTickets",
                        "pageProcessSheets",
                        "exportTickets",
                        "exportProcessSheets",
                        "QuoteStatusWorkflow.PARTIALLY_RELEASED",
                        "QuoteStatusWorkflow.RELEASED",
                        "auditService.record(\"DELIVERY_TICKET_EXPORT\"",
                        "auditService.record(\"DELIVERY_PROCESS_SHEET_EXPORT\""
                )
                .doesNotContain("market:release:view");
    }

    private OrderRfq order(Integer id, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerName("ACME");
        order.setStatus(status);
        return order;
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
