package com.machining.quote;

import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.service.RfqReleaseBatchValidationService;
import com.machining.quote.service.RfqReleaseSalesOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RfqReleaseSalesOrderEffectiveFieldsTest {
    @Test
    void rejectsForecastOrderTypeFromExistingItemWhenGeneratingNewTicket() {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(701);
        item.setRfqId(301);
        item.setPartNo("RFQ-PART-001");
        item.setCustomerOrderNo("CUSTOMER-PO");
        item.setInternalProjectNo("INTERNAL-PROJECT");
        item.setOrderType("预测订单");

        RfqReleaseBatchValidationService.BatchContext context = new RfqReleaseBatchValidationService.BatchContext(
                List.of(301), Map.of(), "C001", "ACME", "ACME Manufacturing", "SO06",
                LocalDate.of(2026, 10, 8), "Customer title", "正式订单", "INTERNAL-PROJECT", "CUSTOMER-PO",
                "CUSTOMER-PROJECT", "PROJECT-001", "计划", "release-batch-key", "release-batch-summary", "request-hash");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                new RfqReleaseSalesOrderService(), "effectiveFields", item, context))
                .hasMessageContaining("订单类型不合法：预测订单");
    }

    @Test
    void reportsUnifiedMessageWhenEffectiveCustomerOrderNumberIsMissing() {
        assertMissingIdentifiers(null, "TARGET-INTERNAL");
    }

    @Test
    void reportsUnifiedMessageWhenEffectiveInternalProjectNumberIsMissing() {
        assertMissingIdentifiers("TARGET-PO", null);
    }

    private void assertMissingIdentifiers(String customerOrderNo, String internalProjectNo) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(701);
        item.setRfqId(301);
        item.setPartNo("RFQ-PART-001");

        RfqReleaseBatchValidationService.BatchContext context = new RfqReleaseBatchValidationService.BatchContext(
                List.of(301),
                Map.of(),
                "C001",
                "ACME",
                "ACME Manufacturing",
                "SO06",
                LocalDate.of(2026, 10, 8),
                "Customer title",
                "备货订单",
                internalProjectNo,
                customerOrderNo,
                "CUSTOMER-PROJECT",
                "PROJECT-001",
                "计划",
                "append-batch-key",
                "append-batch-summary",
                "request-hash");

        RfqReleaseSalesOrderService service = new RfqReleaseSalesOrderService();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "effectiveFields", item, context))
                .hasMessage("编号有缺失，请补齐内部项目号或客户订单号。");
    }
}
