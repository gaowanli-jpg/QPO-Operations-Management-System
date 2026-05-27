package com.machining.quote;

import com.machining.quote.service.ExcelExportService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
