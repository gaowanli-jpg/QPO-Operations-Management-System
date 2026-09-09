package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderAutoSnapshotCompletionService;
import com.machining.quote.service.SalesOrderNumberService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportServiceWorkflowTest extends SalesOrderQpoImportTestSupport {

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
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService
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
        assertThat(resp.getRows().get(0).getMatchedItemCount()).isZero();
        assertThat(resp.getRows().get(0).getUnmatchedItemCount()).isEqualTo(2);
        assertThat(ReflectionTestUtils.getField(resp.getRows().get(0), "missingPriceCount")).isEqualTo(0);
        assertThat(resp.getRows().get(0).getWarningMessage())
                .contains("\u672a\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167");
        assertThat(resp.getRows().get(1).getMatchedItemCount()).isZero();
        assertThat(resp.getRows().get(1).getUnmatchedItemCount()).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(resp.getRows().get(1), "missingPriceCount")).isEqualTo(0);
        assertThat(resp.getRows().get(1).getWarningMessage())
                .contains("\u672a\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167");
        assertThat(ReflectionTestUtils.getField(resp, "missingPriceCount")).isEqualTo(0);

        ArgumentCaptor<SalesOrderImportBatch> batchCaptor = ArgumentCaptor.forClass(SalesOrderImportBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getImportMode()).isEqualTo("QPO_INDEPENDENT");
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchCaptor.getValue().getCreatedBy()).isEqualTo(5L);

        ArgumentCaptor<SalesOrderHeader> headerCaptor = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper, org.mockito.Mockito.times(2)).insert(headerCaptor.capture());
        SalesOrderHeader firstHeader = headerCaptor.getAllValues().get(0);
        assertThat(firstHeader.getSalesOrderNo()).startsWith("SOQPO");
        assertThat(firstHeader.getDocumentNo()).isEqualTo(firstHeader.getSalesOrderNo());
        assertThat(firstHeader.getDocumentTypeCode()).isEqualTo("SO06");
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
        assertThat(firstHeader.getMatchedItemCount()).isZero();
        assertThat(firstHeader.getUnmatchedItemCount()).isEqualTo(2);
        assertThat(firstHeader.getWarningMessage())
                .contains("\u672a\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167");
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
        assertThat(firstItem.getSalesMaterialNo()).isNull();
        assertThat(firstItem.getMaterialNo()).isNull();
        assertThat(firstItem.getMaterialNoStatus()).isEqualTo("未确认");
        assertThat(firstItem.getMaterialNoSource()).isNull();
        assertThat(firstItem.getMaterialNoSourceTicketId()).isNull();
        assertThat(firstItem.getMaterialNoSyncedAt()).isNull();
        assertThat(firstItem.getTaxIncludedUnitPrice()).isEqualByComparingTo(new BigDecimal("11.30"));
        assertThat(firstItem.getUntaxedUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(firstItem.getTaxIncludedTotalPrice()).isEqualByComparingTo(new BigDecimal("113.00"));
        assertThat(firstItem.getUntaxedTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(firstItem.getRemainingQty()).isEqualTo(10);
        assertThat(firstItem.getStatus()).isEqualTo("已报价");
        SalesOrderItem secondItem = itemCaptor.getAllValues().get(1);
        assertThat(secondItem.getSalesMaterialNo()).isNull();
        assertThat(secondItem.getMaterialNo()).isNull();
        assertThat(secondItem.getMaterialNoStatus()).isEqualTo("未确认");

        ArgumentCaptor<SalesOrderImportRecord> recordCaptor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper, org.mockito.Mockito.times(2)).insert(recordCaptor.capture());
        SalesOrderImportRecord firstRecord = recordCaptor.getAllValues().get(0);
        assertThat(firstRecord.getBatchId()).isEqualTo(201L);
        assertThat(firstRecord.getSalesOrderId()).isEqualTo(301L);
        assertThat(firstRecord.getSalesOrderNo()).isEqualTo(firstHeader.getSalesOrderNo());
        assertThat(firstRecord.getItemCount()).isEqualTo(2);
        assertThat(firstRecord.getMatchedItemCount()).isZero();
        assertThat(firstRecord.getUnmatchedItemCount()).isEqualTo(2);
        assertThat(firstRecord.getWarningMessage())
                .contains("\u672a\u8865\u9f50\u5386\u53f2\u751f\u4ea7\u5feb\u7167");
        assertThat(firstRecord.getRfqId()).isNull();
        assertThat(firstRecord.getRfqNo()).isNull();

        ArgumentCaptor<SalesOrderHeader> headerUpdateCaptor = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper, org.mockito.Mockito.times(2)).updateById(headerUpdateCaptor.capture());
        assertThat(headerUpdateCaptor.getAllValues())
                .extracting(SalesOrderHeader::getImportRecordId)
                .containsExactly(401L, 402L);
        verify(recordMapper, never()).updateById(any(SalesOrderImportRecord.class));
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
    void serviceStoresSystemSequentialLineNumbersInsteadOfCustomerExcelSequence() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                mock(AuditService.class)
        );
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithCustomerExcelSequence());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(2201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(2301L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(2401L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));

        service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO06"
        );

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper, org.mockito.Mockito.times(3)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(SalesOrderItem::getLineNo)
                .containsExactly(1, 2, 3);
        assertThat(itemCaptor.getAllValues())
                .extracting(SalesOrderItem::getPartNo)
                .containsExactly("P-SEQ-1", "P-SEQ-2", "P-SEQ-3");
    }

    @Test
    void serviceAllowsBlankPricesAndReportsMissingPriceCount() throws Exception {
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
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithBlankPrices());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(301L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(401L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "FULL-EXCEL-CONTENT-SHOULD-NOT-BE-LOGGED".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getTotalLineCount()).isEqualTo(2);
        assertThat(ReflectionTestUtils.getField(resp, "missingPriceCount")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(resp.getRows().get(0), "missingPriceCount")).isEqualTo(1);

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        SalesOrderItem blankPriceItem = itemCaptor.getAllValues().get(0);
        assertThat(blankPriceItem.getPartNo()).isEqualTo("P-BLANK");
        assertThat(blankPriceItem.getTaxIncludedUnitPrice()).isNull();
        assertThat(blankPriceItem.getUntaxedUnitPrice()).isNull();
        assertThat(blankPriceItem.getTaxIncludedTotalPrice()).isNull();
        assertThat(blankPriceItem.getUntaxedTotalPrice()).isNull();
        assertThat(blankPriceItem.getMaterialNo()).isNull();
        assertThat(blankPriceItem.getMaterialNoStatus()).isEqualTo("未确认");

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("missingPriceCount", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("FULL-EXCEL-CONTENT-SHOULD-NOT-BE-LOGGED")
                .doesNotContain("multipart");
    }

    @Test
    void serviceRunsImportAutoSnapshotCompletionAfterAllRowsAreInsertedAndReportsCounts() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderAutoSnapshotCompletionService matchService = mock(SalesOrderAutoSnapshotCompletionService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                auditService,
                matchService
        );
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
            item.setId(item.getPartNo().equals("P-001") ? 501L : item.getPartNo().equals("P-002") ? 502L : 503L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        when(matchService.completeImportedOrder(301L)).thenReturn(matchResp(2, 1, 1, "auto unmatched 1"));
        when(matchService.completeImportedOrder(302L)).thenReturn(matchResp(1, 0, 1, "auto unmatched 1"));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "FULL-EXCEL-CONTENT-SHOULD-NOT-BE-LOGGED".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        );

        verify(matchService).completeImportedOrder(301L);
        verify(matchService).completeImportedOrder(302L);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0).getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getUnmatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getWarningMessage()).isEqualTo("auto unmatched 1");
        assertThat(resp.getRows().get(1).getMatchedItemCount()).isZero();
        assertThat(resp.getRows().get(1).getUnmatchedItemCount()).isEqualTo(1);

        ArgumentCaptor<SalesOrderHeader> headerUpdateCaptor = ArgumentCaptor.forClass(SalesOrderHeader.class);
        verify(headerMapper, org.mockito.Mockito.times(4)).updateById(headerUpdateCaptor.capture());
        assertThat(headerUpdateCaptor.getAllValues()).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(301L);
            assertThat(update.getMatchedItemCount()).isEqualTo(1);
            assertThat(update.getUnmatchedItemCount()).isEqualTo(1);
            assertThat(update.getWarningMessage()).isEqualTo("auto unmatched 1");
        });

        ArgumentCaptor<SalesOrderImportRecord> recordUpdateCaptor = ArgumentCaptor.forClass(SalesOrderImportRecord.class);
        verify(recordMapper, org.mockito.Mockito.times(2)).updateById(recordUpdateCaptor.capture());
        assertThat(recordUpdateCaptor.getAllValues()).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(401L);
            assertThat(update.getMatchedItemCount()).isEqualTo(1);
            assertThat(update.getUnmatchedItemCount()).isEqualTo(1);
            assertThat(update.getWarningMessage()).isEqualTo("auto unmatched 1");
        });

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("matchedItemCount", 1)
                .containsEntry("unmatchedItemCount", 2)
                .containsEntry("autoMatchTrigger", "IMPORT_AUTO");
    }

    private SalesOrderQpoExcelParseResult parseResultWithCustomerExcelSequence() {
        SalesOrderQpoExcelLineDTO first = line(1, "P-SEQ-1", "A", "", 1);
        first.setSalesOrderLineNo(10);
        SalesOrderQpoExcelLineDTO second = line(2, "P-SEQ-2", "A", "", 1);
        second.setSalesOrderLineNo(10);
        SalesOrderQpoExcelLineDTO third = line(4, "P-SEQ-3", "A", "", 1);
        third.setSalesOrderLineNo(99);
        third.setCustomerOrderNo("PO-001");
        third.setCustomerProjectNo("CP-001");
        third.setCustomerOrderTitle("GSQ1");
        third.setOrderType("样品订单");
        third.setInternalProjectNo("IP-001");

        SalesOrderQpoOrderGroupDTO group = new SalesOrderQpoOrderGroupDTO();
        group.setGroupKey("PO-001|IP-001");
        group.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        group.setCustomerOrderNo("PO-001");
        group.setCustomerProjectNo("CP-001");
        group.setCustomerOrderTitle("GSQ1");
        group.setOrderType("样品订单");
        group.setInternalProjectNo("IP-001");
        group.setLines(new ArrayList<>(List.of(first, second, third)));

        SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
        result.setOrders(List.of(group));
        result.setOrderCount(1);
        result.setLineCount(3);
        result.setSourceRowCount(3);
        return result;
    }
}
