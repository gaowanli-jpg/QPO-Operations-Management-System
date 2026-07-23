package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderRfqMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeliveryDocumentService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryDocumentService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> DOCUMENT_STATUSES = Set.of(
            QuoteStatusWorkflow.PARTIALLY_RELEASED,
            QuoteStatusWorkflow.RELEASED
    );
    private static final String DOCUMENT_STATUS_MESSAGE = "只有部分已提单或已生成提单订单可以查看提单表据";

    @Autowired
    private OrderManagementService orderManagementService;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private ExcelExportService excelExportService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<String> listDocumentCustomers() {
        return orderManagementService.listCustomers();
    }

    public PageResult<OrderRfq> listDocumentOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = clean(status);
        if (normalizedStatus != null && !DOCUMENT_STATUSES.contains(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> query = new QueryWrapper<>();
        applyOrderScope(query);
        applyTextEquals(query, "customer_name", customerName);
        if (normalizedStatus == null) {
            query.in("status", DOCUMENT_STATUSES);
        } else {
            query.eq("status", normalizedStatus);
        }
        if (hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("rfq_no", text)
                    .or()
                    .like("status", text));
        }

        Long total = orderRfqMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderRfqMapper.selectList(query), total, safePage, safePageSize);
    }

    public Map<String, Object> documentSummary(Integer rfqId) {
        OrderRfq order = requireDocumentOrder(rfqId);
        PageResult<OrderDeliveryTicket> ticketPage = deliveryService.pageTickets(rfqId, 1, 1);
        PageResult<ProcessSheetDTO> processSheetPage = deliveryService.pageProcessSheets(rfqId, 1, 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("ticketCount", ticketPage.getTotal());
        result.put("processSheetCount", processSheetPage.getTotal());
        result.put("ticketQty", ticketQty(rfqId));
        return result;
    }

    public PageResult<OrderDeliveryTicket> pageTickets(Integer rfqId, Integer page, Integer pageSize) {
        requireDocumentOrder(rfqId);
        return deliveryService.pageTickets(rfqId, page, pageSize);
    }

    public PageResult<ProcessSheetDTO> pageProcessSheets(Integer rfqId, Integer page, Integer pageSize) {
        requireDocumentOrder(rfqId);
        return deliveryService.pageProcessSheets(rfqId, page, pageSize);
    }

    public void exportTickets(Integer rfqId, HttpServletResponse response) throws IOException {
        exportDocument(rfqId, response, "tickets");
    }

    public void exportProcessSheets(Integer rfqId, HttpServletResponse response) throws IOException {
        exportDocument(rfqId, response, "process-sheets");
    }

    private void exportDocument(Integer rfqId, HttpServletResponse response, String exportType) throws IOException {
        long startedAt = System.currentTimeMillis();
        log.info("Delivery document export started rfqId={} exportType={}", rfqId, exportType);
        try {
            OrderRfq order = requireDocumentOrder(rfqId);
            long rowCount;
            if ("tickets".equals(exportType)) {
                PageResult<OrderDeliveryTicket> ticketPage = deliveryService.pageTickets(rfqId, 1, 1);
                rowCount = ticketPage.getTotal();
                excelExportService.exportDeliveryTickets(rfqId, response);
                auditService.record("DELIVERY_TICKET_EXPORT", "order_rfq", rfqId, null,
                        exportAuditData(order, exportType, rowCount));
            } else {
                PageResult<ProcessSheetDTO> processSheetPage = deliveryService.pageProcessSheets(rfqId, 1, 1);
                rowCount = processSheetPage.getTotal();
                excelExportService.exportProcessSheets(rfqId, response);
                auditService.record("DELIVERY_PROCESS_SHEET_EXPORT", "order_rfq", rfqId, null,
                        exportAuditData(order, exportType, rowCount));
            }
            log.info("Delivery document export completed rfqId={} exportType={} rowCount={} durationMs={}",
                    rfqId, exportType, rowCount, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Delivery document export rejected rfqId={} exportType={} reason={}", rfqId, exportType, safeLogMessage(e));
            } else {
                log.error("Delivery document export failed rfqId={} exportType={} durationMs={}",
                        rfqId, exportType, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
        } catch (IOException e) {
            log.error("Delivery document export failed rfqId={} exportType={} durationMs={}",
                    rfqId, exportType, System.currentTimeMillis() - startedAt, e);
            throw e;
        }
    }

    private Map<String, Object> exportAuditData(OrderRfq order, String exportType, long rowCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rfqId", order.getId());
        data.put("rfqNo", order.getRfqNo());
        data.put("status", order.getStatus());
        data.put("exportType", exportType);
        data.put("rowCount", rowCount);
        return data;
    }

    private long ticketQty(Integer rfqId) {
        return deliveryService.listTickets(rfqId).stream()
                .mapToLong(ticket -> ticket.getQty() == null ? 0 : ticket.getQty())
                .sum();
    }

    private OrderRfq requireDocumentOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderScope(order);
        if (!DOCUMENT_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException(DOCUMENT_STATUS_MESSAGE);
        }
        return order;
    }

    private void applyOrderScope(QueryWrapper<OrderRfq> query) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(query);
        }
    }

    private void assertOrderScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private void applyTextEquals(QueryWrapper<OrderRfq> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isBusinessFailure(RuntimeException e) {
        return e.getClass().equals(RuntimeException.class) && e.getCause() == null && hasText(e.getMessage());
    }

    private String safeLogMessage(RuntimeException e) {
        String message = e == null ? "" : e.getMessage();
        if (!hasText(message)) {
            return e == null ? "unknown" : e.getClass().getSimpleName();
        }
        return message.replaceAll("[\\r\\n\\t]+", " ");
    }
}
