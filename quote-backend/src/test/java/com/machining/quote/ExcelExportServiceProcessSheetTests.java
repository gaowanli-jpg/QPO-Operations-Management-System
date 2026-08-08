package com.machining.quote;

import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.LeadTimeService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceProcessSheetTests {
    private final DataFormatter formatter = new DataFormatter();

    @Test
    void processSheetExportWritesStandardProcessIdentityColumns() throws Exception {
        ExcelExportService service = new ExcelExportService();
        ProcessSheetDTO sheet = new ProcessSheetDTO();
        sheet.setInternalProjectNo("IP-100");
        sheet.setItemType("零件");
        sheet.setPartNo("P-100");
        sheet.setVersion("A");
        sheet.setDescription("测试件");
        sheet.setQty(2);
        sheet.setEngineeringMaterialName("45#钢");
        sheet.setMaterialGrossWeight(1.2);
        sheet.setPartNetWeight(1.0);
        sheet.setSurfaceTreatment("阳极");
        sheet.setDeliveryDate(LocalDate.of(2026, 8, 12));
        sheet.setProcessRoute(List.of(standardRoute()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.invokeMethod(service, "exportProcessSheets", 88, "RFQ-88", List.of(sheet), response);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet result = workbook.getSheetAt(0);
            assertThat(text(result, 0, 16)).isEqualTo("10");
            assertThat(text(result, 1, 16)).isEqualTo("大类编码");
            assertThat(text(result, 1, 17)).isEqualTo("大类名称");
            assertThat(text(result, 1, 18)).isEqualTo("具体工艺");
            assertThat(text(result, 1, 19)).isEqualTo("工时");
            assertThat(text(result, 1, 20)).isEqualTo("小时价格");
            assertThat(text(result, 1, 21)).isEqualTo("金额");
            assertThat(text(result, 2, 16)).isEqualTo("PACKAGING");
            assertThat(text(result, 2, 17)).isEqualTo("包装");
            assertThat(text(result, 2, 18)).isEqualTo("精细包装");
            assertThat(text(result, 2, 19)).isEqualTo("1.5");
            assertThat(text(result, 2, 20)).isEqualTo("10");
            assertThat(text(result, 2, 21)).isEqualTo("15");
        }
    }

    @Test
    void processSheetLeavesExtraFeeWorkHourBlank() {
        ExcelExportService service = new ExcelExportService();

        String extraFeeValue = ReflectionTestUtils.invokeMethod(service, "processSheetWorkHour", Map.of(
                "processName", "包装费",
                "calcType", "非工时费用",
                "inputVal", 200
        ));
        String workHourValue = ReflectionTestUtils.invokeMethod(service, "processSheetWorkHour", Map.of(
                "processName", "CNC",
                "calcType", "工时",
                "inputVal", 1.5
        ));

        assertThat(extraFeeValue).isEmpty();
        assertThat(workHourValue).isEqualTo("1.5");
    }

    @Test
    @SuppressWarnings("unchecked")
    void simpleQuoteSheetKeepsOnlyCustomerFacingColumns() {
        ExcelExportService service = new ExcelExportService();
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());

        List<List<String>> head = ReflectionTestUtils.invokeMethod(service, "buildSimpleQuoteHead");
        assertThat(head).containsExactly(
                List.of("基础信息", "序号"),
                List.of("基础信息", "图号"),
                List.of("基础信息", "物料描述"),
                List.of("基础信息", "图纸状态"),
                List.of("基础信息", "材料名称"),
                List.of("基础信息", "表面处理"),
                List.of("基础信息", "版本"),
                List.of("基础信息", "数量"),
                List.of("基础信息", "单位"),
                List.of("合计", "含税单价"),
                List.of("合计", "含税总价"),
                List.of("合计", "交货周期"),
                List.of("合计", "备注")
        );

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setPartNo("P-100");
        item.setDescription("测试件");
        item.setDrawingUrl("/drawings/P-100.pdf");
        item.setMaterialName("45#");
        item.setSurfaceTreatment("阳极");
        item.setVersion("A");
        item.setQty(2);
        item.setUnit("件");
        item.setTaxIncludedUnitPrice(12.3);
        item.setTaxIncludedTotalPrice(24.6);
        item.setLeadTime("2026-03-20");
        item.setQuoteLeadTime("2026-03-25");
        item.setRemark("客户备注");

        List<Object> row = ReflectionTestUtils.invokeMethod(service, "buildSimpleQuoteRow", item, 1);
        assertThat(row).containsExactly(
                1,
                "P-100",
                "测试件",
                "有图",
                "45#",
                "阳极",
                "A",
                2,
                "件",
                12.3,
                24.6,
                "2026-03-25",
                "客户备注"
        );
    }

    private Map<String, Object> standardRoute() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processDetailId", 7L);
        route.put("processCode", "PACKAGING");
        route.put("categoryName", "包装");
        route.put("detailName", "精细包装");
        route.put("unitWorkHours", 1.5);
        route.put("hourlyRate", 10.0);
        route.put("lineCost", 15.0);
        route.put("sortNo", 10);
        return route;
    }

    private String text(Sheet sheet, int rowIndex, int colIndex) {
        return formatter.formatCellValue(sheet.getRow(rowIndex).getCell(colIndex));
    }
}
