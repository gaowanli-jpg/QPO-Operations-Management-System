package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderChangeController;
import com.machining.quote.dto.SalesOrderChangeItemDataReplacementConfirmResp;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus;
import com.machining.quote.entity.SalesOrderChangeRecord;
import com.machining.quote.entity.SalesOrderChangeRecordItem;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderChangeRecordItemMapper;
import com.machining.quote.mapper.SalesOrderChangeRecordMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderChangeItemDataReplacementService;
import com.machining.quote.service.SalesOrderChangeService;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementExcelParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderChangeItemDataReplacementServiceTest {
    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void controllerExposesPreviewAndConfirmEndpointsWithChangeUpdatePermission() throws Exception {
        Method preview = SalesOrderChangeController.class.getMethod(
                "previewItemDataReplacement", Long.class, org.springframework.web.multipart.MultipartFile.class);
        Method confirm = SalesOrderChangeController.class.getMethod(
                "confirmItemDataReplacement", Long.class, String.class, String.class,
                org.springframework.web.multipart.MultipartFile.class);

        assertThat(preview.getAnnotation(PostMapping.class).value())
                .containsExactly("/{salesOrderId}/item-data-replacement/preview");
        assertThat(preview.getAnnotation(PostMapping.class).consumes())
                .containsExactly(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(preview.getAnnotation(RequirePermission.class).value())
                .containsExactly("change:sales-order:update");
        assertThat(confirm.getAnnotation(PostMapping.class).value())
                .containsExactly("/{salesOrderId}/item-data-replacement/confirm");
        assertThat(confirm.getAnnotation(PostMapping.class).consumes())
                .containsExactly(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(confirm.getAnnotation(RequirePermission.class).value())
                .containsExactly("change:sales-order:update");
    }

    @Test
    void controllerRejectsBlankReasonOrFingerprintAndDelegatesValidConfirm() {
        SalesOrderChangeItemDataReplacementService service = mock(SalesOrderChangeItemDataReplacementService.class);
        SalesOrderChangeController controller = new SalesOrderChangeController();
        ReflectionTestUtils.setField(controller, "itemDataReplacementService", service);
        MockMultipartFile file = new MockMultipartFile(
                "excelFile", "replacement.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        SalesOrderChangeItemDataReplacementConfirmResp expected = new SalesOrderChangeItemDataReplacementConfirmResp();
        expected.setSuccess(true);
        when(service.confirm(eq(100L), any(), eq("f".repeat(64)), eq("本地校正"))).thenReturn(expected);

        SalesOrderChangeItemDataReplacementConfirmResp actual = controller.confirmItemDataReplacement(
                100L,
                "  " + "f".repeat(64) + "  ",
                " 本地校正 ",
                file);

        assertThat(actual).isSameAs(expected);
        verify(service).confirm(eq(100L), any(), eq("f".repeat(64)), eq("本地校正"));
        assertThatThrownBy(() -> controller.confirmItemDataReplacement(100L, " ", "本地校正", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预览指纹");
        assertThatThrownBy(() -> controller.confirmItemDataReplacement(100L, "f".repeat(64), " ", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("变更原因");
    }

    @Test
    void previewUsesU9ParserKeepsHyphenatedPartNoWholeSkipsBlankCellsAndNeverWrites() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(100L, "SO-100", "已同步");
        SalesOrderItem changed = item(200L, 100L, 1, "LK17.5.1.1-1", "A");
        SalesOrderItem unchanged = item(201L, 100L, 2, "P-002", "A");
        unchanged.setRemark("same");
        fixture.withOrder(header, List.of(changed, unchanged));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.service.preview(header.getId(),
                new ByteArrayInputStream(excel(
                        List.of("图号", "版本", "行号", "数量", "备注"),
                        List.of("LK17.5.1.1-1", "A", 1, 8, ""),
                        List.of("P-002", "A", 2, "", "same"),
                        List.of("NO-MATCH", "A", 9, 3, "ignored"))));

        assertThat(preview.isSuccess()).isTrue();
        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getRecognizedFields()).containsExactly("qty", "remark");
        assertThat(preview.getPreviewFingerprint()).matches("[0-9a-f]{64}");
        assertThat(preview.getRows()).extracting(SalesOrderU9ItemDataReplacementRowDTO::getStatus)
                .containsExactly(
                        SalesOrderU9ItemDataReplacementRowStatus.CHANGED,
                        SalesOrderU9ItemDataReplacementRowStatus.UNCHANGED,
                        SalesOrderU9ItemDataReplacementRowStatus.UNMATCHED);
        SalesOrderU9ItemDataReplacementRowDTO changedRow = preview.getRows().get(0);
        assertThat(changedRow.getPartNo()).isEqualTo("LK17.5.1.1-1");
        assertThat(changedRow.getChanges()).extracting("field").containsExactly("qty");
        assertThat(preview.getSummary().getUnmatchedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getErrorCount()).isZero();

        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.changeRecordMapper, never()).insert(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmReparsesLocksPersistsChangesWritesChangeRecordsAndReturnsLatestPreview() throws Exception {
        AuthContext.set(new AuthContext.SessionUser(7, "market01", "市场一号", "MARKET_USER"));
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SO-101", "已同步");
        SalesOrderItem item = item(210L, 101L, 1, "LK17.5.1.1-1", "A");
        SalesOrderItem unchanged = item(211L, 101L, 2, "P-002", "A");
        unchanged.setRemark("same");
        SalesOrderChangePreviewResp latestPreview = new SalesOrderChangePreviewResp();
        latestPreview.setSalesOrderId(header.getId());
        latestPreview.setSalesOrderNo(header.getSalesOrderNo());
        fixture.withOrder(header, List.of(item, unchanged));
        when(fixture.previewService.preview(header.getId())).thenReturn(latestPreview);
        when(fixture.changeRecordMapper.insert(any(SalesOrderChangeRecord.class))).thenAnswer(invocation -> {
            SalesOrderChangeRecord record = invocation.getArgument(0);
            record.setId(9101L);
            return 1;
        });
        when(fixture.changeRecordItemMapper.insert(any(SalesOrderChangeRecordItem.class))).thenReturn(1);
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "数量", "客户交期", "内部交期", "未税单价", "客户订单号", "客户项目号", "内部项目号", "备注"),
                List.of("LK17.5.1.1-1", "A", 1, 8, "2026-09-30", "2026-09-25", "12.50", "PO-900", "CP-900", "IP-900", "ready"),
                List.of("P-002", "A", 2, "", "", "", "", "", "", "", "same"),
                List.of("NO-MATCH", "A", 9, 3, "", "", "", "", "", "", "ignored"));
        String fingerprint = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();

        SalesOrderChangeItemDataReplacementConfirmResp result = fixture.service.confirm(
                header.getId(),
                new ByteArrayInputStream(excel),
                fingerprint,
                "U9 已人工调整，QPO 本地校正");

        Method confirm = SalesOrderChangeItemDataReplacementService.class.getMethod(
                "confirm", Long.class, java.io.InputStream.class, String.class, String.class);
        assertThat(confirm.getAnnotation(Transactional.class)).isNotNull();
        var order = inOrder(fixture.headerMapper, fixture.itemMapper);
        order.verify(fixture.headerMapper).selectByIdForUpdate(header.getId());
        order.verify(fixture.itemMapper).selectByIdForUpdate(item.getId());
        order.verify(fixture.itemMapper).selectByIdForUpdate(unchanged.getId());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(result.getSalesOrderNo()).isEqualTo(header.getSalesOrderNo());
        assertThat(result.getChangeRecordId()).isEqualTo(9101L);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getChangedFields()).containsExactlyInAnyOrder(
                "qty",
                "customerDeliveryDate",
                "internalDeliveryDate",
                "untaxedUnitPrice",
                "customerOrderNo",
                "customerProjectNo",
                "internalProjectNo",
                "remark");
        assertThat(result.getSummary().getUnmatchedCount()).isEqualTo(1);
        assertThat(result.getPreview()).isSameAs(latestPreview);

        assertThat(item.getQty()).isEqualTo(8);
        assertThat(item.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(item.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(item.getUntaxedUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getUntaxedTotalPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getTaxIncludedTotalPrice()).isEqualByComparingTo("120.00");
        assertThat(item.getRemainingQty()).isEqualTo(5);
        assertThat(item.getUnshippedQty()).isEqualTo(6);
        assertThat(header.getU9SyncStatus()).isEqualTo("已同步");

        verify(fixture.itemMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        ArgumentCaptor<SalesOrderChangeRecord> recordCaptor = ArgumentCaptor.forClass(SalesOrderChangeRecord.class);
        verify(fixture.changeRecordMapper).insert(recordCaptor.capture());
        SalesOrderChangeRecord record = recordCaptor.getValue();
        assertThat(record.getChangeType()).isEqualTo("ITEM_DATA_REPLACEMENT");
        assertThat(record.getReason()).isEqualTo("U9 已人工调整，QPO 本地校正");
        assertThat(record.getUpdatedCount()).isEqualTo(1);
        assertThat(record.getChangedFieldsJson()).contains("qty", "remark");

        ArgumentCaptor<SalesOrderChangeRecordItem> detailCaptor = ArgumentCaptor.forClass(SalesOrderChangeRecordItem.class);
        verify(fixture.changeRecordItemMapper).insert(detailCaptor.capture());
        SalesOrderChangeRecordItem detail = detailCaptor.getValue();
        assertThat(detail.getChangeRecordId()).isEqualTo(9101L);
        assertThat(detail.getSalesOrderItemId()).isEqualTo(item.getId());
        assertThat(detail.getBeforeSnapshotJson()).contains("qty", "5", "untaxedUnitPrice", "10.00");
        assertThat(detail.getAfterSnapshotJson()).contains("qty", "8", "untaxedUnitPrice", "12.50");
        assertThat(detail.getAfterSnapshotJson())
                .doesNotContain("drawingUrl", "u9Request", "u9Response", "Authorization", "token");

        ArgumentCaptor<Object> auditCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT"),
                eq("sales_order_header"),
                eq(header.getId()),
                isNull(),
                auditCaptor.capture(),
                eq("CHANGE"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_CHANGE_ITEM_DATA_REPLACEMENT")),
                isNull());
        Map<String, Object> auditSummary = (Map<String, Object>) auditCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("salesOrderId", header.getId())
                .containsEntry("salesOrderNo", header.getSalesOrderNo())
                .containsEntry("changeRecordId", 9101L)
                .containsEntry("reason", "U9 已人工调整，QPO 本地校正")
                .containsEntry("updatedCount", 1)
                .containsEntry("unmatchedCount", 1)
                .containsEntry("errorCount", 0)
                .containsKey("durationMs");
    }

    @Test
    void confirmRejectsBlankReasonStaleFingerprintErrorsAndNonSyncedOrdersBeforeWriting() {
        Fixture missingReason = fixture();
        SalesOrderHeader header = header(102L, "SO-102", "已同步");
        SalesOrderItem item = item(220L, 102L, 1, "P-001", "A");
        missingReason.withOrder(header, List.of(item));
        byte[] excel = excel(List.of("图号", "版本", "备注"), List.of("P-001", "A", "new"));
        String fingerprint = missingReason.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();
        assertThatThrownBy(() -> missingReason.service.confirm(
                header.getId(), new ByteArrayInputStream(excel), fingerprint, " "))
                .hasMessageContaining("变更原因");
        verify(missingReason.itemMapper, never()).update(any(), any());

        Fixture stale = fixture();
        SalesOrderHeader staleHeader = header(103L, "SO-103", "已同步");
        SalesOrderItem staleItem = item(230L, 103L, 1, "P-STALE", "A");
        stale.withOrder(staleHeader, List.of(staleItem));
        byte[] staleExcel = excel(List.of("图号", "版本", "备注"), List.of("P-STALE", "A", "new"));
        String staleFingerprint = stale.service.preview(staleHeader.getId(), new ByteArrayInputStream(staleExcel))
                .getPreviewFingerprint();
        staleItem.setRemark("changed after preview");
        assertThatThrownBy(() -> stale.service.confirm(
                staleHeader.getId(), new ByteArrayInputStream(staleExcel), staleFingerprint, "本地校正"))
                .isInstanceOf(PreviewFingerprintConflictException.class)
                .hasMessageContaining("重新预览");
        verify(stale.itemMapper, never()).update(any(), any());
        verify(stale.changeRecordMapper, never()).insert(any());
        verify(stale.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());

        Fixture errors = fixture();
        SalesOrderHeader errorHeader = header(104L, "SO-104", "已同步");
        errors.withOrder(errorHeader, List.of(
                item(240L, 104L, 1, "P-DUP", "A"),
                item(241L, 104L, 2, "P-DUP", "A")));
        byte[] errorExcel = excel(List.of("图号", "版本", "备注"), List.of("P-DUP", "A", "new"));
        String errorFingerprint = errors.service.preview(errorHeader.getId(), new ByteArrayInputStream(errorExcel))
                .getPreviewFingerprint();
        assertThatThrownBy(() -> errors.service.confirm(
                errorHeader.getId(), new ByteArrayInputStream(errorExcel), errorFingerprint, "本地校正"))
                .hasMessageContaining("不能确认");
        verify(errors.itemMapper, never()).update(any(), any());

        Fixture wrongStatus = fixture();
        SalesOrderHeader pending = header(105L, "SO-105", "结果待确认");
        SalesOrderItem pendingItem = item(250L, 105L, 1, "P-PENDING", "A");
        wrongStatus.withOrder(pending, List.of(pendingItem));
        assertThatThrownBy(() -> wrongStatus.service.preview(
                pending.getId(), new ByteArrayInputStream(excel(List.of("图号", "版本", "备注"), List.of("P-PENDING", "A", "new")))))
                .hasMessageContaining("只允许变更已同步 U9 的销售订单");
        verify(wrongStatus.itemMapper, never()).update(any(), any());
    }

    @Test
    void previewTreatsU9ChangeExcludedCandidateAsUnmatchedInSalesOrderChange() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(107L, "SO-107", "已同步");
        SalesOrderItem active = item(270L, 107L, 1, "P-ACTIVE", "A");
        SalesOrderItem excluded = item(271L, 107L, 2, "P-EXCLUDED", "A");
        ReflectionTestUtils.setField(excluded, "u9ChangeExcluded", true);
        fixture.withOrder(header, List.of(active, excluded));
        when(fixture.itemMapper.selectList(any())).thenAnswer(invocation ->
                queryUsesU9ChangeActiveFilter(invocation.getArgument(0)) ? List.of(active) : List.of(active, excluded));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel(
                List.of("图号", "版本", "备注"),
                List.of("P-EXCLUDED", "A", "should not update"))));

        assertThat(preview.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderItemId()).isNull();
            assertThat(row.getStatus()).isEqualTo(SalesOrderU9ItemDataReplacementRowStatus.UNMATCHED);
            assertThat(row.getReason()).contains("未找到对应零件");
        });
        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.changeRecordMapper, never()).insert(any());
    }

    @Test
    void persistenceFailureStopsBatchBeforeLatestPreviewAuditAndChangeRecordCompletion() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(106L, "SO-106", "已同步");
        SalesOrderItem first = item(260L, 106L, 1, "P-001", "A");
        SalesOrderItem second = item(261L, 106L, 2, "P-002", "A");
        fixture.withOrder(header, List.of(first, second));
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("database failure"));
        byte[] excel = excel(
                List.of("图号", "版本", "备注"),
                List.of("P-001", "A", "first"),
                List.of("P-002", "A", "second"));
        String fingerprint = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();

        assertThatThrownBy(() -> fixture.service.confirm(
                header.getId(), new ByteArrayInputStream(excel), fingerprint, "本地校正"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database failure");

        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.changeRecordMapper, never()).insert(any());
        verify(fixture.previewService, never()).preview(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Fixture fixture() {
        SalesOrderChangeItemDataReplacementService service = new SalesOrderChangeItemDataReplacementService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "excelParser", new SalesOrderU9ItemDataReplacementExcelParser());
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "changeRecordMapper", fixture.changeRecordMapper);
        ReflectionTestUtils.setField(service, "changeRecordItemMapper", fixture.changeRecordItemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "previewService", fixture.previewService);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(fixture.changeRecordMapper.insert(any(SalesOrderChangeRecord.class))).thenAnswer(invocation -> {
            SalesOrderChangeRecord record = invocation.getArgument(0);
            record.setId(7001L);
            return 1;
        });
        when(fixture.changeRecordItemMapper.insert(any(SalesOrderChangeRecordItem.class))).thenReturn(1);
        return fixture;
    }

    private static byte[] excel(List<String> headers, List<?>... rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("replacement");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                List<?> values = rows[r];
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SalesOrderHeader header(Long id, String no, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(no);
        header.setU9SyncStatus(u9Status);
        header.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 9, 0));
        return header;
    }

    private static SalesOrderItem item(Long id, Long salesOrderId, Integer lineNo, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setQty(5);
        item.setReleasedQty(3);
        item.setShippedQty(2);
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 1));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 25));
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setUntaxedTotalPrice(new BigDecimal("50.00"));
        item.setTaxIncludedUnitPrice(new BigDecimal("15.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("75.00"));
        item.setRemainingQty(2);
        item.setUnshippedQty(3);
        item.setCustomerOrderNo("PO-OLD");
        item.setCustomerProjectNo("CP-OLD");
        item.setInternalProjectNo("IP-OLD");
        item.setRemark("old");
        item.setDrawingUrl("https://private.example/drawing.pdf");
        item.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 10, 0).plusMinutes(id));
        return item;
    }

    private static boolean queryUsesU9ChangeActiveFilter(Object query) {
        if (!(query instanceof QueryWrapper<?> wrapper)) {
            return false;
        }
        return wrapper.getCustomSqlSegment().toLowerCase(Locale.ROOT).contains("u9_change_excluded");
    }

    private record Fixture(
            SalesOrderChangeItemDataReplacementService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            SalesOrderChangeRecordMapper changeRecordMapper,
            SalesOrderChangeRecordItemMapper changeRecordItemMapper,
            DataScopeService dataScopeService,
            SalesOrderChangeService previewService,
            AuditService auditService) {
        private Fixture(SalesOrderChangeItemDataReplacementService service) {
            this(service,
                    mock(SalesOrderHeaderMapper.class),
                    mock(SalesOrderItemMapper.class),
                    mock(SalesOrderChangeRecordMapper.class),
                    mock(SalesOrderChangeRecordItemMapper.class),
                    mock(DataScopeService.class),
                    mock(SalesOrderChangeService.class),
                    mock(AuditService.class));
        }

        @SuppressWarnings("unchecked")
        private void withOrder(SalesOrderHeader header, List<SalesOrderItem> items) {
            when(headerMapper.selectById(header.getId())).thenReturn(header);
            when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            ArgumentCaptor<Wrapper<SalesOrderItem>> ignored = ArgumentCaptor.forClass(Wrapper.class);
            when(itemMapper.selectList(ignored.capture())).thenReturn(items);
            for (SalesOrderItem item : items) {
                when(itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
            }
        }
    }
}
