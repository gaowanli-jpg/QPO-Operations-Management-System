package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementConfirmDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementExcelParser;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementService;
import com.machining.quote.service.SalesOrderU9SyncService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

class SalesOrderU9ItemDataReplacementStage3Test {
    @Test
    void controllerExposesConfirmEndpointWithSubmitPermissionAndFingerprint() throws Exception {
        Method method = SalesOrderU9SyncController.class.getMethod(
                "confirmItemDataReplacement",
                Long.class,
                String.class,
                org.springframework.web.multipart.MultipartFile.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/orders/{salesOrderId}/item-data-replacement/confirm");
        assertThat(method.getAnnotation(PostMapping.class).consumes())
                .containsExactly(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    void controllerDelegatesConfirmAndRejectsBlankFingerprintBeforeService() {
        SalesOrderU9ItemDataReplacementService service = mock(SalesOrderU9ItemDataReplacementService.class);
        SalesOrderU9SyncController controller = new SalesOrderU9SyncController();
        ReflectionTestUtils.setField(controller, "itemDataReplacementService", service);
        MockMultipartFile file = new MockMultipartFile(
                "excelFile", "replacement.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        SalesOrderU9ItemDataReplacementConfirmDTO expected = new SalesOrderU9ItemDataReplacementConfirmDTO();
        expected.setSuccess(true);
        when(service.confirm(eq(100L), any(), eq("a".repeat(64)))).thenReturn(expected);

        SalesOrderU9ItemDataReplacementConfirmDTO result = controller.confirmItemDataReplacement(
                100L,
                "  " + "a".repeat(64) + "  ",
                file);

        assertThat(result).isSameAs(expected);
        verify(service).confirm(eq(100L), any(), eq("a".repeat(64)));

        assertThatThrownBy(() -> controller.confirmItemDataReplacement(100L, " ", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预览指纹");
        verify(service, never()).confirm(eq(100L), any(), eq(" "));
    }

    @Test
    void confirmIsTransactionalLocksRowsPersistsChangesAndReturnsLatestPreview() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(100L, "SO-100", "未同步");
        SalesOrderItem item = item(200L, 100L, 1, "LK17.5.1.1-1", "A");
        SalesOrderItem unchanged = item(201L, 100L, 2, "P-002", "A");
        unchanged.setRemark("same");
        SalesOrderU9SyncPreviewResp latestPreview = new SalesOrderU9SyncPreviewResp();
        latestPreview.setSalesOrderId(header.getId());
        latestPreview.setSalesOrderNo(header.getSalesOrderNo());
        fixture.withOrder(header, List.of(item, unchanged));
        when(fixture.previewService.previewSalesOrder(header.getId())).thenReturn(latestPreview);
        byte[] excel = excel(
                List.of("图号", "版本", "行号", "数量", "客户交期", "内部交期", "未税单价", "客户订单号", "客户项目号", "内部项目号", "备注"),
                List.of("LK17.5.1.1-1", "A", 1, 8, "2026-09-30", "2026-09-25", "12.50", "PO-900", "CP-900", "IP-900", "ready"),
                List.of("P-002", "A", 2, "", "", "", "", "", "", "", "same"),
                List.of("NO-MATCH", "A", 9, 3, "", "", "", "", "", "", "ignored"));

        String fingerprint = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();
        SalesOrderU9ItemDataReplacementConfirmDTO result = fixture.service.confirm(
                header.getId(),
                new ByteArrayInputStream(excel),
                fingerprint);

        Method confirm = SalesOrderU9ItemDataReplacementService.class.getMethod(
                "confirm", Long.class, java.io.InputStream.class, String.class);
        assertThat(confirm.getAnnotation(Transactional.class)).isNotNull();
        var order = inOrder(fixture.headerMapper, fixture.itemMapper);
        order.verify(fixture.headerMapper).selectByIdForUpdate(header.getId());
        order.verify(fixture.itemMapper).selectByIdForUpdate(item.getId());
        order.verify(fixture.itemMapper).selectByIdForUpdate(unchanged.getId());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(result.getSalesOrderNo()).isEqualTo(header.getSalesOrderNo());
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
        assertThat(result.getSummary().getChangedCount()).isEqualTo(1);
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
        assertThat(item.getCustomerOrderNo()).isEqualTo("PO-900");
        assertThat(item.getCustomerProjectNo()).isEqualTo("CP-900");
        assertThat(item.getInternalProjectNo()).isEqualTo("IP-900");
        assertThat(item.getRemark()).isEqualTo("ready");

        verify(fixture.itemMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.previewService).previewSalesOrder(header.getId());
        ArgumentCaptor<Object> auditCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_U9_ITEM_DATA_REPLACEMENT"),
                eq("sales_order_header"),
                eq(header.getId()),
                isNull(),
                auditCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_U9_ITEM_DATA_REPLACEMENT")),
                isNull());
        Map<String, Object> auditSummary = (Map<String, Object>) auditCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("salesOrderId", header.getId())
                .containsEntry("salesOrderNo", header.getSalesOrderNo())
                .containsEntry("updatedCount", 1)
                .containsKey("durationMs");
        assertThat(auditSummary.get("durationMs")).isInstanceOf(Number.class);
    }

    @Test
    void staleFingerprintIsRejectedWhenOrderOrItemChangedBeforeAnyWrite() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(101L, "SO-101", "未同步");
        SalesOrderItem item = item(210L, 101L, 1, "P-STALE", "A");
        fixture.withOrder(header, List.of(item));
        byte[] excel = excel(
                List.of("图号", "版本", "备注"),
                List.of("P-STALE", "A", "new remark"));

        String fingerprint = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();
        item.setRemark("changed after preview");

        assertThatThrownBy(() -> fixture.service.confirm(header.getId(), new ByteArrayInputStream(excel), fingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class)
                .hasMessageContaining("重新预览");
        verify(fixture.itemMapper, never()).update(any(), any());
        verify(fixture.previewService, never()).previewSalesOrder(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());

        Fixture changedOrder = fixture();
        SalesOrderHeader orderHeader = header(102L, "SO-102", "未同步");
        SalesOrderItem orderItem = item(220L, 102L, 1, "P-ORDER", "A");
        changedOrder.withOrder(orderHeader, List.of(orderItem));
        byte[] orderExcel = excel(List.of("图号", "版本", "备注"), List.of("P-ORDER", "A", "new"));
        String orderFingerprint = changedOrder.service.preview(orderHeader.getId(), new ByteArrayInputStream(orderExcel))
                .getPreviewFingerprint();
        orderHeader.setU9SyncStatus("已同步");

        assertThatThrownBy(() -> changedOrder.service.confirm(
                orderHeader.getId(), new ByteArrayInputStream(orderExcel), orderFingerprint))
                .isInstanceOf(PreviewFingerprintConflictException.class);
        verify(changedOrder.itemMapper, never()).update(any(), any());
    }

    @Test
    void confirmWithoutLineNoUpdatesAllMatchingPartVersionRows() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(105L, "SO-105", "未同步");
        SalesOrderItem first = item(250L, 105L, 1, "P-DUP", "A");
        SalesOrderItem second = item(251L, 105L, 2, " p-dup ", " a ");
        SalesOrderU9SyncPreviewResp latestPreview = new SalesOrderU9SyncPreviewResp();
        latestPreview.setSalesOrderId(header.getId());
        fixture.withOrder(header, List.of(first, second));
        when(fixture.previewService.previewSalesOrder(header.getId())).thenReturn(latestPreview);
        byte[] excel = excel(
                List.of("图号", "版本", "备注"),
                List.of("P-DUP", "A", "bulk remark"));

        String fingerprint = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel))
                .getPreviewFingerprint();
        SalesOrderU9ItemDataReplacementConfirmDTO result = fixture.service.confirm(
                header.getId(),
                new ByteArrayInputStream(excel),
                fingerprint);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(result.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(result.getSummary().getChangedCount()).isEqualTo(2);
        assertThat(first.getRemark()).isEqualTo("bulk remark");
        assertThat(second.getRemark()).isEqualTo("bulk remark");
        assertThat(result.getPreview()).isSameAs(latestPreview);
        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void previewTreatsU9ChangeExcludedCandidateAsUnmatched() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(104L, "SO-104", "未同步");
        SalesOrderItem active = item(240L, 104L, 1, "P-ACTIVE", "A");
        SalesOrderItem excluded = item(241L, 104L, 2, "P-EXCLUDED", "A");
        ReflectionTestUtils.setField(excluded, "u9ChangeExcluded", true);
        fixture.withOrder(header, List.of(active, excluded));
        when(fixture.itemMapper.selectList(any())).thenAnswer(invocation ->
                queryUsesU9ChangeActiveFilter(invocation.getArgument(0)) ? List.of(active) : List.of(active, excluded));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.service.preview(header.getId(), new ByteArrayInputStream(excel(
                List.of("图号", "版本", "备注"),
                List.of("P-EXCLUDED", "A", "should not update"))));

        assertThat(preview.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderItemId()).isNull();
            assertThat(row.getStatus()).isEqualTo(com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus.UNMATCHED);
            assertThat(row.getReason()).contains("未找到对应零件");
        });
        verify(fixture.itemMapper, never()).update(any(), any());
    }

