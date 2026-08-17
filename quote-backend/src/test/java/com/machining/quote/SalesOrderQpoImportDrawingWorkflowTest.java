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

class SalesOrderQpoImportDrawingWorkflowTest extends SalesOrderQpoImportTestSupport {

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
    void serviceAcceptsVersionDrawingsAndBindsOnlyMatchingPartVersion() throws Exception {
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
        Method importWithVersionDrawings = SalesOrderQpoImportService.class.getMethod(
                "importQpo",
                MultipartFile.class,
                Long.class,
                String.class,
                List.class,
                List.class);
        ReflectionTestUtils.setField(service, "businessFileService", salesOrderImportDrawingStorage());
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithSamePartDifferentVersions());
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
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId("A".equals(item.getVersion()) ? 501L : 502L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));

        SalesOrderQpoImportResp resp = (SalesOrderQpoImportResp) importWithVersionDrawings.invoke(
                service,
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01",
                List.of(),
                List.of(new MockMultipartFile("versionDrawings", "P-100_A.pdf", "application/pdf", "p1".getBytes(StandardCharsets.UTF_8)))
        );

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        SalesOrderItem versionA = itemCaptor.getAllValues().get(0);
        SalesOrderItem versionB = itemCaptor.getAllValues().get(1);
        assertThat(versionA.getPartNo()).isEqualTo("P-100");
        assertThat(versionA.getVersion()).isEqualTo("A");
        assertThat(versionA.getDrawingUrl()).isEqualTo("/uploads/ACME/SOQPO/P-100_A.pdf");
        assertThat(versionA.getHasDrawing()).isTrue();
        assertThat(versionB.getPartNo()).isEqualTo("P-100");
        assertThat(versionB.getVersion()).isEqualTo("B");
        assertThat(versionB.getDrawingUrl()).isNull();
        assertThat(versionB.getHasDrawing()).isFalse();
        assertThat(resp.getDrawingReport())
                .containsEntry("uploadedDrawingCount", 1)
                .containsEntry("matchedItemCount", 1)
                .containsEntry("missingDrawingCount", 1)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("versionMatchedDrawingCount", 1);

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
                .containsEntry("uploadedDrawingCount", 1)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("versionMatchedDrawingCount", 1);
        assertThat((Map<String, Object>) afterData.get("drawingMatchModes"))
                .containsEntry("partNo", 0)
                .containsEntry("partNoVersion", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("P-100_A.pdf")
                .doesNotContain("MockMultipartFile")
                .doesNotContain("multipart")
                .doesNotContain("p1")
                .doesNotContain("FULL-EXCEL-CONTENT-SHOULD-NOT-BE-LOGGED");
    }
}
