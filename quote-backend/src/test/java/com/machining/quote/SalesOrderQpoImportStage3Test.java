package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage3Test {
    private static final String QUOTED = "\u5df2\u62a5\u4ef7";

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void controllerExposesImportQpoEndpointWithSalesOrderPermission() throws Exception {
        assertThat(SalesOrderQpoImportController.class).isNotNull();
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/controller/SalesOrderQpoImportController.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/market/sales-orders\")")
                .contains("@PostMapping(\"/import-qpo\")")
                .contains("@RequirePermission(\"market:rfq:import-sales-order\")")
                .contains("importQpo(")
                .doesNotContain("SALES_ORDER_IMPORT_U9");
    }

    @Test
    void serviceCreatesQuotedQpoOrdersItemsBatchRecordsAndAudit() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        RfqNumberService rfqNumberService = mock(RfqNumberService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                orderMapper,
                itemMapper,
                batchMapper,
                recordMapper,
                rfqNumberService,
                auditService
        );
        AuthContext.set(new AuthContext.SessionUser(5, "market01", "Market One", "MARKET_USER"));
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(rfqNumberService.nextRfqNo()).thenReturn("RFQ-SO-0001");
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            OrderRfq order = invocation.getArgument(0);
            order.setId(101);
            return 1;
        }).when(orderMapper).insert(any(OrderRfq.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(301L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                ""
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        assertThat(resp.getTotalOrderCount()).isEqualTo(1);
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isZero();
        assertThat(resp.getTotalLineCount()).isEqualTo(2);
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0).getRfqNo()).isEqualTo("RFQ-SO-0001");

        ArgumentCaptor<SalesOrderImportBatch> batchCaptor = ArgumentCaptor.forClass(SalesOrderImportBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getImportMode()).isEqualTo("QPO");
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchCaptor.getValue().getCreatedBy()).isEqualTo(5L);

        ArgumentCaptor<OrderRfq> orderCaptor = ArgumentCaptor.forClass(OrderRfq.class);
        verify(orderMapper).insert(orderCaptor.capture());
        OrderRfq order = orderCaptor.getValue();
        assertThat(order.getRfqNo()).isEqualTo("RFQ-SO-0001");
        assertThat(order.getStatus()).isEqualTo(QUOTED);
        assertThat(order.getSourceType()).isEqualTo("SALES_ORDER");
        assertThat(order.getCustomerCode()).isEqualTo("C001");
        assertThat(order.getCustomerShortName()).isEqualTo("ACME");
        assertThat(order.getCustomerName()).isEqualTo("ACME Manufacturing");
        assertThat(order.getSalesDocumentTypeCode()).isEqualTo("SO01");
        assertThat(order.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(order.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(order.getSalesOrderImportBatchId()).isEqualTo(201L);
        assertThat(order.getCreatedBy()).isEqualTo(5);
        assertThat(order.getOwnerUserId()).isEqualTo(5);

        ArgumentCaptor<SalesOrderImportRecord> recordCaptor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        SalesOrderImportRecord record = recordCaptor.getValue();
        assertThat(record.getBatchId()).isEqualTo(201L);
        assertThat(record.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(record.getOrderBy()).isEqualTo("C001");
        assertThat(record.getDocumentType()).isEqualTo("SO01");
        assertThat(record.getLineCount()).isEqualTo(2);
        assertThat(record.getRfqId()).isEqualTo(101);
        assertThat(record.getRfqNo()).isEqualTo("RFQ-SO-0001");
        assertThat(record.getQpoStatus()).isEqualTo("SUCCESS");
        assertThat(record.getStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        ItemQuoteVersion firstItem = itemCaptor.getAllValues().get(0);
        assertThat(firstItem.getRfqId()).isEqualTo(101);
        assertThat(firstItem.getStatus()).isEqualTo(QUOTED);
        assertThat(firstItem.getPartNo()).isEqualTo("P-001");
        assertThat(firstItem.getVersion()).isEqualTo("A");
        assertThat(firstItem.getQty()).isEqualTo(10);
        assertThat(firstItem.getOrderType()).isEqualTo("\u6837\u54c1\u8ba2\u5355");
        assertThat(firstItem.getSalesMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(firstItem.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(firstItem.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(firstItem.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(firstItem.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(firstItem.getInternalTargetLeadTime()).isEqualTo("2026-08-05");
        assertThat(firstItem.getTaxIncludedUnitPrice()).isCloseTo(11.30, within(0.000001));
        assertThat(firstItem.getUntaxedUnitPrice()).isCloseTo(10.00, within(0.000001));
        assertThat(firstItem.getTaxIncludedTotalPrice()).isCloseTo(113.00, within(0.000001));
        assertThat(firstItem.getUntaxedTotalPrice()).isCloseTo(100.00, within(0.000001));
        assertThat(firstItem.getFinalUnitPrice()).isCloseTo(11.30, within(0.000001));
        assertThat(firstItem.getFinalTotalPrice()).isCloseTo(113.00, within(0.000001));
        assertThat(firstItem.getSalesOrderImportRecordId()).isEqualTo(301L);

        verify(orderMapper).updateById(org.mockito.ArgumentMatchers.argThat(updated ->
                updated.getId().equals(101) && updated.getSalesOrderImportRecordId().equals(301L)));
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                any(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_IMPORT_QPO")),
                isNull()
        );
    }

    @Test
    void serviceRejectsMissingCustomerBeforeCreatingRows() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        SalesOrderQpoImportService service = service(
                mock(SalesOrderQpoExcelParser.class),
                customerMapper,
                orderMapper,
                mock(ItemQuoteVersionMapper.class),
                mock(SalesOrderImportBatchMapper.class),
                mock(SalesOrderImportRecordMapper.class),
                mock(RfqNumberService.class),
                mock(AuditService.class)
        );
        when(customerMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile("excelFile", "sales.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                404L,
                "SO01"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("\u5ba2\u6237");
        verify(orderMapper, never()).insert(any(OrderRfq.class));
    }

    private SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            RfqNumberService rfqNumberService,
            AuditService auditService) {
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "excelParser", parser);
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "rfqNumberService", rfqNumberService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private CustomerMaster customer() {
        CustomerMaster customer = new CustomerMaster();
        customer.setId(7L);
        customer.setCustomerCode("C001");
        customer.setCustomerShortName("ACME");
        customer.setCustomerName("ACME Manufacturing");
        customer.setEnabled(true);
        return customer;
    }

    private SalesOrderQpoExcelParseResult parseResult() {
        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setCustomerOrderNo("PO-001");
        group.setInternalProjectNo("IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("\u6837\u54c1\u8ba2\u5355");
        group.setLines(List.of(line("P-001", "A", "MAT-001", 10), line("P-002", "B", "MAT-002", 4)));
        result.setOrders(List.of(group));
        result.setSourceRowCount(2);
        result.setOrderCount(1);
        result.setLineCount(2);
        return result;
    }

    private SalesOrderQpoExcelLineDTO line(String partNo, String version, String salesMaterialNo, int qty) {
        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(2);
        line.setSalesOrderLineNo(1);
        line.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        line.setCustomerOrderNo("PO-001");
        line.setCustomerProjectNo("CP-001");
        line.setCustomerOrderTitle("GSQ1");
        line.setOrderType("\u6837\u54c1\u8ba2\u5355");
        line.setSalesMaterialNo(salesMaterialNo);
        line.setInternalProjectNo("IP-001");
        line.setCustomerDeliveryDate(LocalDate.of(2026, 8, 1));
        line.setInternalDeliveryDate(LocalDate.of(2026, 8, 5));
        line.setPartNo(partNo);
        line.setQty(qty);
        line.setShippedQty(0);
        line.setUnshippedQty(qty);
        line.setDescription("desc-" + partNo);
        line.setMaterialName("6061");
        line.setSurfaceTreatment("/");
        line.setVersion(version);
        line.setTaxIncludedUnitPrice(new BigDecimal("11.30"));
        line.setUntaxedUnitPrice(new BigDecimal("10.00"));
        line.setTaxIncludedTotalPrice(new BigDecimal("113.00"));
        line.setUntaxedTotalPrice(new BigDecimal("100.00"));
        line.setRemark("remark");
        return line;
    }
}
