package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
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
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private ExcelExportService excelExportService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<CustomerOptionDTO> listDocumentCustomers(String field, String keyword) {
        return orderManagementService.listCustomerOptions(field, keyword);
    }

    public PageResult<CustomerOptionDTO> pageDocumentCustomers(String field, String keyword, Integer page, Integer pageSize) {
        return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    public PageResult<Map<String, Object>> listDocumentOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        return listDocumentOrders(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<Map<String, Object>> listDocumentOrders(String customerCode, String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = clean(status);
        if (normalizedStatus != null && !DOCUMENT_STATUSES.contains(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        int fetchLimit = mixedSourceFetchLimit(safePage, safePageSize);
        SourceOrderBucket<OrderRfq> rfqBucket = listDocumentRfqOrders(customerCode, customerName, keyword, normalizedStatus, fetchLimit);
        SourceOrderBucket<SalesOrderHeader> salesOrderBucket = listDocumentSalesOrders(customerCode, customerName, keyword, normalizedStatus, fetchLimit);
        long total = rfqBucket.total() + salesOrderBucket.total();

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(rfqBucket.records().stream()
                .map(this::buildRfqSourceRow)
                .toList());
        rows.addAll(salesOrderBucket.records().stream()
                .map(this::buildSalesOrderSourceRow)
                .toList());
        rows.sort((left, right) -> Long.compare(safeLong(right.get("sortId")), safeLong(left.get("sortId"))));
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), total, safePage, safePageSize);
    }

    private SourceOrderBucket<OrderRfq> listDocumentRfqOrders(String customerCode, String customerName, String keyword, String normalizedStatus, int fetchLimit) {
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();
        applyOrderScope(query);
        applyCustomerFilter(query, customerCode, customerName);
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
            return new SourceOrderBucket<>(List.of(), 0);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + fetchLimit);
        return new SourceOrderBucket<>(orderRfqMapper.selectList(query), total);
    }

    private SourceOrderBucket<SalesOrderHeader> listDocumentSalesOrders(String customerCode, String customerName, String keyword, String normalizedStatus, int fetchLimit) {
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        applyCustomerFilter(query, customerCode, customerName);
        if (normalizedStatus == null) {
            query.in("status", DOCUMENT_STATUSES);
        } else {
            query.eq("status", normalizedStatus);
        }
        if (hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("sales_order_no", text)
                    .or()
                    .like("customer_order_no", text)
                    .or()
                    .like("internal_project_no", text)
                    .or()
                    .like("customer_project_no", text)
                    .or()
                    .like("status", text));
        }

        Long total = salesOrderHeaderMapper.selectCount(query);
        if (total == null || total == 0) {
            return new SourceOrderBucket<>(List.of(), 0);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + fetchLimit);
        return new SourceOrderBucket<>(salesOrderHeaderMapper.selectList(query), total);
    }

    private Map<String, Object> buildRfqSourceRow(OrderRfq order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("sourceType", ReleaseSourceType.RFQ.name());
        row.put("sourceOrderId", order.getId() == null ? null : order.getId().longValue());
        row.put("sourceOrderNo", order.getRfqNo());
        row.put("rfqNo", order.getRfqNo());
        row.put("customerName", order.getCustomerName());
        row.put("status", order.getStatus());
        row.put("createdAt", order.getCreatedAt());
        row.put("sortId", order.getId());
        return row;
    }

    private Map<String, Object> buildSalesOrderSourceRow(SalesOrderHeader order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("sourceType", ReleaseSourceType.SALES_ORDER.name());
        row.put("sourceOrderId", order.getId());
        row.put("sourceOrderNo", order.getSalesOrderNo());
        row.put("salesOrderNo", order.getSalesOrderNo());
        row.put("customerName", order.getCustomerName());
        row.put("customerOrderNo", order.getCustomerOrderNo());
        row.put("customerProjectNo", order.getCustomerProjectNo());
        row.put("internalProjectNo", order.getInternalProjectNo());
        row.put("status", order.getStatus());
        row.put("createdAt", order.getCreatedAt());
        row.put("sortId", order.getId());
        return row;
    }

    public Map<String, Object> documentSummary(Integer rfqId) {
        return documentSummary(ReleaseSourceType.RFQ.name(), rfqId == null ? null : rfqId.longValue());
    }

    public Map<String, Object> documentSummary(String sourceType, Long sourceOrderId) {
        DocumentSource source = requireDocumentSource(sourceType, sourceOrderId);
        PageResult<OrderDeliveryTicket> ticketPage = deliveryService.pageTickets(source.sourceType(), source.sourceOrderId(), 1, 1);
        PageResult<ProcessSheetDTO> processSheetPage = deliveryService.pageProcessSheets(source.sourceType(), source.sourceOrderId(), 1, 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", source.orderInfo());
        result.put("ticketCount", ticketPage.getTotal());
        result.put("processSheetCount", processSheetPage.getTotal());
        result.put("ticketQty", ticketQty(source.sourceType(), source.sourceOrderId()));
        return result;
    }

    public PageResult<OrderDeliveryTicket> pageTickets(Integer rfqId, Integer page, Integer pageSize) {
        return pageTickets(ReleaseSourceType.RFQ.name(), rfqId == null ? null : rfqId.longValue(), page, pageSize);
    }

    public PageResult<OrderDeliveryTicket> pageTickets(String sourceType, Long sourceOrderId, Integer page, Integer pageSize) {
        DocumentSource source = requireDocumentSource(sourceType, sourceOrderId);
        return deliveryService.pageTickets(source.sourceType(), source.sourceOrderId(), page, pageSize);
    }

    public PageResult<ProcessSheetDTO> pageProcessSheets(Integer rfqId, Integer page, Integer pageSize) {
        return pageProcessSheets(ReleaseSourceType.RFQ.name(), rfqId == null ? null : rfqId.longValue(), page, pageSize);
    }

    public PageResult<ProcessSheetDTO> pageProcessSheets(String sourceType, Long sourceOrderId, Integer page, Integer pageSize) {
        DocumentSource source = requireDocumentSource(sourceType, sourceOrderId);
        return deliveryService.pageProcessSheets(source.sourceType(), source.sourceOrderId(), page, pageSize);
    }

    public void exportTickets(Integer rfqId, HttpServletResponse response) throws IOException {
        exportTickets(ReleaseSourceType.RFQ.name(), rfqId == null ? null : rfqId.longValue(), response);
    }

    public void exportTickets(String sourceType, Long sourceOrderId, HttpServletResponse response) throws IOException {
        exportDocument(sourceType, sourceOrderId, response, "tickets");
    }

    public void exportProcessSheets(Integer rfqId, HttpServletResponse response) throws IOException {
        exportProcessSheets(ReleaseSourceType.RFQ.name(), rfqId == null ? null : rfqId.longValue(), response);
    }

    public void exportProcessSheets(String sourceType, Long sourceOrderId, HttpServletResponse response) throws IOException {
        exportDocument(sourceType, sourceOrderId, response, "process-sheets");
    }

    private void exportDocument(String sourceTypeValue, Long sourceOrderId, HttpServletResponse response, String exportType) throws IOException {
        long startedAt = System.currentTimeMillis();
        ReleaseSourceType sourceType = parseSourceType(sourceTypeValue);
        log.info("Delivery document export started sourceType={} sourceOrderId={} exportType={}", sourceType, sourceOrderId, exportType);
        try {
            DocumentSource source = requireDocumentSource(sourceType.name(), sourceOrderId);
            long rowCount;
            if ("tickets".equals(exportType)) {
                PageResult<OrderDeliveryTicket> ticketPage = deliveryService.pageTickets(source.sourceType(), source.sourceOrderId(), 1, 1);
                rowCount = ticketPage.getTotal();
                excelExportService.exportDeliveryTickets(source.sourceType(), source.sourceOrderId(), source.sourceOrderNo(), response);
                auditService.record("DELIVERY_TICKET_EXPORT", source.targetType(), source.targetId(), null,
                        exportAuditData(source, exportType, rowCount));
            } else {
                PageResult<ProcessSheetDTO> processSheetPage = deliveryService.pageProcessSheets(source.sourceType(), source.sourceOrderId(), 1, 1);
                rowCount = processSheetPage.getTotal();
                excelExportService.exportProcessSheets(source.sourceType(), source.sourceOrderId(), source.sourceOrderNo(), response);
                auditService.record("DELIVERY_PROCESS_SHEET_EXPORT", source.targetType(), source.targetId(), null,
                        exportAuditData(source, exportType, rowCount));
            }
            log.info("Delivery document export completed sourceType={} sourceOrderId={} exportType={} rowCount={} durationMs={}",
                    source.sourceType(), source.sourceOrderId(), exportType, rowCount, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            if (isBusinessFailure(e)) {
                log.warn("Delivery document export rejected sourceType={} sourceOrderId={} exportType={} reason={}",
                        sourceType, sourceOrderId, exportType, safeLogMessage(e));
            } else {
                log.error("Delivery document export failed sourceType={} sourceOrderId={} exportType={} durationMs={}",
                        sourceType, sourceOrderId, exportType, System.currentTimeMillis() - startedAt, e);
            }
            throw e;
        } catch (IOException e) {
            log.error("Delivery document export failed sourceType={} sourceOrderId={} exportType={} durationMs={}",
                    sourceType, sourceOrderId, exportType, System.currentTimeMillis() - startedAt, e);
            throw e;
        }
    }

    private Map<String, Object> exportAuditData(DocumentSource source, String exportType, long rowCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceType", source.sourceType().name());
        data.put("sourceOrderId", source.sourceOrderId());
        data.put("sourceOrderNo", source.sourceOrderNo());
        data.put("status", source.status());
        data.put("exportType", exportType);
        data.put("rowCount", rowCount);
        return data;
    }

    private long ticketQty(ReleaseSourceType sourceType, Long sourceOrderId) {
        return deliveryService.listTickets(sourceType, sourceOrderId).stream()
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

    private DocumentSource requireDocumentSource(String sourceTypeValue, Long sourceOrderId) {
        ReleaseSourceType sourceType = parseSourceType(sourceTypeValue);
        if (sourceOrderId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        if (sourceType == ReleaseSourceType.SALES_ORDER) {
            SalesOrderHeader order = salesOrderHeaderMapper.selectById(sourceOrderId);
            if (order == null) {
                throw new RuntimeException("订单不存在");
            }
            assertSalesOrderScope(order);
            if (!DOCUMENT_STATUSES.contains(order.getStatus())) {
                throw new RuntimeException(DOCUMENT_STATUS_MESSAGE);
            }
            Map<String, Object> row = buildSalesOrderSourceRow(order);
            return new DocumentSource(
                    ReleaseSourceType.SALES_ORDER,
                    sourceOrderId,
                    safeText(order.getSalesOrderNo(), "SO-" + sourceOrderId),
                    row,
                    order.getStatus(),
                    "sales_order_header",
                    sourceOrderId);
        }
        if (sourceOrderId > Integer.MAX_VALUE || sourceOrderId < Integer.MIN_VALUE) {
            throw new RuntimeException("订单不存在");
        }
        OrderRfq order = requireDocumentOrder(sourceOrderId.intValue());
        Map<String, Object> row = buildRfqSourceRow(order);
        return new DocumentSource(
                ReleaseSourceType.RFQ,
                sourceOrderId,
                safeText(order.getRfqNo(), "RFQ-" + sourceOrderId),
                row,
                order.getStatus(),
                "order_rfq",
                sourceOrderId.intValue());
    }

    private void applyOrderScope(QueryWrapper<OrderRfq> query) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(query);
        }
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private void assertOrderScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private void assertSalesOrderScope(SalesOrderHeader order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
    }

    private void applyTextEquals(QueryWrapper<?> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private void applyCustomerFilter(QueryWrapper<?> query, String customerCode, String customerName) {
        if (hasText(customerCode)) {
            query.eq("customer_code", customerCode.trim());
        } else {
            applyTextEquals(query, "customer_name", customerName);
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

    private int mixedSourceFetchLimit(int page, int pageSize) {
        long pageEnd = (long) page * pageSize;
        if (pageEnd > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(pageSize, (int) pageEnd);
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ReleaseSourceType parseSourceType(String value) {
        if (ReleaseSourceType.SALES_ORDER.name().equalsIgnoreCase(clean(value))) {
            return ReleaseSourceType.SALES_ORDER;
        }
        return ReleaseSourceType.RFQ;
    }

    private long safeLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String safeText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
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

    private record DocumentSource(
            ReleaseSourceType sourceType,
            Long sourceOrderId,
            String sourceOrderNo,
            Map<String, Object> orderInfo,
            String status,
            String targetType,
            Object targetId) {
    }

    private record SourceOrderBucket<T>(List<T> records, long total) {
    }
}
