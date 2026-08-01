package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderStatusChangeReq;
import com.machining.quote.dto.OrderStatusChangeResult;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderManageService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderManageService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTION_DISCARD = "SALES_ORDER_DISCARD";
    private static final String ACTION_RESTORE = "SALES_ORDER_RESTORE";
    private static final String ACTION_DRAWING_SUPPLEMENT = "SALES_ORDER_DRAWING_SUPPLEMENT";

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
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private BusinessFileService businessFileService;

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

    public Map<String, Object> getDrawingSummary(Long salesOrderId) {
        SalesOrderHeader order = requireSalesOrder(salesOrderId);
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.select(
                "COUNT(*) AS item_count",
                "SUM(CASE WHEN drawing_url IS NOT NULL AND drawing_url <> '' THEN 1 ELSE 0 END) AS bound_drawing_count",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS missing_drawing_count"
        );
        query.eq("sales_order_id", salesOrderId);
        Map<String, Object> row = salesOrderItemMapper.selectMaps(query).stream().findFirst().orElse(Map.of());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", order.getId());
        summary.put("salesOrderNo", order.getSalesOrderNo());
        summary.put("itemCount", numberValue(row.get("item_count")).longValue());
        summary.put("boundDrawingCount", numberValue(row.get("bound_drawing_count")).longValue());
        summary.put("missingDrawingCount", numberValue(row.get("missing_drawing_count")).longValue());
        return summary;
    }

    @Transactional
    public OrderStatusChangeResult discard(Long salesOrderId, OrderStatusChangeReq req) {
        return changeSalesOrderStatus(
                salesOrderId,
                req,
                ACTION_DISCARD,
                "废弃销售订单",
                QuoteStatusWorkflow.QUOTED,
                QuoteStatusWorkflow.DISCARDED,
                "只有已报价且未生成提单的销售订单可以废弃。"
        );
    }

    @Transactional
    public OrderStatusChangeResult restore(Long salesOrderId, OrderStatusChangeReq req) {
        return changeSalesOrderStatus(
                salesOrderId,
                req,
                ACTION_RESTORE,
                "恢复销售订单",
                QuoteStatusWorkflow.DISCARDED,
                QuoteStatusWorkflow.QUOTED,
                "只有废弃状态的销售订单可以恢复。"
        );
    }

    @Transactional
    public Map<String, Object> supplementItemDrawings(Long itemId, List<MultipartFile> drawings) {
        if (itemId == null) {
            throw new RuntimeException("销售订单零件 ID 不能为空");
        }
        if (drawings == null || drawings.isEmpty()) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }
        if (businessFileService == null) {
            throw new RuntimeException("图纸文件服务不可用");
        }

        SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("销售订单零件不存在");
        }
        SalesOrderHeader order = requireSalesOrder(item.getSalesOrderId());

        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> alreadyBoundItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();

        for (MultipartFile drawing : drawings) {
            String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
            if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                skippedFiles.add(buildFileResult(cleanFileName, item.getPartNo(), "非 PDF 文件"));
                continue;
            }
            if (!isBlank(item.getDrawingUrl())) {
                alreadyBoundItems.add(buildSalesItemResult(item, cleanFileName, "该销售订单零件已有图纸，未覆盖"));
                continue;
            }

            String orderNo = firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()));
            BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                    order.getCustomerName(),
                    orderNo,
                    drawing,
                    ACTION_DRAWING_SUPPLEMENT,
                    String.valueOf(itemId)
            );
            item.setDrawingUrl(storedFile.publicPath());
            item.setHasDrawing(true);
            salesOrderItemMapper.updateById(item);
            boundItems.add(buildSalesItemResult(item, cleanFileName, "已绑定"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "补传图纸处理完成，成功绑定 " + boundItems.size() + " 份。");
        result.put("boundCount", boundItems.size());
        result.put("alreadyBoundCount", alreadyBoundItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("boundItems", boundItems);
        result.put("alreadyBoundItems", alreadyBoundItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);

        if (auditService != null && !boundItems.isEmpty()) {
            auditService.record(
                    ACTION_DRAWING_SUPPLEMENT,
                    "sales_order_item",
                    itemId,
                    null,
                    result,
                    "MARKET",
                    "SUCCESS",
                    AuditService.labelForAction(ACTION_DRAWING_SUPPLEMENT),
                    null
            );
        }
        log.info("Sales order drawing supplement completed salesOrderId={} itemId={} uploadCount={} boundCount={} alreadyBoundCount={} skippedCount={}",
                order.getId(), itemId, drawings.size(), boundItems.size(), alreadyBoundItems.size(), skippedFiles.size());
        return result;
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

    private OrderStatusChangeResult changeSalesOrderStatus(
            Long salesOrderId,
            OrderStatusChangeReq req,
            String auditAction,
            String actionName,
            String requiredStatus,
            String targetStatus,
            String rejectionMessage) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        long startedAt = System.nanoTime();
        String fromStatus = null;
        Long ticketCount = null;
        try {
            log.info("Sales order status change started action={} salesOrderId={}", auditAction, salesOrderId);
            SalesOrderHeader order = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
            if (order == null) {
                throw new RuntimeException("销售订单不存在");
            }
            fromStatus = order.getStatus();
            assertSalesOrderDataScope(order);
            ticketCount = countSalesOrderTickets(salesOrderId);
            if (!requiredStatus.equals(fromStatus) || (ACTION_DISCARD.equals(auditAction) && ticketCount != null && ticketCount > 0)) {
                throw new RuntimeException(rejectionMessage);
            }

            List<SalesOrderItem> items = listSalesOrderItems(salesOrderId);
            Map<String, Long> beforeItemSummary = statusSummary(items);
            order.setStatus(targetStatus);
            salesOrderHeaderMapper.updateById(order);

            int updatedItemCount = 0;
            for (SalesOrderItem item : items) {
                if (!shouldUpdateItemStatus(item, auditAction)) {
                    continue;
                }
                item.setStatus(targetStatus);
                salesOrderItemMapper.updateById(item);
                updatedItemCount++;
            }

            String message = buildSalesOrderStatusMessage(actionName, order, fromStatus, targetStatus, req);
            if (auditService != null) {
                auditService.record(
                        auditAction,
                        "sales_order_header",
                        salesOrderId,
                        beforeData(fromStatus, beforeItemSummary, ticketCount, req),
                        afterData(targetStatus, updatedItemCount, statusSummary(items)),
                        "MARKET",
                        "SUCCESS",
                        message,
                        null
                );
            }

            OrderStatusChangeResult result = new OrderStatusChangeResult();
            result.setSalesOrderId(order.getId());
            result.setSalesOrderNo(order.getSalesOrderNo());
            result.setFromStatus(fromStatus);
            result.setToStatus(targetStatus);
            result.setUpdatedItemCount(updatedItemCount);
            result.setMessage(message);
            log.info("Sales order status change completed action={} salesOrderId={} salesOrderNo={} fromStatus={} toStatus={} updatedItemCount={} ticketCount={} durationMs={}",
                    auditAction, salesOrderId, order.getSalesOrderNo(), fromStatus, targetStatus, updatedItemCount,
                    ticketCount, elapsedMs(startedAt));
            return result;
        } catch (RuntimeException e) {
            log.warn("Sales order status change rejected action={} salesOrderId={} fromStatus={} ticketCount={} reason={}",
                    auditAction, salesOrderId, fromStatus, ticketCount, safeMessage(e));
            throw e;
        }
    }

    private Long countSalesOrderTickets(Long salesOrderId) {
        return ticketMapper.selectCount(ticketQuery(salesOrderId));
    }

    private List<SalesOrderItem> listSalesOrderItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("line_no").orderByAsc("id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        return items == null ? List.of() : items;
    }

    private boolean shouldUpdateItemStatus(SalesOrderItem item, String auditAction) {
        if (item == null) {
            return false;
        }
        if (ACTION_RESTORE.equals(auditAction)) {
            return QuoteStatusWorkflow.DISCARDED.equals(item.getStatus());
        }
        return safeInt(item.getReleasedQty()) == 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isReleasedOrderStatus(item.getStatus());
    }

    private Map<String, Long> statusSummary(List<SalesOrderItem> items) {
        Map<String, Long> summary = new LinkedHashMap<>();
        if (items == null) {
            return summary;
        }
        for (SalesOrderItem item : items) {
            String status = item == null || item.getStatus() == null ? "未填写" : item.getStatus();
            summary.put(status, summary.getOrDefault(status, 0L) + 1);
        }
        return summary;
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

    private Map<String, Object> beforeData(
            String fromStatus,
            Map<String, Long> itemStatusSummary,
            Long ticketCount,
            OrderStatusChangeReq req) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", fromStatus);
        data.put("itemStatusSummary", itemStatusSummary);
        data.put("ticketCount", ticketCount == null ? 0 : ticketCount);
        String reason = safeReason(req);
        if (reason != null) {
            data.put("reason", reason);
        }
        return data;
    }

    private Map<String, Object> afterData(String toStatus, int updatedItemCount, Map<String, Long> itemStatusSummary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", toStatus);
        data.put("updatedItemCount", updatedItemCount);
        data.put("itemStatusSummary", itemStatusSummary);
        return data;
    }

    private String buildSalesOrderStatusMessage(
            String actionName,
            SalesOrderHeader order,
            String fromStatus,
            String toStatus,
            OrderStatusChangeReq req) {
        String orderNo = firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()));
        String message = actionName + "：" + orderNo + "，" + fromStatus + " -> " + toStatus;
        String reason = safeReason(req);
        if (reason != null) {
            message += "；原因：" + reason;
        }
        return message;
    }

    private String safeReason(OrderStatusChangeReq req) {
        if (req == null || req.getReason() == null) {
            return null;
        }
        String reason = req.getReason().trim();
        if (reason.isEmpty()) {
            return null;
        }
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }

    private String safeMessage(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return originalFilename.replaceAll(".*[\\\\/]", "").trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Map<String, Object> buildSalesItemResult(SalesOrderItem item, String fileName, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", item.getId());
        result.put("partNo", item.getPartNo());
        result.put("version", item.getVersion());
        result.put("description", item.getDescription());
        result.put("fileName", fileName);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> buildFileResult(String fileName, String partNo, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("partNo", partNo);
        result.put("reason", reason);
        return result;
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

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
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
