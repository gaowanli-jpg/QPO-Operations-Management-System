package com.machining.quote;

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
        item.setDescription("测试补图零件");
        item.setQty(10);
        item.setStatus("待核价");
        itemMapper.insert(item);

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

        Path uploaded = Path.of(System.getProperty("java.io.tmpdir"), "quote-test", "uploads");
        Files.createDirectories(uploaded);
        assertThat(Files.exists(uploaded)).isTrue();

        orderManagementService.deleteOrderCompletely(order.getId(), true);
        assertThat(orderMapper.selectById(order.getId())).isNull();
    }

    @Test
    void canHashPasswordForDefaultUsers() {
        assertThat(authService.hashPassword("admin123")).isEqualTo("240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9");
    }
}
