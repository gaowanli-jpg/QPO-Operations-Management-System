package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteStatusWorkflowTest {
    @Test
    void freezesTargetOrderStatusFlow() {
        assertThat(QuoteStatusWorkflow.UNPRICED).isEqualTo("未核价");
        assertThat(QuoteStatusWorkflow.PRICED).isEqualTo("已核价");
        assertThat(QuoteStatusWorkflow.QUOTED).isEqualTo("已报价");

        assertThat(QuoteStatusWorkflow.nextAfterEngineerComplete(QuoteStatusWorkflow.UNPRICED))
                .as("工程完成报价后只代表核价完成，不能直接变成已报价")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(QuoteStatusWorkflow.nextAfterMarketSaveQuote(QuoteStatusWorkflow.PRICED))
                .as("市场保存报价单后，订单才进入已报价")
                .isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void freezesQuoteProcessReadEditAndExportGates() {
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.PRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.UNPRICED)).isFalse();

        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.PRICED))
                .as("已核价订单允许市场调整报价交期")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.QUOTED))
                .as("已报价订单交期锁定")
                .isFalse();

        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.PRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.QUOTED)).isFalse();

        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.PRICED))
                .as("已核价订单必须先保存报价单，不能直接导出")
                .isFalse();
        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.QUOTED)).isTrue();

        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.PRICED))
                .as("工程核价单只代表工程侧核价结果，应允许已核价订单生成")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.UNPRICED)).isFalse();
        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.QUOTED))
                .as("保存为正式报价单后，不再从工程侧生成核价单")
                .isFalse();
    }

    @Test
    void customerLeadTimeQuoteExportIsNotExposedAsASeparateExportPath() throws IOException {
        String marketController = source("controller/MarketController.java");
        String excelExportService = source("service/ExcelExportService.java");
        String systemTaskService = source("service/SystemTaskService.java");

        assertThat(marketController).doesNotContain("customer-lead-time");
        assertThat(marketController).doesNotContain("exportCustomerLeadTimeQuoteSheet");
        assertThat(excelExportService).doesNotContain("exportCustomerLeadTimeQuoteSheet");
        assertThat(excelExportService).doesNotContain("CUSTOMER_LEAD_TIME");
        assertThat(systemTaskService).doesNotContain("CUSTOMER_LEAD_TIME");
    }

    @Test
    void recognizesLegacyUnpricedLabelsWithoutUsingThemAsNewWriteTargets() {
        assertThat(QuoteStatusWorkflow.isLegacyUnpricedStatus("待工程部评估")).isTrue();
        assertThat(QuoteStatusWorkflow.isLegacyUnpricedStatus("待核价")).isTrue();
        assertThat(QuoteStatusWorkflow.normalizeUnpricedStatus("待工程部评估"))
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(QuoteStatusWorkflow.normalizeUnpricedStatus("待核价"))
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/java/com/machining/quote", relativePath));
    }
}
