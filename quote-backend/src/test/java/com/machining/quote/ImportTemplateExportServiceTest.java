package com.machining.quote;

import com.machining.quote.service.ImportTemplateExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportTemplateExportServiceTest {
    private static final Path MARKET_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "MarketController.java");
    private static final Path SALES_ORDER_CONTROLLER = Path.of(
            "src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderImportController.java");

    private final ImportTemplateExportService service = new ImportTemplateExportService();

    @Test
    void templateDownloadEndpointsReuseImportPermissions() throws Exception {
        String marketController = Files.readString(MARKET_CONTROLLER, StandardCharsets.UTF_8);
        String salesOrderController = Files.readString(SALES_ORDER_CONTROLLER, StandardCharsets.UTF_8);

        assertThat(marketController)
                .contains("@GetMapping(\"/import-rfq/template\")")
                .contains("@RequirePermission(\"market:rfq:import\")");
        assertThat(salesOrderController)
                .contains("@GetMapping(\"/import/template\")")
                .contains("@RequirePermission(\"market:rfq:import-sales-order\")");
    }

    @Test
    void rfqTemplateUsesCurrentImportHeadersAndMarksRequiredColumns() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportRfqImportTemplate(response);

        String encodedName = URLEncoder.encode("客户询价表导入模板", StandardCharsets.UTF_8).replace("+", "%20");
        assertThat(response.getHeader("Content-Disposition")).contains(encodedName);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet dataSheet = workbook.getSheet("询价单导入");
            Sheet helpSheet = workbook.getSheet("字段说明");

            assertThat(headers(dataSheet.getRow(0))).containsExactly(
                    "图号", "物料描述", "材料名称", "表面处理", "版本", "数量", "单位", "交货周期", "备注"
            );
            assertThat(styleAt(dataSheet, 0).getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(styleAt(dataSheet, 0).getFillForegroundColor()).isEqualTo(styleAt(dataSheet, 5).getFillForegroundColor());
            assertThat(styleAt(dataSheet, 0).getFillForegroundColor()).isNotEqualTo(styleAt(dataSheet, 1).getFillForegroundColor());
            assertThat(helpSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("必填");
            assertThat(helpSheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("选填");
        }
    }

    @Test
    void salesOrderTemplateUsesParserHeadersAndMarksRequiredColumns() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportSalesOrderImportTemplate(response);

        String encodedName = URLEncoder.encode("销售订单导入模板", StandardCharsets.UTF_8).replace("+", "%20");
        assertThat(response.getHeader("Content-Disposition")).contains(encodedName);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet dataSheet = workbook.getSheet("销售订单导入");
            Sheet helpSheet = workbook.getSheet("字段说明");

            assertThat(headers(dataSheet.getRow(0))).containsExactly(
                    "序号", "下单日期", "客户订单号", "客户项目号", "客户下单抬头", "订单类型", "料号",
                    "内部项目号", "客户交期", "内部交期(给生产的交期)", "图号", "订单数量", "已出数量",
                    "未出数量", "描述", "材料", "表面处理", "版本", "VAT单价-含税", "未税单价(CNY)",
                    "（未税）金额", "实际出货日期", "备注"
            );
            assertThat(styleAt(dataSheet, 0).getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(styleAt(dataSheet, 1).getFillForegroundColor()).isEqualTo(styleAt(dataSheet, 19).getFillForegroundColor());
            assertThat(styleAt(dataSheet, 0).getFillForegroundColor()).isNotEqualTo(styleAt(dataSheet, 1).getFillForegroundColor());
            assertThat(helpSheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("必填");
            assertThat(helpSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("选填");
        }
    }

    private List<String> headers(Row row) {
        return java.util.stream.StreamSupport.stream(row.spliterator(), false)
                .map(Cell::getStringCellValue)
                .toList();
    }

    private CellStyle styleAt(Sheet sheet, int column) {
        return sheet.getRow(0).getCell(column).getCellStyle();
    }
}
