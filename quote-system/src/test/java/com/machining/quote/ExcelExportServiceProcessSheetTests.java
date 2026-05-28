package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.LeadTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceProcessSheetTests {

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
        item.setFinalUnitPrice(12.3);
        item.setFinalTotalPrice(24.6);
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
}
