package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderRepeatEnrichmentService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportStage3Test {
    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void controllerKeepsSalesOrderImportPermissionButUsesIndependentBoundaryService() throws Exception {
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
    void serviceCreatesIndependentSalesOrderHeadersItemsRecordsAndAudit() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderRepeatEnrichmentService repeatEnrichmentService = mock(SalesOrderRepeatEnrichmentService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService,
                repeatEnrichmentService
        );
        AuthContext.set(new AuthContext.SessionUser(5, "market01", "Market One", "MARKET_USER"));
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(header.getCustomerOrderNo().equals("PO-001") ? 301L : 302L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(record.getCustomerOrderNo().equals("PO-001") ? 401L : 402L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId(item.getPartNo().equals("P-001") ? 501L : 502L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        when(repeatEnrichmentService.enrich(anyList()))
                .thenReturn(enrichmentResult(1, 1, "\u672a\u5339\u914d\u5386\u53f2\u751f\u4ea7\u5feb\u7167\u7684\u9500\u552e\u8ba2\u5355\u96f6\u4ef6\u6570\uff1a1"))
                .thenReturn(enrichmentResult(0, 1, "\u672a\u5339\u914d\u5386\u53f2\u751f\u4ea7\u5feb\u7167\u7684\u9500\u552e\u8ba2\u5355\u96f6\u4ef6\u6570\uff1a1"));

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
        assertThat(resp.getTotalOrderCount()).isEqualTo(2);
        assertThat(resp.getSuccessOrderCount()).isEqualTo(2);
        assertThat(resp.getFailedOrderCount()).isZero();
        assertThat(resp.getTotalLineCount()).isEqualTo(3);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0).getSalesOrderId()).isEqualTo(301L);
        assertThat(resp.getRows().get(0).getSalesOrderNo()).startsWith("SOQPO");
        assertThat(resp.getRows().get(0).getLineCount()).isEqualTo(2);
        assertThat(resp.getRows().get(0).getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getUnmatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getWarningMessage()).contains("1");

        ArgumentCaptor<SalesOrderImportBatch> batchCaptor = ArgumentCaptor.forClass(SalesOrderImportBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getImportMode()).isEqualTo("QPO_INDEPENDENT");
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchCaptor.getValue().getCreatedBy()).isEqualTo(5L);

        ArgumentCaptor<SalesOrderHeader> headerCaptor = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper, org.mockito.Mockito.times(2)).insert(headerCaptor.capture());
        SalesOrderHeader firstHeader = headerCaptor.getAllValues().get(0);
        assertThat(firstHeader.getSalesOrderNo()).startsWith("SOQPO");
        assertThat(firstHeader.getDocumentTypeCode()).isEqualTo("SO01");
        assertThat(firstHeader.getCustomerId()).isEqualTo(7L);
        assertThat(firstHeader.getCustomerCode()).isEqualTo("C001");
        assertThat(firstHeader.getCustomerShortName()).isEqualTo("ACME");
        assertThat(firstHeader.getCustomerName()).isEqualTo("ACME Manufacturing");
        assertThat(firstHeader.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(firstHeader.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(firstHeader.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(firstHeader.getStatus()).isEqualTo("已报价");
        assertThat(firstHeader.getImportBatchId()).isEqualTo(201L);
        assertThat(firstHeader.getTotalItemCount()).isEqualTo(2);
        assertThat(firstHeader.getUnmatchedItemCount()).isEqualTo(2);
        assertThat(firstHeader.getCreatedBy()).isEqualTo(5L);
        assertThat(firstHeader.getOwnerUserId()).isEqualTo(5);

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper, org.mockito.Mockito.times(3)).insert(itemCaptor.capture());
        SalesOrderItem firstItem = itemCaptor.getAllValues().get(0);
        assertThat(firstItem.getSalesOrderId()).isEqualTo(301L);
        assertThat(firstItem.getLineNo()).isEqualTo(1);
        assertThat(firstItem.getPartNo()).isEqualTo("P-001");
        assertThat(firstItem.getVersion()).isEqualTo("A");
        assertThat(firstItem.getQty()).isEqualTo(10);
        assertThat(firstItem.getSalesMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getTaxIncludedUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
        assertThat(firstItem.getUntaxedUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(firstItem.getTaxIncludedTotalPrice()).isEqualByComparingTo(new BigDecimal("113.00"));
        assertThat(firstItem.getUntaxedTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(firstItem.getFinalUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
        assertThat(firstItem.getFinalTotalPrice()).isEqualByComparingTo(new BigDecimal("113.00"));
        assertThat(firstItem.getRemainingQty()).isEqualTo(10);
        assertThat(firstItem.getStatus()).isEqualTo("已报价");
        SalesOrderItem secondItem = itemCaptor.getAllValues().get(1);
        assertThat(secondItem.getSalesMaterialNo()).isEmpty();
        assertThat(secondItem.getMaterialNo()).isNull();

        ArgumentCaptor<SalesOrderImportRecord> recordCaptor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
        SalesOrderImportRecord firstRecord = recordCaptor.getAllValues().get(0);
        assertThat(firstRecord.getBatchId()).isEqualTo(201L);
        assertThat(firstRecord.getSalesOrderId()).isEqualTo(301L);
        assertThat(firstRecord.getSalesOrderNo()).isEqualTo(firstHeader.getSalesOrderNo());
        assertThat(firstRecord.getItemCount()).isEqualTo(2);
        assertThat(firstRecord.getRfqId()).isNull();
        assertThat(firstRecord.getRfqNo()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SalesOrderItem>> enrichmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(repeatEnrichmentService, org.mockito.Mockito.times(2)).enrich(enrichmentCaptor.capture());
        assertThat(enrichmentCaptor.getAllValues().get(0))
                .extracting(SalesOrderItem::getId)
                .containsExactly(501L, 502L);
        assertThat(enrichmentCaptor.getAllValues().get(0))
                .extracting(SalesOrderItem::getPartNo)
                .containsExactly("P-001", "P-002");

        ArgumentCaptor<SalesOrderHeader> headerUpdateCaptor = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper, atLeast(2)).updateById(headerUpdateCaptor.capture());
        assertThat(headerUpdateCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(301L);
                    assertThat(update.getMatchedItemCount()).isEqualTo(1);
                    assertThat(update.getUnmatchedItemCount()).isEqualTo(1);
                    assertThat(update.getWarningMessage()).contains("1");
                });
        ArgumentCaptor<SalesOrderImportRecord> recordUpdateCaptor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper, atLeast(1)).updateById(recordUpdateCaptor.capture());
        assertThat(recordUpdateCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(401L);
                    assertThat(update.getMatchedItemCount()).isEqualTo(1);
                    assertThat(update.getUnmatchedItemCount()).isEqualTo(1);
                    assertThat(update.getWarningMessage()).contains("1");
                });
        verify(dataScopeService).assertCanAccessCustomer(customer());
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                any(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq("导入销售订单到独立主表"),
                isNull()
        );
    }

    @Test
    void serviceAcceptsSalesOrderDrawingsAndBindsMatchedPdfsToIndependentItems() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                auditService
        );
        Method importWithDrawings = SalesOrderQpoImportService.class.getMethod(
                "importQpo",
                MultipartFile.class,
                Long.class,
                String.class,
                List.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class, invocation -> {
            if ("saveSalesOrderImportDrawing".equals(invocation.getMethod().getName())) {
                MultipartFile drawing = invocation.getArgument(2);
                String fileName = drawing.getOriginalFilename() == null
                        ? "drawing.pdf"
                        : drawing.getOriginalFilename().replaceAll(".*[\\\\/]", "");
                return new BusinessFileService.StoredBusinessFile(
                        "/uploads/ACME/SOQPO/" + fileName,
                        StorageType.LOCAL,
                        1L,
                        "ACME/SOQPO/" + fileName,
                        fileName,
                        "application/pdf",
                        drawing.getSize(),
                        null);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(header.getCustomerOrderNo().equals("PO-001") ? 301L : 302L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(record.getCustomerOrderNo().equals("PO-001") ? 401L : 402L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId(item.getPartNo().equals("P-001") ? 501L : 502L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));

        SalesOrderQpoImportResp resp = (SalesOrderQpoImportResp) importWithDrawings.invoke(
                service,
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01",
                List.of(
                        new MockMultipartFile("drawings", "P-001.pdf", "application/pdf", "p1".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("drawings", "Z-999.pdf", "application/pdf", "z9".getBytes(StandardCharsets.UTF_8))
                )
        );

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper, org.mockito.Mockito.times(3)).insert(itemCaptor.capture());
        SalesOrderItem firstItem = itemCaptor.getAllValues().get(0);
        SalesOrderItem secondItem = itemCaptor.getAllValues().get(1);
        assertThat(firstItem.getPartNo()).isEqualTo("P-001");
        assertThat(firstItem.getDrawingUrl()).isEqualTo("/uploads/ACME/SOQPO/P-001.pdf");
        assertThat(firstItem.getHasDrawing()).isTrue();
        assertThat(secondItem.getPartNo()).isEqualTo("P-002");
        assertThat(secondItem.getDrawingUrl()).isNull();
        assertThat(secondItem.getHasDrawing()).isFalse();
        assertThat(resp.getDrawingReport())
                .containsEntry("uploadedDrawingCount", 2)
                .containsEntry("matchedItemCount", 1)
                .containsEntry("missingDrawingCount", 2)
                .containsEntry("unmatchedDrawingCount", 1);
        assertThat(resp.getRows().get(0).getMatchedDrawingCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getMissingDrawingCount()).isEqualTo(1);
    }

    @Test
    void serviceRejectsDuplicateCustomerOrderAndInternalProjectBeforeWriting() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                mock(SalesOrderImportRecordMapper.class),
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                mock(AuditService.class)
        );
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(headerMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复")
                .hasMessageContaining("PO-001")
                .hasMessageContaining("IP-001");
        verify(batchMapper, never()).insert(any(SalesOrderImportBatch.class));
        verify(itemMapper, never()).insert(any(SalesOrderItem.class));
    }

    @Test
    void serviceRejectsCustomerWithoutDataPermission() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        SalesOrderQpoImportService service = service(
                mock(SalesOrderQpoExcelParser.class),
                customerMapper,
                mock(SalesOrderImportBatchMapper.class),
                mock(SalesOrderImportRecordMapper.class),
                mock(SalesOrderHeaderMapper.class),
                mock(SalesOrderItemMapper.class),
                dataScopeService,
                mock(AuditService.class)
        );
        CustomerMaster customer = customer();
        when(customerMapper.selectById(7L)).thenReturn(customer);
        org.mockito.Mockito.doThrow(new RuntimeException("No data permission for customer ACME Manufacturing"))
                .when(dataScopeService).assertCanAccessCustomer(customer);

        assertThatThrownBy(() -> service.importQpo(
                new MockMultipartFile("excelFile", "sales.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8)),
                7L,
                "SO01"
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
    }

    @Test
    void serviceSourceDoesNotCreateRfqOrdersOrItemQuoteVersions() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("OrderRfqMapper")
                .doesNotContain("ItemQuoteVersionMapper")
                .doesNotContain("RfqNumberService")
                .doesNotContain("new OrderRfq")
                .doesNotContain("new ItemQuoteVersion")
                .doesNotContain("orderMapper.insert")
                .doesNotContain("rfqNumberService.nextRfqNo")
                .doesNotContain("SOURCE_SALES_ORDER");
    }

    @Test
    void serviceRejectsMissingCustomerBeforeIndependentImportWork() {
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderQpoImportService service = service(
                mock(SalesOrderQpoExcelParser.class),
                customerMapper,
                mock(SalesOrderImportBatchMapper.class),
                mock(SalesOrderImportRecordMapper.class),
                mock(SalesOrderHeaderMapper.class),
                mock(SalesOrderItemMapper.class),
                mock(DataScopeService.class),
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
    }

    private SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService) {
        return service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService,
                null
        );
    }

    private SalesOrderQpoImportService service(
            SalesOrderQpoExcelParser parser,
            CustomerMasterMapper customerMapper,
            SalesOrderImportBatchMapper batchMapper,
            SalesOrderImportRecordMapper recordMapper,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService,
            SalesOrderRepeatEnrichmentService repeatEnrichmentService) {
        SalesOrderQpoImportService service = new SalesOrderQpoImportService();
        ReflectionTestUtils.setField(service, "excelParser", parser);
        ReflectionTestUtils.setField(service, "customerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "headerMapper", headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        if (repeatEnrichmentService != null) {
            ReflectionTestUtils.setField(service, "repeatEnrichmentService", repeatEnrichmentService);
        }
        return service;
    }

    private com.machining.quote.dto.SalesOrderRepeatEnrichmentResult enrichmentResult(
            int matchedCount,
            int unmatchedCount,
            String warningMessage) {
        com.machining.quote.dto.SalesOrderRepeatEnrichmentResult result =
                new com.machining.quote.dto.SalesOrderRepeatEnrichmentResult();
        result.setMatchedItemCount(matchedCount);
        result.setUnmatchedItemCount(unmatchedCount);
        result.setWarningMessage(warningMessage);
        return result;
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
        SalesOrderQpoOrderGroupDTO first = new SalesOrderQpoOrderGroupDTO();
        first.setGroupKey("PO-001|IP-001");
        first.setCustomerOrderNo("PO-001");
        first.setInternalProjectNo("IP-001");
        first.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        first.setCustomerProjectNo("CP-001");
        first.setCustomerOrderTitle("GSQ1");
        first.setOrderType("样品订单");
        first.setLines(List.of(line(1, "P-001", "A", "MAT-001", 10), line(2, "P-002", "B", "", 4)));

        SalesOrderQpoOrderGroupDTO second = new SalesOrderQpoOrderGroupDTO();
        second.setGroupKey("PO-002|IP-002");
        second.setCustomerOrderNo("PO-002");
        second.setInternalProjectNo("IP-002");
        second.setSalesOrderDate(LocalDate.of(2026, 7, 2));
        second.setCustomerProjectNo("CP-002");
        second.setCustomerOrderTitle("GSQ2");
        second.setOrderType("批量订单");
        second.setLines(List.of(line(3, "P-003", "C", "MAT-003", 5)));

        result.setOrders(List.of(first, second));
        result.setOrderCount(2);
        result.setLineCount(3);
        result.setSourceRowCount(3);
        return result;
    }

    private SalesOrderQpoExcelLineDTO line(int lineNo, String partNo, String version, String salesMaterialNo, int qty) {
        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(lineNo + 1);
        line.setSalesOrderLineNo(lineNo);
        line.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        line.setCustomerOrderNo(lineNo == 3 ? "PO-002" : "PO-001");
        line.setCustomerProjectNo(lineNo == 3 ? "CP-002" : "CP-001");
        line.setCustomerOrderTitle(lineNo == 3 ? "GSQ2" : "GSQ1");
        line.setOrderType(lineNo == 3 ? "批量订单" : "样品订单");
        line.setSalesMaterialNo(salesMaterialNo);
        line.setInternalProjectNo(lineNo == 3 ? "IP-002" : "IP-001");
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
        line.setActualShipDate(LocalDate.of(2026, 8, 9));
        line.setRemark("remark");
        return line;
    }
}
