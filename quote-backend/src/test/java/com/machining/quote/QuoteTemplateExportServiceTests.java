package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.QuoteTemplateExportService;
import com.machining.quote.service.QuoteTemplateService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-template-export-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-template-export-test/backups/"
})
@Transactional
class QuoteTemplateExportServiceTests {
    private static final int HEADER_ROW_INDEX = 7;
    private static final int DETAIL_START_ROW_INDEX = 8;
    private final DataFormatter formatter = new DataFormatter();

    @Autowired
    private QuoteTemplateService quoteTemplateService;
    @Autowired
    private QuoteTemplateExportService quoteTemplateExportService;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;

    @BeforeEach
    void writeTemplate() throws Exception {
        Files.createDirectories(quoteTemplateService.templatePath().getParent());
        try (Workbook workbook = templateWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            Files.write(quoteTemplateService.templatePath(), outputStream.toByteArray());
        }
    }

    @Test
    void detailTemplateExportWritesTopInfoRowsAndDynamicColumns() throws Exception {
        OrderRfq order = createOrder();
        createQuotedItems(order.getId(), 11);

        MockHttpServletResponse response = new MockHttpServletResponse();
        quoteTemplateExportService.exportTemplateQuoteDetail(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(rowContains(sheet, 2, order.getRfqNo())).isTrue();
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, 1)).isEqualTo("'=P-001");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX + 11, 0)).isEqualTo("QUOTATION VALIDITY");
            assertThat(rowContains(sheet, 6, "第1道工艺")).isFalse();
            assertThat(rowContains(sheet, 6, "装配")).isTrue();
            assertThat(rowContains(sheet, HEADER_ROW_INDEX, "加急费率(%)")).isTrue();
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, headerCol(sheet, "交货周期"))).isEqualTo("2026-04-01");
        }
    }

    @Test
    void simpleTemplateExportKeepsOnlyCustomerFacingColumns() throws Exception {
        OrderRfq order = createOrder();
        createQuotedItems(order.getId(), 11);

        MockHttpServletResponse response = new MockHttpServletResponse();
        quoteTemplateExportService.exportTemplateQuoteSimple(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 0, 0)).isEqualTo("客户报价单模板");
            assertThat(rowContains(sheet, 2, order.getRfqNo())).isTrue();
            assertThat(hasMergedRegion(sheet, 0, 0, 0, 12)).isTrue();
            assertThat(text(sheet, DETAIL_START_ROW_INDEX + 11, 0)).isEqualTo("QUOTATION VALIDITY");
            assertThat(hasMergedRegion(sheet, DETAIL_START_ROW_INDEX + 11, DETAIL_START_ROW_INDEX + 11, 0, 12)).isTrue();

            Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
            List<String> headers = new ArrayList<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                headers.add(text(sheet, HEADER_ROW_INDEX, col));
            }
            assertThat(headers).containsExactly(
                    "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                    "含税单价", "含税总价", "交货周期", "备注"
            );
            assertThat(headers).doesNotContain("重量(KG)", "材料费", "基础成本", "百分比费用合计");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, 1)).isEqualTo("'=P-001");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, 11)).isEqualTo("2026-04-01");
        }
    }

    @Test
    void detailTemplateExportRepairsLooseDetailHeaderLayout() throws Exception {
        try (Workbook workbook = looseDetailHeaderTemplateWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            Files.write(quoteTemplateService.templatePath(), outputStream.toByteArray());
        }
        OrderRfq order = createOrder();
        createQuotedItems(order.getId(), 1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        quoteTemplateExportService.exportTemplateQuoteDetail(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(groupHeaderCol(sheet, "下料")).isEqualTo(11);
            assertThat(text(sheet, HEADER_ROW_INDEX, 11)).isEqualTo("类型");
            assertThat(text(sheet, HEADER_ROW_INDEX, 12)).isEqualTo("工时/金额");
            assertThat(text(sheet, HEADER_ROW_INDEX, 13)).isEqualTo("单价");
            assertThat(rowContains(sheet, HEADER_ROW_INDEX, "运输费率(%)")).isTrue();
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, 11)).isEqualTo("工时");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, 12)).isEqualTo("1");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, headerCol(sheet, "基础成本"))).isEqualTo("70");
        }
    }

    @Test
    void detailTemplateExportMergesRepeatedProcessesUnderProcessNameHeaders() throws Exception {
        OrderRfq order = createOrder();
        createQuotedItem(order.getId(), List.of(
                route("铣", "工时", 1.0, 70.0, 70.0, 10),
                route("CNC", "工时", 2.0, 100.0, 200.0, 20),
                route("铣", "工时", 3.0, 80.0, 240.0, 30)
        ));

        MockHttpServletResponse response = new MockHttpServletResponse();
        quoteTemplateExportService.exportTemplateQuoteDetail(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(rowContains(sheet, 6, "第1道工艺")).isFalse();
            int millCol = groupHeaderCol(sheet, "铣");
            assertThat(text(sheet, HEADER_ROW_INDEX, millCol)).isEqualTo("类型");
            assertThat(text(sheet, HEADER_ROW_INDEX, millCol + 1)).isEqualTo("工时/金额");
            assertThat(text(sheet, HEADER_ROW_INDEX, millCol + 2)).isEqualTo("单价");
            assertThat(text(sheet, HEADER_ROW_INDEX, millCol + 3)).isEqualTo("费用");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, millCol)).isEqualTo("工时");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, millCol + 1)).isEqualTo("4");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, millCol + 2)).isEqualTo("77.5");
            assertThat(text(sheet, DETAIL_START_ROW_INDEX, millCol + 3)).isEqualTo("310");
        }
    }

    private OrderRfq createOrder() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("RFQ-TPL-" + System.nanoTime());
        order.setCustomerName("模板客户");
        order.setStatus("已报价");
        orderMapper.insert(order);
        return order;
    }

    private void createQuotedItems(Integer rfqId, int count) {
        for (int i = 1; i <= count; i++) {
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setRfqId(rfqId);
            item.setPartNo(i == 1 ? "=P-001" : "P-" + String.format("%03d", i));
            item.setVersion("A");
            item.setDescription("测试零件" + i);
            item.setQty(2);
            item.setUnit("件");
            item.setDrawingUrl("/uploads/test/P-" + i + ".pdf");
            item.setMaterialName("45#钢");
            item.setSurfaceTreatment("阳极");
            item.setLeadTime("2026-03-20");
            item.setQuoteLeadTime("2026-04-01");
            item.setStatus("已报价");
            item.setRemark("备注" + i);
            item.setMaterialData(Map.of("weight", 1.5, "cost", 20.0));
            item.setProcessRoute(processRoutes());
            item.setFeeData(feeRows());
            item.setBaseCost(70.0);
            item.setFinalUnitPrice(90.0);
            item.setFinalTotalPrice(180.0);
            itemMapper.insert(item);
        }
    }

    private void createQuotedItem(Integer rfqId, List<Map<String, Object>> processRoutes) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(rfqId);
        item.setPartNo("P-MERGE-TPL");
        item.setVersion("A");
        item.setDescription("模板重复工艺测试件");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/test/P-MERGE-TPL.pdf");
        item.setMaterialName("45#钢");
        item.setSurfaceTreatment("阳极");
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-04-01");
        item.setStatus("已报价");
        item.setRemark("重复工艺");
        item.setMaterialData(Map.of("weight", 1.0, "cost", 20.0));
        item.setProcessRoute(processRoutes);
        item.setFeeData(List.of());
        item.setBaseCost(530.0);
        item.setFinalUnitPrice(530.0);
        item.setFinalTotalPrice(530.0);
        itemMapper.insert(item);
    }

    private List<Map<String, Object>> processRoutes() {
        return List.of(
                route("下料", "工时", 1.0, 10.0, 10.0, 10),
                route("CNC", "工时", 2.0, 15.0, 30.0, 20),
                route("表处", "非工时费用", 5.0, 1.0, 5.0, 30),
                route("装配", "工时", 1.0, 25.0, 25.0, 40)
        );
    }

    private Map<String, Object> route(String processName, String calcType, double inputVal, double unitRate, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processName", processName);
        route.put("calcType", calcType);
        route.put("inputVal", inputVal);
        route.put("unitRate", unitRate);
        route.put("lineCost", lineCost);
        route.put("sortNo", sortNo);
        return route;
    }

    private List<Map<String, Object>> feeRows() {
        return List.of(
                fee("运输", 1.0, 1.0),
                fee("管理", 2.0, 2.0),
                fee("利润", 3.0, 3.0),
                fee("税", 4.0, 4.0),
                fee("加急", 5.0, 5.0)
        );
    }

    private Map<String, Object> fee(String feeName, double ratePercent, double cost) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", feeName);
        fee.put("ratePercent", ratePercent);
        fee.put("cost", cost);
        return fee;
    }

    private Workbook templateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("第2份");
        sheet.createRow(0).createCell(0).setCellValue("客户报价单模板");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 39));
        Row infoRow = sheet.createRow(2);
        infoRow.createCell(0).setCellValue("询价日：");
        infoRow.createCell(8).setCellValue("RFQ No.");

        Row groupRow = sheet.createRow(6);
        Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
        List<String> headers = new ArrayList<>(List.of(
                "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                "重量(KG)", "材料费"
        ));
        for (int i = 1; i <= 3; i++) {
            headers.addAll(List.of("工艺名称", "类型", "工时/金额", "单价", "费用"));
        }
        headers.addAll(List.of(
                "运输费率（％）", "运输金额",
                "管理费率(%)", "管理金额",
                "利润费率(%)", "利润金额",
                "税费率(%)", "税金额",
                "基础成本", "百分比费用合计", "含税单价", "含税总价", "交货周期", "备注"
        ));
        for (int col = 0; col < headers.size(); col++) {
            headerRow.createCell(col).setCellValue(headers.get(col));
            groupRow.createCell(col).setCellValue("");
            sheet.setColumnWidth(col, 12 * 256);
        }
        groupRow.getCell(0).setCellValue("基础信息");
        groupRow.getCell(11).setCellValue("第1道工艺");
        groupRow.getCell(16).setCellValue("第2道工艺");
        groupRow.getCell(21).setCellValue("第3道工艺");
        groupRow.getCell(26).setCellValue("百分比费用");
        groupRow.getCell(34).setCellValue("合计");

        for (int rowIndex = DETAIL_START_ROW_INDEX; rowIndex < 18; rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(rowIndex - DETAIL_START_ROW_INDEX + 1);
        }
        sheet.createRow(18).createCell(0).setCellValue("QUOTATION VALIDITY");
        sheet.addMergedRegion(new CellRangeAddress(18, 18, 0, 39));
        return workbook;
    }

    private Workbook looseDetailHeaderTemplateWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("第2份");
        sheet.createRow(0).createCell(0).setCellValue("客户报价单模板");
        Row infoRow = sheet.createRow(2);
        infoRow.createCell(0).setCellValue("询价日：");
        infoRow.createCell(8).setCellValue("RFQ No.");

        Row groupRow = sheet.createRow(6);
        Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
        List<String> headers = new ArrayList<>(List.of(
                "序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位",
                "重量(KG)", "材料费",
                "工艺名称", "工序类型", "工时", "加工单价", "加工费用",
                "运输费率(%)", "运输金额",
                "基础成本", "百分比费用合计", "含税单价", "含税总价", "交货周期", "备注"
        ));
        for (int col = 0; col < headers.size(); col++) {
            headerRow.createCell(col).setCellValue(headers.get(col));
            groupRow.createCell(col).setCellValue("");
            sheet.setColumnWidth(col, 12 * 256);
        }
        groupRow.getCell(0).setCellValue("基础信息");
        groupRow.getCell(11).setCellValue("加工明细");
        groupRow.getCell(16).setCellValue("百分比费用");
        groupRow.getCell(18).setCellValue("合计");

        Row row = sheet.createRow(DETAIL_START_ROW_INDEX);
        row.createCell(0).setCellValue(1);
        sheet.createRow(DETAIL_START_ROW_INDEX + 1).createCell(0).setCellValue("QUOTATION VALIDITY");
        return workbook;
    }

    private boolean rowContains(Sheet sheet, int rowIndex, String expected) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return false;
        }
        for (Cell cell : row) {
            if (formatter.formatCellValue(cell).contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMergedRegion(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == firstRow
                    && region.getLastRow() == lastRow
                    && region.getFirstColumn() == firstCol
                    && region.getLastColumn() == lastCol) {
                return true;
            }
        }
        return false;
    }

    private int headerCol(Sheet sheet, String header) {
        Row row = sheet.getRow(HEADER_ROW_INDEX);
        for (Cell cell : row) {
            if (header.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing header: " + header);
    }

    private int groupHeaderCol(Sheet sheet, String header) {
        Row row = sheet.getRow(6);
        for (Cell cell : row) {
            if (header.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing group header: " + header);
    }

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(colIndex));
    }
}
