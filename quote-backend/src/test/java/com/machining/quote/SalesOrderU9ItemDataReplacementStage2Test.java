package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelParseResult;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementPreviewDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementRowStatus;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementExcelParser;
import com.machining.quote.service.SalesOrderU9ItemDataReplacementService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9ItemDataReplacementStage2Test {
    @Test
    void controllerExposesPreviewEndpointWithSubmitPermissionAndMultipartFile() throws Exception {
        Method method = SalesOrderU9SyncController.class.getMethod(
                "previewItemDataReplacement", Long.class, org.springframework.web.multipart.MultipartFile.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/orders/{salesOrderId}/item-data-replacement/preview");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    void controllerRejectsEmptyOrNonExcelFilesBeforeService() {
        SalesOrderU9ItemDataReplacementService service = mock(SalesOrderU9ItemDataReplacementService.class);
        SalesOrderU9SyncController controller = new SalesOrderU9SyncController();
        ReflectionTestUtils.setField(controller, "itemDataReplacementService", service);

        assertThatThrownBy(() -> controller.previewItemDataReplacement(
                100L,
                new MockMultipartFile("excelFile", "replacement.xlsx", "application/octet-stream", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> controller.previewItemDataReplacement(
                100L,
                new MockMultipartFile("excelFile", "replacement.csv", "text/csv", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx")
                .hasMessageContaining(".xls");

        verify(service, never()).preview(any(), any());
    }

    @Test
    void parserKeepsHyphenatedPartNoWholeAndIgnoresBlankReplacementCells() throws Exception {
        SalesOrderU9ItemDataReplacementExcelParser parser = new SalesOrderU9ItemDataReplacementExcelParser();

        SalesOrderU9ItemDataReplacementExcelParseResult parsed = parser.parse(new ByteArrayInputStream(excel(
                List.of("图号", "版本", "行号", "数量", "客户交期", "备注"),
                List.of("LK17.5.1.1-1", "A", 1, 8, "2026-09-30", ""))));

        assertThat(parsed.getRecognizedFields()).containsExactly("qty", "customerDeliveryDate", "remark");
        assertThat(parsed.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getPartNo()).isEqualTo("LK17.5.1.1-1");
            assertThat(row.getNormalizedPartNo()).isEqualTo("LK17.5.1.1-1");
            assertThat(row.getLineNo()).isEqualTo(1);
            assertThat(row.getValues()).containsEntry("qty", 8);
            assertThat(row.getValues()).containsEntry("customerDeliveryDate", LocalDate.of(2026, 9, 30));
            assertThat(row.getValues()).doesNotContainKey("remark");
            assertThat(row.getErrors()).isEmpty();
        });
    }

    @Test
    void parserRequiresPartVersionAndAtLeastOneBusinessField() throws Exception {
        SalesOrderU9ItemDataReplacementExcelParser parser = new SalesOrderU9ItemDataReplacementExcelParser();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(excel(
                List.of("图号", "版本", "行号"),
                List.of("P-001", "A", 1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一个可替换业务表头");
    }

    @Test
    void previewMatchesInsideCurrentSalesOrderWithOptionalLineNoAndNeverWrites() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(100L, "SO-100", "未同步");
        SalesOrderItem changed = item(200L, 100L, 1, "LK17.5.1.1-1", "A");
        SalesOrderItem unchanged = item(201L, 100L, 2, "P-002", "A");
        unchanged.setRemark("same");
        fixture.withOrder(header, List.of(changed, unchanged));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.service.preview(header.getId(),
                new ByteArrayInputStream(excel(
                        List.of("图号", "版本", "行号", "数量", "备注"),
                        List.of("LK17.5.1.1-1", "A", 1, 8, "ready"),
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
        assertThat(changedRow.getSalesOrderItemId()).isEqualTo(changed.getId());
        assertThat(changedRow.getLineNo()).isEqualTo(1);
        assertThat(changedRow.getChanges()).extracting("field")
                .containsExactly("qty", "remark");
        assertThat(changedRow.getChanges().get(0).getBeforeValue()).isEqualTo(5);
        assertThat(changedRow.getChanges().get(0).getAfterValue()).isEqualTo(8);
        assertThat(preview.getSummary().getMatchedCount()).isEqualTo(2);
        assertThat(preview.getSummary().getChangedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getUnmatchedCount()).isEqualTo(1);
        assertThat(preview.getSummary().getErrorCount()).isZero();

        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).update(any(), any());
    }

    @Test
    void unmatchedRowsDoNotBlockConfirmationButErrorsDo() {
        Fixture ambiguous = fixture();
        SalesOrderHeader header = header(101L, "SO-101", "未同步");
        ambiguous.withOrder(header, List.of(
                item(210L, 101L, 1, "P-DUP", "A"),
                item(211L, 101L, 2, "P-DUP", "A")));

        SalesOrderU9ItemDataReplacementPreviewDTO duplicatePreview = ambiguous.service.preview(header.getId(),
                new ByteArrayInputStream(excel(
                        List.of("图号", "版本", "备注"),
                        List.of("P-DUP", "A", "new remark"),
                        List.of("NO-MATCH", "A", "unmatched"))));

        assertThat(duplicatePreview.isCanConfirm()).isFalse();
        assertThat(duplicatePreview.getRows()).extracting(SalesOrderU9ItemDataReplacementRowDTO::getStatus)
                .containsExactly(
                        SalesOrderU9ItemDataReplacementRowStatus.ERROR,
                        SalesOrderU9ItemDataReplacementRowStatus.UNMATCHED);
        assertThat(duplicatePreview.getSummary().getErrorCount()).isEqualTo(1);
        assertThat(duplicatePreview.getSummary().getUnmatchedCount()).isEqualTo(1);
    }

    @Test
    void lockedU9StatusRowsAreSkippedInPreview() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(102L, "SO-102", "已同步");
        fixture.withOrder(header, List.of(item(220L, 102L, 1, "P-LOCK", "A")));

        SalesOrderU9ItemDataReplacementPreviewDTO preview = fixture.service.preview(header.getId(),
                new ByteArrayInputStream(excel(
                        List.of("图号", "版本", "备注"),
                        List.of("P-LOCK", "A", "new remark"))));

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(SalesOrderU9ItemDataReplacementRowStatus.SKIPPED);
            assertThat(row.getReason()).contains("已同步");
        });
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).update(any(), any());
    }

    private static Fixture fixture() {
        SalesOrderU9ItemDataReplacementService service = new SalesOrderU9ItemDataReplacementService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "excelParser", new SalesOrderU9ItemDataReplacementExcelParser());
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
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
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 1));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 25));
        item.setUntaxedUnitPrice(new BigDecimal("10.00"));
        item.setCustomerOrderNo("PO-OLD");
        item.setCustomerProjectNo("CP-OLD");
        item.setInternalProjectNo("IP-OLD");
        item.setRemark("old");
        return item;
    }

    private record Fixture(
            SalesOrderU9ItemDataReplacementService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService) {
        private Fixture(SalesOrderU9ItemDataReplacementService service) {
            this(service, mock(SalesOrderHeaderMapper.class), mock(SalesOrderItemMapper.class),
                    mock(DataScopeService.class));
        }

        @SuppressWarnings("unchecked")
        private void withOrder(SalesOrderHeader header, List<SalesOrderItem> items) {
            when(headerMapper.selectById(header.getId())).thenReturn(header);
            ArgumentCaptor<Wrapper<SalesOrderItem>> ignored = ArgumentCaptor.forClass(Wrapper.class);
            when(itemMapper.selectList(ignored.capture())).thenReturn(items);
        }
    }
}