    @Test
    void persistenceFailureStopsBatchBeforeLatestPreviewAndAudit() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(103L, "SO-103", "未同步");
        SalesOrderItem first = item(230L, 103L, 1, "P-001", "A");
        SalesOrderItem second = item(231L, 103L, 2, "P-002", "A");
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

        assertThatThrownBy(() -> fixture.service.confirm(header.getId(), new ByteArrayInputStream(excel), fingerprint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database failure");

        verify(fixture.itemMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(fixture.previewService, never()).previewSalesOrder(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Fixture fixture() {
        SalesOrderU9ItemDataReplacementService service = new SalesOrderU9ItemDataReplacementService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "excelParser", new SalesOrderU9ItemDataReplacementExcelParser());
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        ReflectionTestUtils.setField(service, "previewService", fixture.previewService);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        when(fixture.itemMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
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
        header.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 9, 0));
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
        item.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 10, 0).plusMinutes(id));
        return item;
    }

    private static boolean queryUsesU9ChangeActiveFilter(Object query) {
        if (!(query instanceof QueryWrapper<?> wrapper)) {
            return false;
        }
        return wrapper.getCustomSqlSegment().toLowerCase(Locale.ROOT).contains("u9_change_excluded");
    }

    private record Fixture(
            SalesOrderU9ItemDataReplacementService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            SalesOrderU9SyncService previewService,
            AuditService auditService) {
        private Fixture(SalesOrderU9ItemDataReplacementService service) {
            this(service,
                    mock(SalesOrderHeaderMapper.class),
                    mock(SalesOrderItemMapper.class),
                    mock(DataScopeService.class),
                    mock(SalesOrderU9SyncService.class),
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
