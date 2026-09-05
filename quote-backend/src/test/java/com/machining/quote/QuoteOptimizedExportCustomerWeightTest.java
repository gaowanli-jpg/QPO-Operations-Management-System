package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.QuoteProcessService;
import com.machining.quote.service.QuoteTemplateExportService;
import com.machining.quote.service.QuoteTemplateService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteOptimizedExportCustomerWeightTest {
    private static final int RFQ_ID = 1801;
    private final DataFormatter formatter = new DataFormatter();

    @Test
    void detailQuoteExportKeepsCustomerWeightWhileOptimizedExportHidesAndRecalculatesIt() throws Exception {
        ExportFixture fixture = excelFixture(quotedNormalItem());

        MockHttpServletResponse normalResponse = new MockHttpServletResponse();
        fixture.excelExportService.exportQuoteSheet(RFQ_ID, normalResponse);

        MockHttpServletResponse optimizedResponse = new MockHttpServletResponse();
        fixture.excelExportService.exportOptimizedQuoteSheet(RFQ_ID, optimizedResponse);

        try (Workbook normalWorkbook = workbook(normalResponse);
             Workbook optimizedWorkbook = workbook(optimizedResponse)) {
            Sheet normalSheet = normalWorkbook.getSheetAt(0);
            Sheet optimizedSheet = optimizedWorkbook.getSheetAt(0);

            assertThat(sheetText(normalSheet))
                    .contains("客户权重费率(%)", "客户权重金额");
            assertThat(sheetText(optimizedSheet))
                    .doesNotContain("客户权重费率(%)")
                    .doesNotContain("客户权重金额")
                    .contains("运输费率(%)", "税费率(%)");

            int processStartCol = groupHeaderColumn(optimizedSheet, 0, "下料");
            assertThat(text(optimizedSheet, 2, processStartCol)).isEqualTo("工时");
            assertThat(number(optimizedSheet, 2, processStartCol + 1)).isEqualTo(1.4);
            assertThat(number(optimizedSheet, 2, processStartCol + 3)).isEqualTo(14.0);
            assertThat(number(optimizedSheet, 2, headerColumn(optimizedSheet, 1, "基础成本"))).isEqualTo(44.0);
            assertThat(number(optimizedSheet, 2, headerColumn(optimizedSheet, 1, "百分比费用合计"))).isCloseTo(10.692, offset(0.000001));
            assertThat(number(optimizedSheet, 2, headerColumn(optimizedSheet, 1, "含税单价"))).isCloseTo(54.692, offset(0.000001));
        }
    }

    @Test
    void optimizedCustomerTemplateDetailAlsoHidesCustomerWeightColumnsAndUsesAdjustedRows() throws Exception {
        ItemQuoteVersion item = quotedNormalItem();
        QuoteTemplateExportService service = new QuoteTemplateExportService();
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        QuoteProcessService quoteProcessService = mock(QuoteProcessService.class);
        QuoteTemplateService quoteTemplateService = mock(QuoteTemplateService.class);

        when(orderRfqMapper.selectById(RFQ_ID)).thenReturn(order());
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(quoteTemplateService.openTemplateWorkbook()).thenReturn(templateWorkbook());
        when(quoteTemplateService.templateLayout()).thenReturn(new QuoteTemplateService.TemplateLayout(8, 6, 7, 8));

        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "quoteProcessService", quoteProcessService);
        ReflectionTestUtils.setField(service, "quoteTemplateService", quoteTemplateService);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportOptimizedTemplateQuoteDetail(RFQ_ID, response);

        try (Workbook workbook = workbook(response)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheetText(sheet))
                    .doesNotContain("客户权重费率(%)")
                    .doesNotContain("客户权重金额")
                    .contains("运输费率(%)", "税费率(%)");

            int processStartCol = groupHeaderColumn(sheet, 6, "下料");
            assertThat(text(sheet, 8, processStartCol)).isEqualTo("工时");
            assertThat(number(sheet, 8, processStartCol + 1)).isEqualTo(1.4);
            assertThat(number(sheet, 8, processStartCol + 3)).isEqualTo(14.0);
            assertThat(number(sheet, 8, headerColumn(sheet, 7, "基础成本"))).isEqualTo(44.0);
            assertThat(number(sheet, 8, headerColumn(sheet, 7, "含税单价"))).isCloseTo(54.692, offset(0.000001));
        }
    }

    @Test
    void customerTemplateDetailRebuildsKnownSystemDetailTableWhenTemplateMiddleIsNotCanonical() throws Exception {
        ItemQuoteVersion item = quotedNormalItem();
        item.setFeeData(List.of(
                fee("运输", "BASIC_PERCENT", 10.0, 4.4),
                fee("税", "TAX", 13.0, 6.292)
        ));
        QuoteTemplateExportService service = new QuoteTemplateExportService();
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        QuoteProcessService quoteProcessService = mock(QuoteProcessService.class);
        QuoteTemplateService quoteTemplateService = mock(QuoteTemplateService.class);

        when(orderRfqMapper.selectById(RFQ_ID)).thenReturn(order());
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(quoteTemplateService.openTemplateWorkbook()).thenReturn(nonCanonicalMiddleTemplateWorkbook());
        when(quoteTemplateService.templateLayout()).thenReturn(new QuoteTemplateService.TemplateLayout(8, 6, 7, 8));

        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "quoteProcessService", quoteProcessService);
        ReflectionTestUtils.setField(service, "quoteTemplateService", quoteTemplateService);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportTemplateQuoteDetail(RFQ_ID, response);

        try (Workbook workbook = workbook(response)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 0, 0)).isEqualTo("客户报价单模板");
            assertThat(text(sheet, 2, 0)).isEqualTo("询价日：");
            assertThat(text(sheet, 9, 0)).isEqualTo("QUOTATION VALIDITY");

            assertThat(text(sheet, 6, 0)).isEqualTo("基础信息");
            assertThat(text(sheet, 6, 9)).isEqualTo("材料费");
            assertThat(text(sheet, 7, 0)).isEqualTo("序号");
            assertThat(text(sheet, 7, 1)).isEqualTo("图号");
            assertThat(text(sheet, 7, 2)).isEqualTo("物料描述");
            assertThat(text(sheet, 7, 3)).isEqualTo("图纸状态");
            assertThat(text(sheet, 7, 4)).isEqualTo("材料名称");
            assertThat(text(sheet, 7, 5)).isEqualTo("表面处理");
            assertThat(text(sheet, 7, 6)).isEqualTo("版本");
            assertThat(text(sheet, 7, 7)).isEqualTo("数量");
            assertThat(text(sheet, 7, 8)).isEqualTo("单位");
            assertThat(text(sheet, 7, 9)).isEqualTo("重量(KG)");
            assertThat(text(sheet, 7, 10)).isEqualTo("材料费");

            assertThat(sheetText(sheet))
                    .doesNotContain("模板旧工艺")
                    .doesNotContain("客户权重费率(%)")
                    .doesNotContain("客户权重金额")
                    .doesNotContain("材料编码")
                    .doesNotContain("材料类型")
                    .doesNotContain("材质分类");
            assertThat(text(sheet, 8, 1)).isEqualTo("P-OPT-001");
            assertThat(text(sheet, 8, 5)).isEqualTo("阳极");
            assertThat(text(sheet, 8, 9)).isEqualTo("1");
            assertThat(text(sheet, 8, 10)).isEqualTo("30");
            int lastHeaderCol = headerColumn(sheet, 7, "备注");
            assertNoHeaderCellsAfter(sheet, 6, lastHeaderCol);
            assertNoHeaderCellsAfter(sheet, 7, lastHeaderCol);
            assertHeaderStyleMatches(sheet, 6, 0, lastHeaderCol);
            assertHeaderStyleMatches(sheet, 7, 0, lastHeaderCol);
        }
    }

    @Test
    void customerTemplateSimpleRebuildsKnownSystemSimpleTableWhenTemplateMiddleIsNotCanonical() throws Exception {
        ItemQuoteVersion item = quotedNormalItem();
        QuoteTemplateExportService service = new QuoteTemplateExportService();
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        QuoteProcessService quoteProcessService = mock(QuoteProcessService.class);
        QuoteTemplateService quoteTemplateService = mock(QuoteTemplateService.class);

        when(orderRfqMapper.selectById(RFQ_ID)).thenReturn(order());
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(quoteTemplateService.openTemplateWorkbook()).thenReturn(nonCanonicalMiddleTemplateWorkbook());
        when(quoteTemplateService.templateLayout()).thenReturn(new QuoteTemplateService.TemplateLayout(8, 6, 7, 8));

        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "quoteProcessService", quoteProcessService);
        ReflectionTestUtils.setField(service, "quoteTemplateService", quoteTemplateService);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportTemplateQuoteSimple(RFQ_ID, response);

        try (Workbook workbook = workbook(response)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 0, 0)).isEqualTo("客户报价单模板");
            assertThat(text(sheet, 2, 0)).isEqualTo("询价日：");
            assertThat(text(sheet, 9, 0)).isEqualTo("QUOTATION VALIDITY");

            assertThat(text(sheet, 6, 0)).isEqualTo("基础信息");
            assertThat(text(sheet, 6, 12)).isEqualTo("合计");
            assertThat(text(sheet, 7, 0)).isEqualTo("序号");
            assertThat(text(sheet, 7, 1)).isEqualTo("图号");
            assertThat(text(sheet, 7, 2)).isEqualTo("物料描述");
            assertThat(text(sheet, 7, 5)).isEqualTo("材料编码");
            assertThat(text(sheet, 7, 12)).isEqualTo("含税单价");
            assertThat(text(sheet, 7, 15)).isEqualTo("备注");
            int lastHeaderCol = headerColumn(sheet, 7, "备注");
            assertNoHeaderCellsAfter(sheet, 6, lastHeaderCol);
            assertNoHeaderCellsAfter(sheet, 7, lastHeaderCol);
            assertThat(sheetText(sheet)).doesNotContain("模板旧工艺");
            assertThat(text(sheet, 8, 1)).isEqualTo("P-OPT-001");
            assertThat(text(sheet, 8, 5)).isEmpty();
            assertThat(text(sheet, 8, 12)).isEqualTo("54.692");
        }
    }

    @Test
    void customerTemplateDetailKeepsCustomerWeightFeeColumnsSameWidthAsOtherPercentageFees() throws Exception {
        ItemQuoteVersion item = quotedNormalItem();
        QuoteTemplateExportService service = new QuoteTemplateExportService();
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        QuoteProcessService quoteProcessService = mock(QuoteProcessService.class);
        QuoteTemplateService quoteTemplateService = mock(QuoteTemplateService.class);

        when(orderRfqMapper.selectById(RFQ_ID)).thenReturn(order());
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(quoteTemplateService.openTemplateWorkbook()).thenReturn(nonCanonicalMiddleTemplateWorkbook());
        when(quoteTemplateService.templateLayout()).thenReturn(new QuoteTemplateService.TemplateLayout(8, 6, 7, 8));

        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "quoteProcessService", quoteProcessService);
        ReflectionTestUtils.setField(service, "quoteTemplateService", quoteTemplateService);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportTemplateQuoteDetail(RFQ_ID, response);

        try (Workbook workbook = workbook(response)) {
            Sheet sheet = workbook.getSheetAt(0);
            int customerWeightRateCol = headerColumn(sheet, 7, "客户权重费率(%)");
            int customerWeightAmountCol = headerColumn(sheet, 7, "客户权重金额");
            int transportRateCol = headerColumn(sheet, 7, "运输费率(%)");
            int transportAmountCol = headerColumn(sheet, 7, "运输金额");

            assertThat(sheet.getColumnWidth(customerWeightRateCol)).isEqualTo(sheet.getColumnWidth(transportRateCol));
            assertThat(sheet.getColumnWidth(customerWeightAmountCol)).isEqualTo(sheet.getColumnWidth(transportAmountCol));
        }
    }

    private ExportFixture excelFixture(ItemQuoteVersion item) {
        ExcelExportService service = new ExcelExportService();
        OrderRfqMapper orderRfqMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        QuoteProcessService quoteProcessService = mock(QuoteProcessService.class);

        when(orderRfqMapper.selectById(RFQ_ID)).thenReturn(order());
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(item));

        ReflectionTestUtils.setField(service, "orderRfqMapper", orderRfqMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "quoteProcessService", quoteProcessService);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        return new ExportFixture(service);
    }

    private ItemQuoteVersion quotedNormalItem() {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(RFQ_ID);
        item.setPartNo("P-OPT-001");
        item.setVersion("A");
        item.setDescription("优化导出测试件");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/P-OPT-001.pdf");
        item.setMaterialName("材料名称");
        item.setSurfaceTreatment("阳极");
        item.setQuoteLeadTime("2026-09-30");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setQuoteMode("NORMAL");
        item.setMaterialData(Map.of("cost", 30.0, "weight", 1.0));
        item.setProcessRoute(List.of(route("下料", "工时", 1.0, 10.0, 10.0, 10)));
        item.setFeeData(List.of(
                fee("客户权重", "CUSTOMER_WEIGHT", 10.0, 4.0),
                fee("运输", "BASIC_PERCENT", 10.0, 4.4),
                fee("税", "TAX", 13.0, 6.292)
        ));
        item.setBaseCost(40.0);
        item.setUntaxedUnitPrice(48.4);
        item.setTaxIncludedUnitPrice(54.692);
        item.setTaxIncludedTotalPrice(54.692);
        return item;
    }

    private OrderRfq order() {
        OrderRfq order = new OrderRfq();
        order.setId(RFQ_ID);
        order.setRfqNo("RFQ-OPT-001");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private Map<String, Object> route(String processName, String calcType, double inputVal, double unitRate, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processName", processName);
        route.put("calcType", calcType);
        route.put("inputVal", inputVal);
        route.put("unitWorkHours", inputVal);
        route.put("unitRate", unitRate);
        route.put("hourlyRate", unitRate);
        route.put("lineCost", lineCost);
        route.put("sortNo", sortNo);
        return route;
    }

    private Map<String, Object> fee(String feeName, String feeCategory, double ratePercent, double cost) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", feeName);
        fee.put("feeCategory", feeCategory);
        fee.put("ratePercent", ratePercent);
        fee.put("cost", cost);
        return fee;
    }

    private Workbook templateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("报价单");
        sheet.createRow(0).createCell(0).setCellValue("客户报价单模板");
        Row groupRow = sheet.createRow(6);
        Row headerRow = sheet.createRow(7);
        List<String> headers = new ArrayList<>(List.of(
                "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                "重量(KG)", "材料费",
                "类型", "工时/金额", "单价", "费用",
                "客户权重费率(%)", "客户权重金额",
                "运输费率(%)", "运输金额",
                "税费率(%)", "税金额",
                "基础成本", "百分比费用合计", "含税单价", "含税总价", "交货周期", "备注"
        ));
        for (int col = 0; col < headers.size(); col++) {
            groupRow.createCell(col).setCellValue("");
            headerRow.createCell(col).setCellValue(headers.get(col));
            sheet.setColumnWidth(col, 12 * 256);
        }
        groupRow.getCell(0).setCellValue("基础信息");
        groupRow.getCell(11).setCellValue("加工明细");
        groupRow.getCell(15).setCellValue("百分比费用");
        groupRow.getCell(21).setCellValue("合计");
        sheet.createRow(8).createCell(0).setCellValue(1);
        sheet.createRow(9).createCell(0).setCellValue("QUOTATION VALIDITY");
        return workbook;
    }

    private Workbook nonCanonicalMiddleTemplateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("报价单");
        sheet.createRow(0).createCell(0).setCellValue("客户报价单模板");
        Row infoRow = sheet.createRow(2);
        infoRow.createCell(0).setCellValue("询价日：");
        infoRow.createCell(8).setCellValue("RFQ No.");

        Row groupRow = sheet.createRow(6);
        Row headerRow = sheet.createRow(7);
        CellStyle headerStyle = headerStyle(workbook);
        List<String> headers = List.of(
                "模板旧序号", "模板旧图号", "模板旧列", "客户权重费率(%)", "客户权重金额",
                "含税单价", "含税总价", "交货周期", "备注"
        );
        for (int col = 0; col < headers.size(); col++) {
            Cell groupCell = groupRow.createCell(col);
            groupCell.setCellValue("");
            groupCell.setCellStyle(headerStyle);
            Cell headerCell = headerRow.createCell(col);
            headerCell.setCellValue(headers.get(col));
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(col, 12 * 256);
        }
        for (int col = headers.size(); col < 32; col++) {
            Cell groupCell = groupRow.createCell(col);
            groupCell.setCellStyle(headerStyle);
            Cell headerCell = headerRow.createCell(col);
            headerCell.setCellStyle(headerStyle);
            sheet.setColumnWidth(col, 12 * 256);
        }
        sheet.setColumnWidth(15, 22 * 256);
        sheet.setColumnWidth(16, 22 * 256);
        sheet.setColumnWidth(17, 12 * 256);
        sheet.setColumnWidth(18, 12 * 256);
        groupRow.getCell(0).setCellValue("模板旧基础信息");
        groupRow.getCell(2).setCellValue("模板旧工艺");
        groupRow.getCell(3).setCellValue("模板旧百分比费用");
        groupRow.getCell(5).setCellValue("模板旧合计");
        sheet.createRow(8).createCell(0).setCellValue(1);
        sheet.createRow(9).createCell(0).setCellValue("QUOTATION VALIDITY");
        return workbook;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void assertHeaderStyleMatches(Sheet sheet, int rowIndex, int sampleCol, int lastCol) {
        CellStyle expected = sheet.getRow(rowIndex).getCell(sampleCol).getCellStyle();
        Font expectedFont = sheet.getWorkbook().getFontAt(expected.getFontIndexAsInt());
        for (int col = sampleCol; col <= lastCol; col++) {
            CellStyle actual = sheet.getRow(rowIndex).getCell(col).getCellStyle();
            Font actualFont = sheet.getWorkbook().getFontAt(actual.getFontIndexAsInt());
            assertThat(actual.getFillPattern()).as("row %s col %s fill pattern", rowIndex, col)
                    .isEqualTo(expected.getFillPattern());
            assertThat(actual.getFillForegroundColor()).as("row %s col %s fill", rowIndex, col)
                    .isEqualTo(expected.getFillForegroundColor());
            assertThat(actualFont.getBold()).as("row %s col %s bold", rowIndex, col)
                    .isEqualTo(expectedFont.getBold());
        }
    }

    private void assertNoHeaderCellsAfter(Sheet sheet, int rowIndex, int lastCol) {
        Row row = sheet.getRow(rowIndex);
        assertThat(row.getLastCellNum()).as("row %s last cell", rowIndex).isEqualTo((short) (lastCol + 1));
    }

    private Workbook workbook(MockHttpServletResponse response) throws Exception {
        return WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()));
    }

    private String sheetText(Sheet sheet) {
        StringBuilder text = new StringBuilder();
        for (Row row : sheet) {
            for (Cell cell : row) {
                text.append(formatter.formatCellValue(cell)).append('\n');
            }
        }
        return text.toString();
    }

    private int groupHeaderColumn(Sheet sheet, int rowIndex, String expected) {
        Row row = sheet.getRow(rowIndex);
        for (Cell cell : row) {
            if (expected.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing group header: " + expected);
    }

    private int headerColumn(Sheet sheet, int rowIndex, String expected) {
        Row row = sheet.getRow(rowIndex);
        for (Cell cell : row) {
            if (expected.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing header: " + expected);
    }

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        return formatter.formatCellValue(sheet.getRow(rowIndex).getCell(colIndex));
    }

    private double number(Sheet sheet, int rowIndex, int colIndex) {
        return Double.parseDouble(text(sheet, rowIndex, colIndex));
    }

    private record ExportFixture(ExcelExportService excelExportService) {
    }
}
