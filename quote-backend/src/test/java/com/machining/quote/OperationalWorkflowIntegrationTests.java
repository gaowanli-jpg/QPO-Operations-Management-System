package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "erp.auth.enabled=false",
        "erp.upload-dir=${java.io.tmpdir}/quote-test/uploads/",
        "erp.backup-dir=${java.io.tmpdir}/quote-test/backups/"
})
@Transactional
class OperationalWorkflowIntegrationTests {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private AuthService authService;

    @Test
    void supplementDrawingsAndDeleteOrderWorkTogether() throws Exception {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("WF-" + System.nanoTime());
        order.setCustomerName("试运行客户");
        order.setProjectName("试运行项目");
        order.setStatus("待工程部评估");
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("T-001");
        item.setVersion("A");
        item.setDescription("测试补图零件");
        item.setQty(10);
        item.setStatus("待核价");
        itemMapper.insert(item);

        ItemQuoteVersion itemVersionB = new ItemQuoteVersion();
        itemVersionB.setRfqId(order.getId());
        itemVersionB.setPartNo("T-001");
        itemVersionB.setVersion("B");
        itemVersionB.setDescription("测试补图零件B版");
        itemVersionB.setQty(10);
        itemVersionB.setStatus("待核价");
        itemMapper.insert(itemVersionB);

        MockMultipartFile drawing = new MockMultipartFile(
                "drawings",
                "T-001.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> result = orderManagementService.supplementDrawings(order.getId(), List.of(drawing));
        assertThat(result.get("success")).isEqualTo(true);

        ItemQuoteVersion updated = itemMapper.selectById(item.getId());
        assertThat(updated.getDrawingUrl()).contains("/uploads/");
        ItemQuoteVersion updatedVersionB = itemMapper.selectById(itemVersionB.getId());
        assertThat(updatedVersionB.getDrawingUrl()).contains("/uploads/");

        Path uploaded = Path.of(System.getProperty("java.io.tmpdir"), "quote-test", "uploads");
        Files.createDirectories(uploaded);
        assertThat(Files.exists(uploaded)).isTrue();

        orderManagementService.deleteOrderCompletely(order.getId(), true);
        assertThat(orderMapper.selectById(order.getId())).isNull();
    }

    @Test
    void supplementDrawingsDoesNotChangeOrderStatus() {
        OrderRfq order = new OrderRfq();
        order.setRfqNo("SUP-STATUS-" + System.nanoTime());
        order.setCustomerName("补图状态客户");
        order.setProjectName("补图状态项目");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        orderMapper.insert(order);

        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo("SUP-001");
        item.setVersion("A");
        item.setDescription("补图不改订单状态零件");
        item.setQty(1);
        item.setStatus(QuoteStatusWorkflow.PRICED);
        itemMapper.insert(item);

        MockMultipartFile drawing = new MockMultipartFile(
                "drawings",
                "SUP-001.pdf",
                "application/pdf",
                "%PDF-1.4 status".getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> result = orderManagementService.supplementDrawings(order.getId(), List.of(drawing));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(itemMapper.selectById(item.getId()).getStatus())
                .as("补图绑定到具体零件后，该零件可回到未核价等待重新核价")
                .isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(orderMapper.selectById(order.getId()).getStatus())
                .as("订单状态只能通过专门状态流转动作改变，补图不应隐式回退订单")
                .isEqualTo(QuoteStatusWorkflow.PRICED);
    }

    @Test
    void canHashPasswordForDefaultUsers() {
        String encoded = authService.hashPassword("admin123");
        assertThat(encoded).startsWith("$2");
        assertThat(authService.matchesPassword("admin123", encoded)).isTrue();
        assertThat(authService.matchesPassword("admin123", "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9")).isTrue();
    }
}
