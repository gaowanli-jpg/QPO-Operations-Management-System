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
    void detailQuoteExportMergesRepeatedProcessesUnderProcessNameHeaders() throws Exception {
        OrderRfq order = createQuotedOrder();
        createQuotedItem(order.getId());

        MockHttpServletResponse response = new MockHttpServletResponse();
        excelExportService.exportQuoteSheet(order.getId(), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(rowContains(sheet, 0, "第1道工艺")).isFalse();
            int millCol = groupHeaderColumn(sheet, "铣");
            assertThat(text(sheet, 1, millCol)).isEqualTo("类型");
            assertThat(text(sheet, 1, millCol + 1)).isEqualTo("工时/金额");
            assertThat(text(sheet, 1, millCol + 2)).isEqualTo("单价");
            assertThat(text(sheet, 1, millCol + 3)).isEqualTo("费用");

            assertThat(text(sheet, 2, millCol)).isEqualTo("工时");
            assertThat(numeric(sheet, 2, millCol + 1)).isEqualTo(4.0);
            assertThat(numeric(sheet, 2, millCol + 2)).isEqualTo(77.5);
            assertThat(numeric(sheet, 2, millCol + 3)).isEqualTo(310.0);
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
                route("铣", "工时", 1.0, 70.0, 70.0, 10),
                route("CNC", "工时", 2.0, 100.0, 200.0, 20),
                route("铣", "工时", 3.0, 80.0, 240.0, 30)
        ));
        item.setFeeData(List.of());
        item.setBaseCost(530.0);
        item.setFinalUnitPrice(530.0);
        item.setFinalTotalPrice(530.0);
        itemMapper.insert(item);
    }

    private Map<String, Object> route(String processName, String calcType, double inputVal,
                                      double unitRate, double lineCost, int sortNo) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processName", processName);
        route.put("calcType", calcType);
        route.put("inputVal", inputVal);
        route.put("unitRate", unitRate);
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
