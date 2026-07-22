package com.machining.quote;

import com.machining.quote.controller.EngineerController;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
@Transactional
class EngineerSetupPreviewTest {
    @Autowired
    private EngineerController engineerController;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;

    @Test
    @SuppressWarnings("unchecked")
    void setupSummaryOnlyReturnsFirstTenItemsWithTotalCount() {
        OrderRfq order = createOrder("SETUP-PREVIEW");
        createItems(order, 12);

        Map<String, Object> summary = engineerController.getSetupSummary(order.getId());

        assertThat(summary.get("itemCount")).isEqualTo(12L);
        assertThat(summary.get("previewLimit")).isEqualTo(10);
        assertThat((List<ItemQuoteVersion>) summary.get("itemList"))
                .hasSize(10)
                .extracting(ItemQuoteVersion::getPartNo)
                .containsExactly(
                        "PREVIEW-01", "PREVIEW-02", "PREVIEW-03", "PREVIEW-04", "PREVIEW-05",
                        "PREVIEW-06", "PREVIEW-07", "PREVIEW-08", "PREVIEW-09", "PREVIEW-10"
                );
        assertThat((List<ItemQuoteVersion>) summary.get("previewItemList"))
                .hasSize(10)
                .extracting(ItemQuoteVersion::getPartNo)
                .containsExactly(
                        "PREVIEW-01", "PREVIEW-02", "PREVIEW-03", "PREVIEW-04", "PREVIEW-05",
                        "PREVIEW-06", "PREVIEW-07", "PREVIEW-08", "PREVIEW-09", "PREVIEW-10"
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void pricingSummaryStillReturnsAllItemsForItemPricingWorkspace() {
        OrderRfq order = createOrder("PRICING-FULL");
        createItems(order, 12);

        Map<String, Object> summary = engineerController.getPricingSummary(order.getId());

        assertThat(summary).doesNotContainKeys("previewItemList", "previewLimit");
        assertThat((List<ItemQuoteVersion>) summary.get("itemList"))
                .hasSize(12)
                .extracting(ItemQuoteVersion::getPartNo)
                .containsExactly(
                        "PREVIEW-01", "PREVIEW-02", "PREVIEW-03", "PREVIEW-04", "PREVIEW-05",
                        "PREVIEW-06", "PREVIEW-07", "PREVIEW-08", "PREVIEW-09", "PREVIEW-10",
                        "PREVIEW-11", "PREVIEW-12"
                );
    }

    private OrderRfq createOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "-CUSTOMER");
        order.setStatus("未核价");
        orderMapper.insert(order);
        return order;
    }

    private void createItems(OrderRfq order, int count) {
        for (int i = 1; i <= count; i++) {
            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setRfqId(order.getId());
            item.setPartNo("PREVIEW-" + String.format("%02d", i));
            item.setVersion("A");
            item.setQty(i);
            item.setStatus("未核价");
            itemMapper.insert(item);
        }
    }
}
