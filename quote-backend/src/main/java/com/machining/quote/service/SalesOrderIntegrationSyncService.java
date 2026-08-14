package com.machining.quote.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncPageData;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOrderIntegrationSyncService {
    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SalesOrderIntegrationMapper salesOrderIntegrationMapper;

    public SalesOrderSyncResp query(SalesOrderSyncQueryReq req) {
        SalesOrderSyncQueryReq query = req == null ? new SalesOrderSyncQueryReq() : req;
        int pageSize = query.getPageSize() == null ? 100 : query.getPageSize();
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
        return resp;
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

    private List<Map<String, Object>> toRecords(
            List<SalesOrderSyncOrderRow> orders,
            Map<Long, List<SalesOrderSyncItemRow>> itemsByOrderId) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (SalesOrderSyncOrderRow order : orders) {
            LinkedHashMap<String, Object> record = new LinkedHashMap<>();
            record.put("saleOrderNo", order.getSaleOrderNo());
            record.put("documentNo", order.getDocumentNo());
            record.put("customerCode", order.getCustomerCode());
            record.put("customerName", order.getCustomerName());
            record.put("customerShortName", order.getCustomerShortName());
            record.put("salesOrderDate", order.getSalesOrderDate());
            record.put("customerOrderNo", order.getCustomerOrderNo());
            record.put("customerProjectNo", order.getCustomerProjectNo());
            record.put("customerOrderTitle", order.getCustomerOrderTitle());
            record.put("internalProjectNo", order.getInternalProjectNo());
            record.put("orderType", order.getOrderType());
            record.put("status", order.getStatus());
            record.put("createdAt", order.getCreatedAt());
            record.put("updatedAt", order.getUpdatedAt());
            record.put("changedAt", order.getChangedAt());
            record.put("items", itemsByOrderId.getOrDefault(order.getSalesOrderId(), List.of()).stream()
                    .map(this::toItemRecord)
                    .toList());
            records.add(record);
        }
        return records;
    }

    private Map<String, Object> toItemRecord(SalesOrderSyncItemRow item) {
        LinkedHashMap<String, Object> record = new LinkedHashMap<>();
        record.put("saleOrderNo", item.getSaleOrderNo());
        record.put("saleOrderItemNo", item.getSaleOrderItemNo());
        record.put("lineNo", item.getLineNo());
        record.put("partNo", item.getPartNo());
        record.put("version", item.getVersion());
        record.put("description", item.getDescription());
        record.put("qty", item.getQty());
        record.put("materialNo", item.getMaterialNo());
        record.put("materialNoStatus", item.getMaterialNoStatus());
        record.put("salesMaterialNo", item.getSalesMaterialNo());
        record.put("customerDeliveryDate", item.getCustomerDeliveryDate());
        record.put("internalDeliveryDate", item.getInternalDeliveryDate());
        record.put("orderType", item.getOrderType());
        record.put("status", item.getStatus());
        record.put("createdAt", item.getCreatedAt());
        record.put("updatedAt", item.getUpdatedAt());
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

    private record Cursor(LocalDateTime changedAt, Long salesOrderId) {
    }
}
