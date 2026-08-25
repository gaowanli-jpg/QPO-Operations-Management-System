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
        assertThat(QuoteStatusWorkflow.PARTIALLY_RELEASED).isEqualTo("部分已提单");
        assertThat(QuoteStatusWorkflow.RELEASED).isEqualTo("已生成提单");
        assertThat(QuoteStatusWorkflow.DISCARDED).isEqualTo("废弃");

        assertThat(QuoteStatusWorkflow.nextAfterEngineerComplete(QuoteStatusWorkflow.UNPRICED))
                .as("工程完成报价后只代表核价完成，不能直接变成已报价")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(QuoteStatusWorkflow.nextAfterMarketSaveQuote(QuoteStatusWorkflow.PRICED))
                .as("市场保存报价单后，订单才进入已报价")
                .isEqualTo(QuoteStatusWorkflow.QUOTED);
    }

    @Test
    void freezesOrderManagementStatusSetAndDiscardBoundary() {
        assertThat(QuoteStatusWorkflow.orderManagementStatuses())
                .containsExactly(
                        QuoteStatusWorkflow.UNPRICED,
                        QuoteStatusWorkflow.PRICED,
                        QuoteStatusWorkflow.QUOTED,
                        QuoteStatusWorkflow.PARTIALLY_RELEASED,
                        QuoteStatusWorkflow.RELEASED,
                        QuoteStatusWorkflow.DISCARDED
                );

        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.UNPRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.PRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED))
                .as("部分已提单已有不可逆提单记录，不能废弃")
                .isFalse();
        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.RELEASED))
                .as("已生成提单已有不可逆提单记录，不能废弃")
                .isFalse();
        assertThat(QuoteStatusWorkflow.isDiscardableOrderStatus(QuoteStatusWorkflow.DISCARDED)).isFalse();

        assertThat(QuoteStatusWorkflow.isReleasedOrderStatus(QuoteStatusWorkflow.PARTIALLY_RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.isReleasedOrderStatus(QuoteStatusWorkflow.RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.isReleasedOrderStatus(QuoteStatusWorkflow.QUOTED)).isFalse();
    }

    @Test
    void freezesQuoteProcessReadEditAndExportGates() {
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.PRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.PARTIALLY_RELEASED))
                .as("部分已提单订单允许回到报价处理只读导出")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.RELEASED))
                .as("已生成提单订单允许回到报价处理只读导出")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canReadQuoteProcess(QuoteStatusWorkflow.UNPRICED)).isFalse();

        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.PRICED))
                .as("已核价订单允许市场调整报价交期")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.QUOTED))
                .as("已报价订单交期锁定")
                .isFalse();
        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.PARTIALLY_RELEASED))
                .as("已提单订单交期锁定")
                .isFalse();
        assertThat(QuoteStatusWorkflow.canEditQuoteLeadTime(QuoteStatusWorkflow.RELEASED))
                .as("已提单订单交期锁定")
                .isFalse();

        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.PRICED)).isTrue();
        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.QUOTED)).isFalse();
        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.PARTIALLY_RELEASED)).isFalse();
        assertThat(QuoteStatusWorkflow.canSaveQuoteSheet(QuoteStatusWorkflow.RELEASED)).isFalse();

        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.PRICED))
                .as("已核价订单必须先保存报价单，不能直接导出")
                .isFalse();
        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.PARTIALLY_RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.canExportQuoteSheet(QuoteStatusWorkflow.RELEASED)).isTrue();

        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.PRICED))
                .as("工程核价单只代表工程侧核价结果，应允许已核价订单生成")
                .isTrue();
        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.UNPRICED)).isFalse();
        assertThat(QuoteStatusWorkflow.canExportPricingSheet(QuoteStatusWorkflow.QUOTED))
                .as("保存为正式报价单后，不再从工程侧生成核价单")
                .isFalse();
    }

    @Test
    void releasedOrdersCanOpenReleaseWorkspaceAndGenerateAdditionalTickets() {
        assertThat(QuoteStatusWorkflow.canReadReleaseWorkspace(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadReleaseWorkspace(QuoteStatusWorkflow.PARTIALLY_RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadReleaseWorkspace(QuoteStatusWorkflow.RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.canReadReleaseWorkspace(QuoteStatusWorkflow.PRICED)).isFalse();

        assertThat(QuoteStatusWorkflow.canGenerateDeliveryTicket(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.canGenerateDeliveryTicket(QuoteStatusWorkflow.PARTIALLY_RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.canGenerateDeliveryTicket(QuoteStatusWorkflow.RELEASED))
                .as("Released orders stay selectable because inquiry quantity is no longer a release cap")
                .isTrue();
    }

    @Test
    void supplementDrawingRestoresOnlyMissingDrawingItemsWithoutPriceToUnpriced() {
        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.NO_DRAWING, false, false))
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);

        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.NO_DRAWING, false, true))
                .isEqualTo(QuoteStatusWorkflow.NO_DRAWING);
        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.NO_DRAWING, true, false))
                .isEqualTo(QuoteStatusWorkflow.NO_DRAWING);
        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.PRICED, false, false))
                .isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.QUOTED, false, false))
                .isEqualTo(QuoteStatusWorkflow.QUOTED);
        assertThat(QuoteStatusWorkflow.statusAfterSupplementDrawing(QuoteStatusWorkflow.ITEM_RELEASED, false, false))
                .isEqualTo(QuoteStatusWorkflow.ITEM_RELEASED);
    }

    @Test
    void rfqReleaseEligibleItemsRequireMarketConfirmedStatusDrawingAndPrice() {
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.QUOTED, true, true)).isTrue();
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.ITEM_RELEASED, true, true)).isTrue();

        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.PRICED, true, true)).isFalse();
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.QUOTED, false, true)).isFalse();
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.QUOTED, true, false)).isFalse();
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.PARTIALLY_RELEASED, true, true)).isFalse();
        assertThat(QuoteStatusWorkflow.isRfqReleaseEligibleItem(QuoteStatusWorkflow.RELEASED, true, true)).isFalse();

        assertThat(QuoteStatusWorkflow.isMarketConfirmedItemStatus(QuoteStatusWorkflow.QUOTED)).isTrue();
        assertThat(QuoteStatusWorkflow.isMarketConfirmedItemStatus(QuoteStatusWorkflow.ITEM_RELEASED)).isTrue();
        assertThat(QuoteStatusWorkflow.isMarketConfirmedItemStatus(QuoteStatusWorkflow.PRICED)).isFalse();
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
