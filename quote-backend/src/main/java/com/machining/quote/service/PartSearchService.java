package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartSearchItemDetailDTO;
import com.machining.quote.dto.PartSearchItemRowDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PartSearchService {
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private SalesOrderItemMapper salesItemMapper;
    @Autowired
    private SalesOrderHeaderMapper salesHeaderMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public PageResult<PartSearchItemRowDTO> searchRfqItems(String partNo, String version, Integer page, Integer pageSize) {
        String safePartNo = requirePartNo(partNo);
        String safeVersion = trimToNull(version);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);

        QueryWrapper<ItemQuoteVersion> countQuery = new QueryWrapper<>();
        applyPartFilters(countQuery, safePartNo, safeVersion);
        if (dataScopeService != null) {
            dataScopeService.applyRfqScope(countQuery, "rfq_id");
        }
        Long total = itemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<ItemQuoteVersion> pageQuery = new QueryWrapper<>();
        applyPartFilters(pageQuery, safePartNo, safeVersion);
        if (dataScopeService != null) {
            dataScopeService.applyRfqScope(pageQuery, "rfq_id");
        }
        pageQuery.orderByDesc("id");
        pageQuery.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<ItemQuoteVersion> items = itemMapper.selectList(pageQuery);
        Map<Integer, OrderRfq> ordersById = loadRfqOrders(items);
        List<PartSearchItemRowDTO> records = items.stream()
                .map(item -> toRfqRow(item, ordersById.get(item.getRfqId())))
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public PartSearchItemDetailDTO getRfqItemDetail(Integer itemId) {
        if (itemId == null) {
            throw new RuntimeException("零件ID不能为空");
        }
        ItemQuoteVersion item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new RuntimeException("零件不存在");
        }
        OrderRfq order = orderMapper.selectById(item.getRfqId());
        if (order == null) {
            throw new RuntimeException("所属订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
        return detail("RFQ", rfqOrderSummary(order), item);
    }

    public PageResult<PartSearchItemRowDTO> searchSalesItems(String partNo, String version, Integer page, Integer pageSize) {
        String safePartNo = requirePartNo(partNo);
        String safeVersion = trimToNull(version);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);

        QueryWrapper<SalesOrderItem> countQuery = new QueryWrapper<>();
        applyPartFilters(countQuery, safePartNo, safeVersion);
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderIdScope(countQuery, "sales_order_id");
        }
        Long total = salesItemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<SalesOrderItem> pageQuery = new QueryWrapper<>();
        applyPartFilters(pageQuery, safePartNo, safeVersion);
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderIdScope(pageQuery, "sales_order_id");
        }
        pageQuery.orderByDesc("id");
        pageQuery.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<SalesOrderItem> items = salesItemMapper.selectList(pageQuery);
        Map<Long, SalesOrderHeader> ordersById = loadSalesOrders(items);
        List<PartSearchItemRowDTO> records = items.stream()
                .map(item -> toSalesRow(item, ordersById.get(item.getSalesOrderId())))
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public PartSearchItemDetailDTO getSalesItemDetail(Long itemId) {
        if (itemId == null) {
            throw new RuntimeException("零件ID不能为空");
        }
        SalesOrderItem item = salesItemMapper.selectById(itemId);
        if (item == null) {
            throw new RuntimeException("零件不存在");
        }
        SalesOrderHeader order = salesHeaderMapper.selectById(item.getSalesOrderId());
        if (order == null) {
            throw new RuntimeException("所属订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
        return detail("SALES_ORDER", salesOrderSummary(order), item);
    }

    private void applyPartFilters(QueryWrapper<?> query, String partNo, String version) {
        query.eq("part_no", partNo);
        if (version != null) {
            query.eq("version", version);
        }
    }

    private Map<Integer, OrderRfq> loadRfqOrders(List<ItemQuoteVersion> items) {
        List<Integer> orderIds = items.stream()
                .map(ItemQuoteVersion::getRfqId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(OrderRfq::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, SalesOrderHeader> loadSalesOrders(List<SalesOrderItem> items) {
        List<Long> orderIds = items.stream()
                .map(SalesOrderItem::getSalesOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return salesHeaderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(SalesOrderHeader::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private PartSearchItemRowDTO toRfqRow(ItemQuoteVersion item, OrderRfq order) {
        PartSearchItemRowDTO row = new PartSearchItemRowDTO();
        row.setId(item.getId() == null ? null : item.getId().longValue());
        row.setSourceType("RFQ");
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setUntaxedUnitPrice(decimal(item.getUntaxedUnitPrice()));
        row.setTaxIncludedUnitPrice(decimal(item.getTaxIncludedUnitPrice()));
        row.setDrawingUrl(item.getDrawingUrl());
        row.setOrderId(item.getRfqId() == null ? null : item.getRfqId().longValue());
        row.setOrderNo(order == null ? null : order.getRfqNo());
        row.setOrderStatus(order == null ? null : order.getStatus());
        row.setItemStatus(item.getStatus());
        return row;
    }

    private PartSearchItemRowDTO toSalesRow(SalesOrderItem item, SalesOrderHeader order) {
        PartSearchItemRowDTO row = new PartSearchItemRowDTO();
        row.setId(item.getId());
        row.setSourceType("SALES_ORDER");
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setUntaxedUnitPrice(item.getUntaxedUnitPrice());
        row.setTaxIncludedUnitPrice(item.getTaxIncludedUnitPrice());
        row.setDrawingUrl(item.getDrawingUrl());
        row.setOrderId(item.getSalesOrderId());
        row.setOrderNo(order == null ? null : order.getSalesOrderNo());
        row.setOrderStatus(order == null ? null : order.getStatus());
        row.setItemStatus(item.getStatus());
        return row;
    }

    private PartSearchItemDetailDTO detail(String sourceType, Map<String, Object> orderSummary, Object itemDetail) {
        PartSearchItemDetailDTO dto = new PartSearchItemDetailDTO();
        dto.setSourceType(sourceType);
        dto.setOrderSummary(orderSummary);
        dto.setItemDetail(itemDetail);
        return dto;
    }

    private Map<String, Object> rfqOrderSummary(OrderRfq order) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", order.getId());
        summary.put("orderNo", order.getRfqNo());
        summary.put("status", order.getStatus());
        summary.put("customerCode", order.getCustomerCode());
        summary.put("customerName", order.getCustomerName());
        summary.put("customerShortName", order.getCustomerShortName());
        summary.put("orderType", order.getOrderType());
        summary.put("createdAt", order.getCreatedAt());
        return summary;
    }

    private Map<String, Object> salesOrderSummary(SalesOrderHeader order) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", order.getId());
        summary.put("orderNo", order.getSalesOrderNo());
        summary.put("status", order.getStatus());
        summary.put("documentNo", order.getDocumentNo());
        summary.put("documentTypeCode", order.getDocumentTypeCode());
        summary.put("customerCode", order.getCustomerCode());
        summary.put("customerName", order.getCustomerName());
        summary.put("customerShortName", order.getCustomerShortName());
        summary.put("customerOrderNo", order.getCustomerOrderNo());
        summary.put("customerProjectNo", order.getCustomerProjectNo());
        summary.put("internalProjectNo", order.getInternalProjectNo());
        summary.put("orderType", order.getOrderType());
        summary.put("u9SyncStatus", order.getU9SyncStatus());
        summary.put("createdAt", order.getCreatedAt());
        summary.put("updatedAt", order.getUpdatedAt());
        return summary;
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String requirePartNo(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new RuntimeException("图号不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
