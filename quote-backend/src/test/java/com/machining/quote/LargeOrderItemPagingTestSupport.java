package com.machining.quote;

import com.machining.quote.controller.EngineerController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteProcessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
abstract class LargeOrderItemPagingTestSupport {
    protected static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");
    protected static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    @Autowired
    protected OrderItemPageService orderItemPageService;
    @Autowired
    protected InternalTargetLeadTimeService internalTargetLeadTimeService;
    @Autowired
    protected DeliveryService deliveryService;
    @Autowired
    protected OrderManagementService orderManagementService;
    @Autowired
    protected QuoteProcessService quoteProcessService;
    @Autowired
    protected EngineerController engineerController;
    @Autowired
    protected OrderRfqMapper orderMapper;
    @Autowired
    protected ItemQuoteVersionMapper itemMapper;
    @Autowired
    protected OrderDeliveryTicketMapper ticketMapper;

    @AfterEach
    void tearDown() {
        // Transactional rollback handles test data; method kept as a visible reminder that tests are isolated.
    }

    protected String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }

    protected String assertSourceContains(String packageName, String fileName, String expected) throws Exception {
        String source = source(packageName, fileName);
        assertThat(source).contains(expected);
        return source;
    }

    protected String method(String source, String methodName) {
        int start = source.indexOf(methodName + "(");
        assertThat(start).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int nextAnnotation = source.indexOf("\n    @", start + methodName.length());
        int end = nextAnnotation >= 0 ? nextAnnotation : source.length();
        return source.substring(start, end);
    }

    protected String javaMethod(String source, String methodName) {
        int nameIndex = source.indexOf(methodName + "(");
        assertThat(nameIndex).as("method %s exists", methodName).isGreaterThanOrEqualTo(0);
        int start = source.lastIndexOf('\n', nameIndex);
        int firstBrace = source.indexOf('{', nameIndex);
        assertThat(firstBrace).as("method %s has a body", methodName).isGreaterThanOrEqualTo(0);
        int depth = 0;
        for (int index = firstBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(Math.max(0, start), index + 1);
                }
            }
        }
        throw new AssertionError("method body not closed: " + methodName);
    }

    protected OrderRfq createOrder(String prefix) {
        OrderRfq order = new OrderRfq();
        order.setRfqNo(prefix + "-" + System.nanoTime());
        order.setCustomerName(prefix + "-CUSTOMER");
        order.setStatus(QuoteStatusWorkflow.UNPRICED);
        orderMapper.insert(order);
        return order;
    }

    protected OrderRfq createReleaseOrder(String prefix) {
        OrderRfq order = createOrder(prefix);
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        orderMapper.updateById(order);
        return order;
    }

    protected ItemQuoteVersion createItem(OrderRfq order, String partNo, String status, Double taxIncludedUnitPrice, String drawingUrl) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setQty(1);
        item.setStatus(status);
        item.setTaxIncludedUnitPrice(taxIncludedUnitPrice);
        item.setDrawingUrl(drawingUrl);
        itemMapper.insert(item);
        return item;
    }

    protected OrderDeliveryTicket createTicket(OrderRfq order, ItemQuoteVersion item, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setRfqId(order.getId());
        ticket.setRfqNo(order.getRfqNo());
        ticket.setCustomerName(order.getCustomerName());
        ticket.setItemId(item.getId());
        ticket.setPartNo(item.getPartNo());
        ticket.setVersion(item.getVersion());
        ticket.setQty(qty);
        ticketMapper.insert(ticket);
        return ticket;
    }

    protected Map<String, String> importedSelection(String rfqNo, String partNo, String version) {
        return Map.of(
                "rfqNo", rfqNo,
                "normalizedRfqNo", rfqNo == null ? "" : rfqNo.trim().toUpperCase(),
                "partNo", partNo,
                "normalizedPartNo", partNo == null ? "" : partNo.trim().toUpperCase(),
                "version", version,
                "normalizedVersion", version == null ? "" : version.trim().toUpperCase()
        );
    }
}
