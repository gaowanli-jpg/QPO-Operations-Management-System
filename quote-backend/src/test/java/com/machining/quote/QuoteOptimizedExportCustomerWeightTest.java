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
import org.apache.poi.ss.usermodel.DataFormatter;
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
