package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderProductionTaskNoticeExportService;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderProductionTaskNoticeExportTest {

    @Test
    void exportSkipsDiscardedSalesOrderItems() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(930L);
        header.setSalesOrderNo("SOQPO-930");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 8));

        SalesOrderItem active = item(9301L, 930L, 1, null);
        active.setPartNo("PN-ACTIVE");
        active.setQty(2);
        active.setVersion("A");

        SalesOrderItem discarded = item(9302L, 930L, 2, null);
        discarded.setPartNo("PN-DISCARDED");
        discarded.setQty(9);
        discarded.setVersion("B");
        discarded.setStatus(QuoteStatusWorkflow.DISCARDED);

        Fixture fixture = fixture(header, List.of(active, discarded));

        byte[] bytes = fixture.service.export(930L);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT"),
                eq("sales_order_header"),
                eq(930L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("rowCount", 1);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            assertThat(text(sheet, 12, "P")).isEqualTo("PN-ACTIVE");
            assertThat(text(sheet, 12, "T")).isEqualTo("2");
            assertThat(text(sheet, 13, "P")).isBlank();
        }
    }

    @Test
    void exportSkipsU9ChangeExcludedSalesOrderItems() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(932L);
        header.setSalesOrderNo("SOQPO-932");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 8));

        SalesOrderItem active = item(9321L, 932L, 1, null);
        active.setPartNo("PN-PRODUCTION");
        active.setQty(2);
        active.setVersion("A");

        SalesOrderItem excluded = item(9322L, 932L, 2, null);
        excluded.setPartNo("PN-U9-EXCLUDED");
        excluded.setQty(9);
        excluded.setVersion("B");
        excluded.setU9ChangeExcluded(true);

        Fixture fixture = fixture(header, List.of(active, excluded));

        byte[] bytes = fixture.service.export(932L);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT"),
                eq("sales_order_header"),
                eq(932L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("rowCount", 1);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            assertThat(text(sheet, 12, "P")).isEqualTo("PN-PRODUCTION");
            assertThat(text(sheet, 12, "T")).isEqualTo("2");
            assertThat(text(sheet, 13, "P")).isBlank();
        }
    }

    @Test
    void exportAllowsEmptyNoticeWhenAllSalesOrderItemsAreDiscarded() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(931L);
        header.setSalesOrderNo("SOQPO-931");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 8));

        SalesOrderItem discarded = item(9311L, 931L, 1, null);
        discarded.setPartNo("PN-DISCARDED-ONLY");
        discarded.setQty(5);
        discarded.setStatus(QuoteStatusWorkflow.DISCARDED);

        Fixture fixture = fixture(header, List.of(discarded));

        byte[] bytes = fixture.service.export(931L);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT"),
                eq("sales_order_header"),
                eq(931L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("rowCount", 0);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            assertThat(text(sheet, 12, "P")).isBlank();
            assertThat(text(sheet, 12, "T")).isBlank();
            assertThat(text(sheet, 15, "AB")).contains("SOQPO-931");
        }
    }

    @Test
    void exportFillsProductionTaskNoticeTemplateFromSalesOrderHeaderAndItems() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(900L);
        header.setSalesOrderNo("SOQPO-900");
        header.setDocumentNo("DOC-900");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 4));
        header.setCustomerName("客户A");

        SalesOrderItem repeated = item(901L, 900L, 1, "已匹配");
        repeated.setInternalProjectNo("IP-001");
        repeated.setCustomerOrderNo("PO-001");
        repeated.setCustomerProjectNo("CP-001");
        repeated.setPartNo("PN-001");
        repeated.setQty(3);
        repeated.setVersion("A");
        repeated.setEngineeringMaterialName("进口 6061");
        repeated.setSurfaceTreatment("本色阳极");
        repeated.setSalesOrderDate(LocalDate.of(2026, 9, 5));
        repeated.setInternalDeliveryDate(LocalDate.of(2026, 9, 20));
        repeated.setRemark("首件备注");

        SalesOrderItem fresh = item(902L, 900L, 2, "未匹配");
        fresh.setInternalProjectNo("IP-002");
        fresh.setCustomerOrderNo("PO-002");
        fresh.setCustomerProjectNo("CP-002");
        fresh.setPartNo("PN-002");
        fresh.setQty(5);
        fresh.setVersion("B");
        fresh.setEngineeringMaterialName("SUS304");
        fresh.setMaterialName("不应优先使用的销售材料");
        fresh.setSurfaceTreatment("钝化");
        fresh.setSalesOrderDate(LocalDate.of(2026, 9, 6));
        fresh.setInternalDeliveryDate(LocalDate.of(2026, 9, 25));
        fresh.setRemark("第二件备注");

        Fixture fixture = fixture(header, List.of(fresh, repeated));

        byte[] bytes = fixture.service.export(900L);

        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.itemMapper).selectList(any(QueryWrapper.class));
        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT"),
                eq("sales_order_header"),
                eq(900L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                eq(AuditService.labelForAction("SALES_ORDER_PRODUCTION_TASK_NOTICE_EXPORT")),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("salesOrderId", 900L)
                .containsEntry("salesOrderNo", "SOQPO-900")
                .containsEntry("rowCount", 2);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            assertThat(sheet).isNotNull();
            assertThat(text(sheet, 3, "M")).isEqualTo("常熟市兆恒众力精密机械有限公司");
            assertThat(text(sheet, 5, "Q")).isEqualTo("生产任务通知单");
            assertThat(text(sheet, 8, "AA")).isEqualTo("下单日期：");
            assertThat(text(sheet, 8, "AD")).isEqualTo("2026-09-04");

            assertThat(text(sheet, 12, "B")).isEqualTo("10");
            assertThat(text(sheet, 12, "E")).isEqualTo("重复");
            assertThat(text(sheet, 12, "H")).isEqualTo("IP-001");
            assertThat(text(sheet, 12, "J")).isEqualTo("PO-001");
            assertThat(text(sheet, 12, "L")).isEqualTo("CP-001");
            assertThat(text(sheet, 12, "P")).isEqualTo("PN-001");
            assertThat(text(sheet, 12, "T")).isEqualTo("3");
            assertThat(text(sheet, 12, "U")).isEqualTo("A");
            assertThat(text(sheet, 12, "V")).isEqualTo("进口 6061");
            assertThat(text(sheet, 12, "Y")).isEqualTo("本色阳极");
            assertThat(text(sheet, 12, "AC")).isEqualTo("2026-09-05");
            assertThat(text(sheet, 12, "AF")).isEqualTo("2026-09-20");
            assertThat(text(sheet, 12, "AG")).isEqualTo("首件备注");

            assertThat(text(sheet, 13, "B")).isEqualTo("20");
            assertThat(text(sheet, 13, "E")).isEqualTo("新件");
            assertThat(text(sheet, 13, "H")).isEqualTo("IP-002");
            assertThat(text(sheet, 13, "T")).isEqualTo("5");
            assertThat(text(sheet, 13, "V")).isEqualTo("SUS304");
            assertThat(text(sheet, 13, "AF")).isEqualTo("2026-09-25");
            assertThat(text(sheet, 13, "AG")).isEqualTo("第二件备注");

            assertThat(hasMergedRegion(sheet, "B12:D12")).isTrue();
            assertThat(hasMergedRegion(sheet, "B13:D13")).isTrue();
            assertThat(hasMergedRegion(sheet, "B14:D14")).isFalse();
            assertThat(text(sheet, 17, "AB")).isEqualTo("表单编号：SOQPO-900");
            assertThat(text(sheet, 67, "AB")).isBlank();
            assertThat(visibleText(sheet, 22, "B")).isBlank();
            assertThat(visibleText(sheet, 22, "P")).isBlank();
        }
    }

    @Test
    void exportKeepsTemplateFooterBelowExtendedDataRows() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(910L);
        header.setSalesOrderNo("SOQPO-910");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 4));

        List<SalesOrderItem> items = java.util.stream.IntStream.rangeClosed(1, 53)
                .mapToObj(index -> {
                    SalesOrderItem item = item(9100L + index, 910L, index, index % 2 == 0 ? "已匹配" : "未匹配");
                    item.setPartNo("PN-" + index);
                    item.setQty(1);
                    item.setEngineeringMaterialName("6061");
                    return item;
                })
                .toList();

        Fixture fixture = fixture(header, items);

        byte[] bytes = fixture.service.export(910L);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            assertThat(text(sheet, 64, "B")).isEqualTo("530");
            assertThat(text(sheet, 64, "P")).isEqualTo("PN-53");
            assertThat(hasMergedRegion(sheet, "B65:D65")).isFalse();
            assertThat(text(sheet, 67, "AB")).isEqualTo("表单编号：SOQPO-910");
            assertThat(text(sheet, 68, "AB")).isBlank();
        }
    }

    @Test
    void exportAdaptsLongNoticeTextWithinMergedDataCells() throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(915L);
        header.setSalesOrderNo("SOQPO-915");
        header.setSalesOrderDate(LocalDate.of(2026, 9, 8));

        SalesOrderItem item = item(9151L, 915L, 1, "未匹配");
        item.setInternalProjectNo("内部项目号-2026-超长项目编号-ALPHA-001");
        item.setCustomerOrderNo("客户订单号-超长订单编号-CUSTOMER-2026-0001");
        item.setCustomerProjectNo("客户项目号-超长项目编号-PROJECT-2026-0001");
        item.setPartNo("PART-VERY-LONG-5.71.18502-REV-A");
        item.setQty(3);
        item.setVersion("REVISION-A");
        item.setEngineeringMaterialName("铝合金 6061-T6 阳极氧化用长材料描述");
        item.setSurfaceTreatment("本色阳极氧化及喷砂处理-长工艺说明");
        item.setRemark("这是用于验证生产任务通知单自适应布局的超长备注，包含中文、English、数字2026以及多个业务说明字段。");

        Fixture fixture = fixture(header, List.of(item));

        byte[] bytes = fixture.service.export(915L);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            Row dataRow = sheet.getRow(11);

            assertThat(dataRow.getHeightInPoints()).isGreaterThan(15F);
            for (String column : List.of("H", "J", "L", "P", "V", "Y", "AG")) {
                assertThat(dataRow.getCell(columnIndex(column)).getCellStyle().getWrapText())
                        .as("%s should wrap long notice text", column)
                        .isTrue();
            }
            assertThat(columnWidth(sheet, "B", "AI")).isLessThanOrEqualTo(146 * 256);
            assertThat(columnWidth(sheet, "H", "I")).isGreaterThan(12 * 256);
            assertThat(hasMergedRegion(sheet, "H12:I12")).isTrue();
            assertThat(text(sheet, 16, "AB")).isEqualTo("表单编号：SOQPO-915");
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
            assertThat(sheet.getFitToPage()).isTrue();
        }
    }

    @Test
    void exportSupportsLargeProductionTaskNoticeRowsUpToTwoThousand() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            assertLargeProductionTaskNoticeExport(150);
            assertLargeProductionTaskNoticeExport(2000);
        });
    }

    @Test
    void controllerEndpointIsSecuredAndWritesXlsxDownload() throws Exception {
        SalesOrderProductionTaskNoticeExportService service = mock(SalesOrderProductionTaskNoticeExportService.class);
        byte[] bytes = new byte[] { 1, 2, 3 };
        when(service.export(901L)).thenReturn(bytes);

        com.machining.quote.controller.SalesOrderManageController controller = new com.machining.quote.controller.SalesOrderManageController();
        ReflectionTestUtils.setField(controller, "productionTaskNoticeExportService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.exportProductionTaskNotice(901L, response);

        assertThat(response.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition"))
                .contains("filename*=UTF-8''")
                .contains("901")
                .contains("%E7%94%9F%E4%BA%A7%E4%BB%BB%E5%8A%A1%E9%80%9A%E7%9F%A5%E5%8D%95.xlsx");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
    }

    private Fixture fixture(SalesOrderHeader header, List<SalesOrderItem> items) {
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        AuditService auditService = mock(AuditService.class);
        when(headerMapper.selectById(header.getId())).thenReturn(header);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(items);
        SalesOrderProductionTaskNoticeExportService service = new SalesOrderProductionTaskNoticeExportService();
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return new Fixture(service, headerMapper, itemMapper, dataScopeService, auditService);
    }

    private void assertLargeProductionTaskNoticeExport(int itemCount) throws Exception {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(920L + itemCount);
        header.setSalesOrderNo("SOQPO-LARGE-" + itemCount);
        header.setSalesOrderDate(LocalDate.of(2026, 9, 4));

        List<SalesOrderItem> items = java.util.stream.IntStream.rangeClosed(1, itemCount)
                .mapToObj(index -> {
                    SalesOrderItem item = item(920000L + index, header.getId(), index, index % 2 == 0 ? "已匹配" : "未匹配");
                    item.setPartNo("PN-LARGE-" + index);
                    item.setQty(index);
                    item.setVersion("V" + index);
                    item.setEngineeringMaterialName("6061");
                    item.setSurfaceTreatment("本色阳极");
                    item.setSalesOrderDate(LocalDate.of(2026, 9, 4));
                    item.setInternalDeliveryDate(LocalDate.of(2026, 9, 24));
                    item.setRemark("ROW-" + index);
                    return item;
                })
                .toList();

        Fixture fixture = fixture(header, items);

        byte[] bytes = fixture.service.export(header.getId());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("StandardDocPrintTemplate");
            int lastDataRow = 11 + itemCount;
            assertDataRowStyleMatches(sheet, 62, 63);
            assertDataRowStyleMatches(sheet, 62, lastDataRow - 1);
            assertThat(text(sheet, lastDataRow, "B")).isEqualTo(String.valueOf(itemCount * 10));
            assertThat(text(sheet, lastDataRow, "P")).isEqualTo("PN-LARGE-" + itemCount);
            assertThat(text(sheet, lastDataRow, "AG")).isEqualTo("ROW-" + itemCount);
            assertThat(text(sheet, lastDataRow + 3, "AB")).isEqualTo("表单编号：SOQPO-LARGE-" + itemCount);
        }
    }

    private void assertDataRowStyleMatches(Sheet sheet, int templateRowNumber, int actualRowNumber) {
        Row templateRow = sheet.getRow(templateRowNumber - 1);
        Row actualRow = sheet.getRow(actualRowNumber - 1);
        assertThat(actualRow).as("row %s exists", actualRowNumber).isNotNull();
        for (int columnIndex = columnIndex("B"); columnIndex <= columnIndex("AI"); columnIndex++) {
            assertThat(actualRow.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellStyle().getIndex())
                    .as("row %s column %s style", actualRowNumber, columnName(columnIndex))
                    .isEqualTo(templateRow.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getCellStyle().getIndex());
        }
    }

    private SalesOrderItem item(Long id, Long salesOrderId, Integer lineNo, String matchStatus) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        item.setMatchStatus(matchStatus);
        item.setQty(1);
        item.setBaseCost(BigDecimal.ONE);
        return item;
    }

    private String text(Sheet sheet, int oneBasedRow, String column) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null) {
            return "";
        }
        var cell = row.getCell(columnIndex(column));
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK, _NONE, ERROR -> "";
        };
    }

    private String visibleText(Sheet sheet, int oneBasedRow, String column) {
        Row row = sheet.getRow(oneBasedRow - 1);
        if (row == null || row.getZeroHeight()) {
            return "";
        }
        return text(sheet, oneBasedRow, column);
    }

    private int columnIndex(String column) {
        int result = 0;
        for (int i = 0; i < column.length(); i++) {
            result = result * 26 + (column.charAt(i) - 'A' + 1);
        }
        return result - 1;
    }

    private int columnWidth(Sheet sheet, String firstColumn, String lastColumn) {
        int total = 0;
        for (int index = columnIndex(firstColumn); index <= columnIndex(lastColumn); index++) {
            total += sheet.getColumnWidth(index);
        }
        return total;
    }

    private String columnName(int zeroBasedColumnIndex) {
        int value = zeroBasedColumnIndex + 1;
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }

    private boolean hasMergedRegion(Sheet sheet, String range) {
        CellRangeAddress expected = CellRangeAddress.valueOf(range);
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            if (sheet.getMergedRegion(i).formatAsString().equals(expected.formatAsString())) {
                return true;
            }
        }
        return false;
    }

    private record Fixture(
            SalesOrderProductionTaskNoticeExportService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            DataScopeService dataScopeService,
            AuditService auditService) {
    }
}
