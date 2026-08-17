package com.machining.quote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderSyncItemDTO;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderDTO;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncPageData;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOrderIntegrationSyncService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderIntegrationSyncService.class);
    private static final String CALLER = "EXTERNAL_SALES_ORDER_SYNC";
    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SalesOrderIntegrationMapper salesOrderIntegrationMapper;

    public SalesOrderSyncResp query(SalesOrderSyncQueryReq req) {
        long startedAt = System.nanoTime();
        SalesOrderSyncQueryReq query = req == null ? new SalesOrderSyncQueryReq() : req;
        int pageSize = query.getPageSize() == null ? 100 : query.getPageSize();
        try {
            Cursor cursor = decodeCursor(query.getCursor());
            List<SalesOrderSyncOrderRow> rawOrders = nullToEmpty(salesOrderIntegrationMapper.selectOrderPage(
                    query, cursor.changedAt(), cursor.salesOrderId(), pageSize + 1));
            boolean hasMore = rawOrders.size() > pageSize;
            List<SalesOrderSyncOrderRow> pageOrders = hasMore ? rawOrders.subList(0, pageSize) : rawOrders;

            SalesOrderSyncResp resp = SalesOrderSyncResp.empty(pageSize);
            SalesOrderSyncPageData data = resp.getData();
            data.setHasMore(hasMore);
            data.setRecords(toRecords(pageOrders, loadItems(pageOrders, query)));
            data.setNextCursor(hasMore && !pageOrders.isEmpty() ? encodeCursor(pageOrders.get(pageOrders.size() - 1)) : null);
            log.info(
                    "Sales order sync query completed caller={} pageSize={} orderCount={} itemCount={} hasMore={} durationMs={}",
                    CALLER,
                    pageSize,
                    data.getRecords().size(),
                    data.getRecords().stream().mapToInt(order -> order.getItems() == null ? 0 : order.getItems().size()).sum(),
                    hasMore,
                    durationMs(startedAt));
            return resp;
        } catch (RuntimeException error) {
            log.warn(
                    "Sales order sync query failed caller={} pageSize={} reason={} durationMs={}",
                    CALLER,
                    pageSize,
                    failureReason(error),
                    durationMs(startedAt));
            throw error;
        }
    }

    private Map<Long, List<SalesOrderSyncItemRow>> loadItems(List<SalesOrderSyncOrderRow> orders, SalesOrderSyncQueryReq req) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(SalesOrderSyncOrderRow::getSalesOrderId).toList();
        return nullToEmpty(salesOrderIntegrationMapper.selectItemsByOrderIds(orderIds, req)).stream()
                .collect(Collectors.groupingBy(
                        SalesOrderSyncItemRow::getSalesOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<SalesOrderSyncOrderDTO> toRecords(
            List<SalesOrderSyncOrderRow> orders,
            Map<Long, List<SalesOrderSyncItemRow>> itemsByOrderId) {
        return orders.stream()
                .map(order -> toOrderRecord(order, itemsByOrderId.getOrDefault(order.getSalesOrderId(), List.of())))
                .toList();
    }

    private SalesOrderSyncOrderDTO toOrderRecord(SalesOrderSyncOrderRow order, List<SalesOrderSyncItemRow> items) {
        SalesOrderSyncOrderDTO record = new SalesOrderSyncOrderDTO();
        record.setSaleOrderNo(order.getSaleOrderNo());
        record.setCreatedAt(order.getCreatedAt());
        record.setChangedAt(order.getChangedAt());
        record.setStatus(order.getStatus());
        record.setOrderType(order.getOrderType());
        record.setInternalProjectNo(order.getInternalProjectNo());
        record.setItems(items.stream()
                .map(this::toItemRecord)
                .toList());
        return record;
    }

    private SalesOrderSyncItemDTO toItemRecord(SalesOrderSyncItemRow item) {
        if (item.getLineNo() == null) {
            throw new IllegalArgumentException("sales_order_item.line_no is required for external sales order sync");
        }
        SalesOrderSyncItemDTO record = new SalesOrderSyncItemDTO();
        record.setSaleOrderItemNo(String.valueOf(item.getLineNo()));
        record.setMaterialNo(item.getMaterialNo());
        record.setReleasedQty(nonNegative(item.getReleasedQty()));
        record.setDescription(item.getDescription());
        record.setSurfaceTreatment(item.getSurfaceTreatment());
        record.setRemark(item.getRemark());
        return record;
    }

    private String encodeCursor(SalesOrderSyncOrderRow order) {
        if (order == null || order.getChangedAt() == null || order.getSalesOrderId() == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "changedAt", order.getChangedAt().format(CURSOR_TIME_FORMATTER),
                    "salesOrderId", order.getSalesOrderId()
            ));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode sales order sync cursor", e);
        }
    }

    private Cursor decodeCursor(String value) {
        if (isBlank(value)) {
            return new Cursor(null, null);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value.trim());
            JsonNode node = objectMapper.readTree(decoded);
            return new Cursor(LocalDateTime.parse(node.get("changedAt").asText(), CURSOR_TIME_FORMATTER), node.get("salesOrderId").asLong());
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor 格式错误", e);
        }
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long durationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String failureReason(RuntimeException error) {
        String message = error.getMessage();
        if (message != null && message.contains("sales_order_item.line_no")) {
            return "missingLineNo";
        }
        if (message != null && message.contains("cursor")) {
            return "invalidCursor";
        }
        return error.getClass().getSimpleName();
    }

    private Integer nonNegative(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(value, 0);
    }

    private record Cursor(LocalDateTime changedAt, Long salesOrderId) {
    }
}
