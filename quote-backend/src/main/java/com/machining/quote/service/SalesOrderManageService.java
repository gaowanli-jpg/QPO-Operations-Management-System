package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderManageService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        if (dataScopeService != null) {
            return dataScopeService.listCustomerOptions(field, keyword);
        }
        return List.of();
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            Long customerId,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderDataScope(query);
        applyOrderFilters(query, customerId, customerName, keyword, status);
        Long total = salesOrderHeaderMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByDesc("updated_at").orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<SalesOrderHeader> orders = salesOrderHeaderMapper.selectList(query);
        return PageResult.of(buildSummaries(orders), total, safePage, safePageSize);
    }

    public Map<String, Object> getOrderDetail(Long salesOrderId) {
        SalesOrderHeader order = requireSalesOrder(salesOrderId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summary", buildSummary(order));
        detail.put("orderInfo", order);
        return detail;
    }

    public PageResult<SalesOrderItem> pageOrderItems(Long salesOrderId, Integer page, Integer pageSize) {
        requireSalesOrder(salesOrderId);
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<SalesOrderItem> countQuery = new QueryWrapper<>();
        countQuery.eq("sales_order_id", salesOrderId);
        Long total = salesOrderItemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<SalesOrderItem> pageQuery = new QueryWrapper<>();
        pageQuery.eq("sales_order_id", salesOrderId)
                .orderByAsc("line_no")
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(salesOrderItemMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    public PageResult<OrderDeliveryTicket> pageOrderTickets(Long salesOrderId, Integer page, Integer pageSize) {
        requireSalesOrder(salesOrderId);
        return pageTicketsForSalesOrder(salesOrderId, page, pageSize);
    }

    public PageResult<ProcessSheetDTO> pageDynamicProcessSheets(Long salesOrderId, Integer page, Integer pageSize) {
        PageResult<OrderDeliveryTicket> ticketPage = pageOrderTickets(salesOrderId, page, pageSize);
        List<ProcessSheetDTO> records = ticketPage.getRecords().stream()
                .map(this::buildProcessSheet)
                .toList();
        return PageResult.of(records, ticketPage.getTotal(), ticketPage.getPage(), ticketPage.getPageSize());
    }

    private PageResult<OrderDeliveryTicket> pageTicketsForSalesOrder(Long salesOrderId, Integer page, Integer pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<OrderDeliveryTicket> countQuery = ticketQuery(salesOrderId);
        Long total = ticketMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<OrderDeliveryTicket> pageQuery = ticketQuery(salesOrderId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(Long salesOrderId) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        if (salesOrderId == null) {
            return query.apply("1 = 0");
        }
        return query.eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                .eq("source_order_id", salesOrderId);
    }

    private Map<String, Object> buildSummary(SalesOrderHeader order) {
        ItemStats itemStats = loadItemStats(List.of(order.getId())).getOrDefault(order.getId(), new ItemStats());
        TicketStats ticketStats = loadTicketStats(List.of(order.getId())).getOrDefault(order.getId(), new TicketStats());
        return buildSummary(order, itemStats, ticketStats);
    }

    private List<Map<String, Object>> buildSummaries(List<SalesOrderHeader> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream()
                .map(SalesOrderHeader::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, ItemStats> itemStatsMap = loadItemStats(orderIds);
        Map<Long, TicketStats> ticketStatsMap = loadTicketStats(orderIds);
        return orders.stream()
                .map(order -> buildSummary(
                        order,
                        itemStatsMap.getOrDefault(order.getId(), new ItemStats()),
                        ticketStatsMap.getOrDefault(order.getId(), new TicketStats())))
                .toList();
    }

    private Map<String, Object> buildSummary(SalesOrderHeader order, ItemStats itemStats, TicketStats ticketStats) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("salesOrderNo", order.getSalesOrderNo());
        row.put("documentNo", order.getDocumentNo());
        row.put("documentTypeCode", order.getDocumentTypeCode());
        row.put("customerId", order.getCustomerId());
        row.put("customerCode", order.getCustomerCode());
        row.put("customerName", order.getCustomerName());
        row.put("customerShortName", order.getCustomerShortName());
        row.put("salesOrderDate", order.getSalesOrderDate());
        row.put("customerOrderNo", order.getCustomerOrderNo());
        row.put("customerProjectNo", order.getCustomerProjectNo());
        row.put("customerOrderTitle", order.getCustomerOrderTitle());
        row.put("orderType", order.getOrderType());
        row.put("internalProjectNo", order.getInternalProjectNo());
        row.put("status", order.getStatus());
        row.put("u9SyncStatus", order.getU9SyncStatus());
        row.put("u9OrderId", order.getU9OrderId());
        row.put("u9OrderCode", order.getU9OrderCode());
        row.put("u9SyncMessage", order.getU9SyncMessage());
        row.put("itemCount", itemStats.itemCount);
        row.put("releasedQuantity", itemStats.releasedQuantity);
        row.put("totalQuantity", itemStats.totalQuantity);
        row.put("drawingMissingCount", itemStats.drawingMissingCount);
        row.put("ticketCount", ticketStats.ticketCount);
        row.put("ticketQty", ticketStats.ticketQty);
        row.put("processSheetCount", ticketStats.ticketCount);
        row.put("warningMessage", order.getWarningMessage());
        row.put("createdAt", order.getCreatedAt());
        row.put("updatedAt", order.getUpdatedAt());
        return row;
    }

    private Map<Long, ItemStats> loadItemStats(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.select(
                "sales_order_id",
                "COUNT(*) AS item_count",
                "COALESCE(SUM(qty), 0) AS total_quantity",
                "COALESCE(SUM(released_qty), 0) AS released_quantity",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS drawing_missing_count"
        );
        query.in("sales_order_id", orderIds);
        query.groupBy("sales_order_id");
        return salesOrderItemMapper.selectMaps(query).stream()
                .map(ItemStats::from)
                .collect(Collectors.toMap(stats -> stats.salesOrderId, Function.identity()));
    }

    private Map<Long, TicketStats> loadTicketStats(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.select(
                "source_order_id",
                "COUNT(*) AS ticket_count",
                "COALESCE(SUM(qty), 0) AS ticket_qty"
        );
        query.eq("source_type", ReleaseSourceType.SALES_ORDER.name());
        query.in("source_order_id", orderIds);
        query.groupBy("source_order_id");
        return ticketMapper.selectMaps(query).stream()
                .map(TicketStats::from)
                .collect(Collectors.toMap(stats -> stats.salesOrderId, Function.identity()));
    }

    private ProcessSheetDTO buildProcessSheet(OrderDeliveryTicket ticket) {
        ProcessSheetDTO sheet = new ProcessSheetDTO();
        sheet.setId(ticket.getId());
        sheet.setRfqId(ticket.getRfqId());
        sheet.setItemId(ticket.getItemId());
        sheet.setInternalProjectNo(ticket.getInternalProjectNo());
        sheet.setItemType(ticket.getItemType());
        sheet.setPartNo(ticket.getPartNo());
        sheet.setVersion(ticket.getVersion());
        sheet.setDescription(ticket.getDescription());
        sheet.setQty(ticket.getQty());
        sheet.setMaterialName(ticket.getMaterialName());
        sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        sheet.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        sheet.setPartNetWeight(ticket.getPartNetWeight() == null ? 0.0 : ticket.getPartNetWeight());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(ticket.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(ticket.getDeliveryDate());
        sheet.setRemark(ticket.getRemark());
        sheet.setProcessRoute(ticket.getProcessRoute());
        return sheet;
    }

    private SalesOrderHeader requireSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader order = salesOrderHeaderMapper.selectById(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(order);
        return order;
    }

    private void applySalesOrderDataScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private void assertSalesOrderDataScope(SalesOrderHeader order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
    }

    private void applyOrderFilters(
            QueryWrapper<SalesOrderHeader> query,
            Long customerId,
            String customerName,
            String keyword,
            String status) {
        if (customerId != null) {
            query.eq("customer_id", customerId);
        }
        if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
        }
        if (!isBlank(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("sales_order_no", value)
                    .or()
                    .like("document_no", value)
                    .or()
                    .like("customer_order_no", value)
                    .or()
                    .like("customer_project_no", value)
                    .or()
                    .like("internal_project_no", value)
                    .or()
                    .like("document_type_code", value)
                    .or()
                    .like("status", value));
        }
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static Number numberValue(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ItemStats {
        private Long salesOrderId;
        private int itemCount;
        private int totalQuantity;
        private int releasedQuantity;
        private int drawingMissingCount;

        private static ItemStats from(Map<String, Object> row) {
            ItemStats stats = new ItemStats();
            stats.salesOrderId = numberValue(row.get("sales_order_id")).longValue();
            stats.itemCount = numberValue(row.get("item_count")).intValue();
            stats.totalQuantity = numberValue(row.get("total_quantity")).intValue();
            stats.releasedQuantity = numberValue(row.get("released_quantity")).intValue();
            stats.drawingMissingCount = numberValue(row.get("drawing_missing_count")).intValue();
            return stats;
        }
    }

    private static class TicketStats {
        private Long salesOrderId;
        private int ticketCount;
        private int ticketQty;

        private static TicketStats from(Map<String, Object> row) {
            TicketStats stats = new TicketStats();
            stats.salesOrderId = numberValue(row.get("source_order_id")).longValue();
            stats.ticketCount = numberValue(row.get("ticket_count")).intValue();
            stats.ticketQty = numberValue(row.get("ticket_qty")).intValue();
            return stats;
        }
    }
}
