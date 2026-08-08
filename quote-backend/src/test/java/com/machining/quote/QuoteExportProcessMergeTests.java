package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.ExcelExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-export-process-merge/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-export-process-merge/backups/"
})
@Transactional
class QuoteExportProcessMergeTests {
    private final DataFormatter formatter = new DataFormatter();

    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ExcelExportService excelExportService;

    @Test
    void detailQuoteExportMergesByStableProcessIdentityAndKeepsProcessCode() throws Exception {
        OrderRfq order = createQuotedOrder();
        createQuotedItem(order.getId());

        MockHttpServletResponse response = new MockHttpServletResponse();
        excelExportService.exportQuoteSheet(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(rowContains(sheet, 0, "第1道工艺")).isFalse();
            int packagingCol = groupHeaderColumn(sheet, "PACKAGING / 精细包装");
            int surfaceCol = groupHeaderColumn(sheet, "SURFACE / 精细包装");
            assertThat(text(sheet, 1, packagingCol)).isEqualTo("大类编码");
            assertThat(text(sheet, 1, packagingCol + 1)).isEqualTo("大类名称");
            assertThat(text(sheet, 1, packagingCol + 2)).isEqualTo("具体工艺");
            assertThat(text(sheet, 1, packagingCol + 3)).isEqualTo("工时");
            assertThat(text(sheet, 1, packagingCol + 4)).isEqualTo("小时价格");
            assertThat(text(sheet, 1, packagingCol + 5)).isEqualTo("金额");

            assertThat(text(sheet, 2, packagingCol)).isEqualTo("PACKAGING");
            assertThat(text(sheet, 2, packagingCol + 1)).isEqualTo("包装");
            assertThat(text(sheet, 2, packagingCol + 2)).isEqualTo("精细包装");
            assertThat(numeric(sheet, 2, packagingCol + 3)).isEqualTo(4.0);
            assertThat(numeric(sheet, 2, packagingCol + 4)).isEqualTo(77.5);
            assertThat(numeric(sheet, 2, packagingCol + 5)).isEqualTo(310.0);

            assertThat(text(sheet, 2, surfaceCol)).isEqualTo("SURFACE");
            assertThat(text(sheet, 2, surfaceCol + 1)).isEqualTo("表面处理");
            assertThat(text(sheet, 2, surfaceCol + 2)).isEqualTo("精细包装");
            assertThat(numeric(sheet, 2, surfaceCol + 3)).isEqualTo(2.0);
            assertThat(numeric(sheet, 2, surfaceCol + 4)).isEqualTo(50.0);
            assertThat(numeric(sheet, 2, surfaceCol + 5)).isEqualTo(100.0);
        }
    }

    private OrderRfq createQuotedOrder() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("RFQ-MERGE-" + System.nanoTime());
        order.setCustomerName("合并测试客户");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.insert(order);
        return order;
    }

    private void createQuotedItem(Integer rfqId) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(rfqId);
        item.setPartNo("P-MERGE-001");
        item.setDescription("重复工艺测试件");
        item.setVersion("A");
        item.setQty(1);
        item.setUnit("件");
        item.setDrawingUrl("/uploads/test/P-MERGE-001.pdf");
        item.setMaterialName("45#钢");
        item.setSurfaceTreatment("阳极");
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-04-01");
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        item.setMaterialData(Map.of("weight", 1.0, "cost", 20.0));
        item.setProcessRoute(List.of(
                route(7L, "PACKAGING", "包装", "精细包装", 1.0, 70.0, 70.0, 10),
                route(8L, "CNC", "加工", "CNC", 2.0, 100.0, 200.0, 20),
                route(7L, "PACKAGING", "包装", "精细包装", 3.0, 80.0, 240.0, 30),
                route(9L, "SURFACE", "表面处理", "精细包装", 2.0, 50.0, 100.0, 40)
        ));
        item.setFeeData(List.of());
        item.setBaseCost(530.0);
        item.setTaxIncludedUnitPrice(530.0);
        item.setTaxIncludedTotalPrice(530.0);
        itemMapper.insert(item);
    }

    private Map<String, Object> route(Long processDetailId, String processCode, String categoryName, String detailName,
                                      double unitWorkHours, double hourlyRate, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processDetailId", processDetailId);
        route.put("processCode", processCode);
        route.put("categoryName", categoryName);
        route.put("detailName", detailName);
        route.put("processName", detailName);
        route.put("unitWorkHours", unitWorkHours);
        route.put("hourlyRate", hourlyRate);
        route.put("lineCost", lineCost);
        route.put("sortNo", sortNo);
        return route;
    }

    private int groupHeaderColumn(Sheet sheet, String expected) {
        Row row = sheet.getRow(0);
        for (Cell cell : row) {
            if (expected.equals(formatter.formatCellValue(cell))) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("Missing group header: " + expected);
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

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(colIndex));
    }

    private double numeric(Sheet sheet, int rowIndex, int colIndex) {
        return sheet.getRow(rowIndex).getCell(colIndex).getNumericCellValue();
    }
}
