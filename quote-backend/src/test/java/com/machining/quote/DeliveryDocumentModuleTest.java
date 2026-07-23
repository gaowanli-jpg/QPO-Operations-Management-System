package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private DeliveryDocumentService service;

    @BeforeEach
    void setUp() {
        orderRfqMapper = mock(OrderRfqMapper.class);
        deliveryService = mock(DeliveryService.class);
        excelExportService = mock(ExcelExportService.class);
        auditService = mock(AuditService.class);
        service = new DeliveryDocumentService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "deliveryService", deliveryService);
        ReflectionTestUtils.setField(service, "excelExportService", excelExportService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
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
        OrderRfq quoted = order(42, QuoteStatusWorkflow.QUOTED);
        when(orderRfqMapper.selectById(42)).thenReturn(quoted);

        assertThatThrownBy(() -> service.documentSummary(42))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只有部分已提单或已生成提单订单可以查看提单表据");
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
