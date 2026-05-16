package com.machining.quote;

import com.machining.quote.entity.OrderRfq;
import com.machining.quote.service.OrderFilePathService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilePathServiceTests {
    @Test
    void normalizesUnsafeCustomerAndProjectSegments() {
        OrderFilePathService service = new OrderFilePathService();
        ReflectionTestUtils.setField(service, "uploadDir", "D:/ERP_Files/");

        OrderRfq order = new OrderRfq();
        order.setId(10);
        order.setCustomerName("客户/A..");
        order.setProjectName("项目:*?B");

        assertThat(service.orderDrawingDirectory(order).toString()).contains("客户_A_").contains("项目___B");
        assertThat(service.drawingUrl(order, "../图纸.pdf")).doesNotContain("..").contains("/uploads/");
    }
}
