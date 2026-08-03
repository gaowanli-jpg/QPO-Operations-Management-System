package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private OrderFilePathService orderFilePathService;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public List<Map<String, Object>> listOrderSummaries(String customerName) {
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        if (!isBlank(customerName)) {
            qw.eq("customer_name", customerName.trim());
        }
        qw.orderByDesc("id");
        return buildSummaries(orderMapper.selectList(qw));
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerName, keyword, status);
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<OrderRfq> orders = orderMapper.selectList(qw);
        return PageResult.of(buildSummaries(orders), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerName, keyword, status);
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listQuoteProcessCandidateOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.canReadQuoteProcess(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerName, keyword, null);
        if (normalizedStatus == null) {
            qw.in("status", QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.QUOTED);
        } else {
            qw.eq("status", normalizedStatus);
        }
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listReleaseCandidateOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.canGenerateDeliveryTicket(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerName, keyword, null);
        if (normalizedStatus == null) {
            qw.in("status", QuoteStatusWorkflow.QUOTED, QuoteStatusWorkflow.PARTIALLY_RELEASED);
        } else {
            qw.eq("status", normalizedStatus);
        }
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<Map<String, Object>> listReleaseOrderSources(
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(listOrders(customerName, keyword, status, 1, 100).getRecords().stream()
                .map(this::buildReleaseRfqSourceRow)
                .toList());
        rows.addAll(listReleaseSalesOrderCandidates(customerName, keyword, status).stream()
                .map(this::buildReleaseSalesOrderSourceRow)
                .toList());
        rows.sort((left, right) -> Long.compare(safeLong(right.get("sortId")), safeLong(left.get("sortId"))));
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        qw.select("DISTINCT customer_name");
        qw.isNotNull("customer_name");
        qw.ne("customer_name", "");
        qw.orderByAsc("customer_name");
        return orderMapper.selectList(qw).stream()
                .map(OrderRfq::getCustomerName)
                .filter(customerName -> !isBlank(customerName))
                .toList();
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        return listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
    }

    public Map<String, Object> getOrderDetail(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("summary", buildSummary(order));
        detail.put("orderInfo", order);
        detail.put("fileRoot", buildOrderFileRoot(order));
        detail.put("drawingSummary", getDrawingSummary(rfqId));
        return detail;
    }

    public PageResult<ItemQuoteVersion> pageOrderItems(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<ItemQuoteVersion> countQuery = new QueryWrapper<>();
        countQuery.eq("rfq_id", rfqId);
        Long total = itemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<ItemQuoteVersion> pageQuery = new QueryWrapper<>();
        pageQuery.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(itemMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    public PageResult<OrderDeliveryTicket> pageOrderTickets(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<OrderDeliveryTicket> countQuery = new QueryWrapper<>();
        countQuery.eq("rfq_id", rfqId);
        Long total = ticketMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<OrderDeliveryTicket> pageQuery = new QueryWrapper<>();
        pageQuery.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    public PageResult<ProcessSheetDTO> pageDynamicProcessSheets(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        return deliveryService.pageProcessSheets(rfqId, page, pageSize);
    }

    public Map<String, Object> getDrawingSummary(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.select(
                "COUNT(*) AS item_count",
                "SUM(CASE WHEN drawing_url IS NOT NULL AND drawing_url <> '' THEN 1 ELSE 0 END) AS bound_drawing_count",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS missing_drawing_count"
        );
        qw.eq("rfq_id", rfqId);
        Map<String, Object> row = itemMapper.selectMaps(qw).stream().findFirst().orElse(Map.of());
        long itemCount = numberValue(row.get("item_count")).longValue();
        long boundCount = numberValue(row.get("bound_drawing_count")).longValue();
        long missingCount = numberValue(row.get("missing_drawing_count")).longValue();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", order.getId());
        summary.put("rfqNo", order.getRfqNo());
        summary.put("itemCount", itemCount);
        summary.put("boundDrawingCount", boundCount);
        summary.put("missingDrawingCount", missingCount);
        summary.put("fileRoot", buildOrderFileRoot(order));
        return summary;
    }

    @Transactional
    public Map<String, Object> supplementDrawings(Integer rfqId, List<MultipartFile> drawings) {
        return supplementDrawings(rfqId, drawings, null);
    }

    @Transactional
    public Map<String, Object> supplementDrawings(
            Integer rfqId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings)) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }

        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<String, List<ItemQuoteVersion>> itemMapByPartNo = new HashMap<>();
        Map<String, List<ItemQuoteVersion>> itemMapByPartNoVersion = new HashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities = new HashSet<>();
        for (ItemQuoteVersion item : items) {
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            if (!normalizedPartNo.isEmpty()) {
                itemMapByPartNo.computeIfAbsent(normalizedPartNo, key -> new ArrayList<>()).add(item);
            }
            String normalizedVersion = DrawingPartNoResolver.normalizeVersion(item.getVersion());
            if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                itemMapByPartNoVersion
                        .computeIfAbsent(itemKey(normalizedPartNo, normalizedVersion), key -> new ArrayList<>())
                        .add(item);
                itemIdentities.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
            }
        }

        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> alreadyBoundItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();

        if (versionDrawings != null) {
            for (MultipartFile drawing : versionDrawings) {
                String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
                if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                    skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                    continue;
                }

                DrawingPartNoResolver.DrawingIdentity identity =
                        DrawingPartNoResolver.extractPartNoVersion(cleanFileName, itemIdentities);
                String partNoFromFileName = identity == null
                        ? DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet())
                        : identity.partNo();
                if (identity == null) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
                    continue;
                }
                List<ItemQuoteVersion> matchedItems = itemMapByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
                    continue;
                }
                if (matchedItems.stream().allMatch(item -> !isBlank(item.getDrawingUrl()))) {
                    for (ItemQuoteVersion item : matchedItems) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                    }
                    continue;
                }

                String fileUrl = businessFileService
                        .saveDrawing(order, drawing, "ORDER_SUPPLEMENT_DRAWING", String.valueOf(rfqId))
                        .publicPath();
                for (ItemQuoteVersion item : matchedItems) {
                    if (!isBlank(item.getDrawingUrl())) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                        continue;
                    }
                    item.setDrawingUrl(fileUrl);
                    item.setStatus(QuoteStatusWorkflow.UNPRICED);
                    itemMapper.updateById(item);
                    boundItems.add(buildItemResult(item, cleanFileName, "已绑定"));
                }
            }
        }

        if (drawings != null) {
            for (MultipartFile drawing : drawings) {
                String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
                if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                    skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                    continue;
                }

                String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet());
                List<ItemQuoteVersion> matchedItems = itemMapByPartNo.get(partNoFromFileName);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
                    continue;
                }
                if (matchedItems.stream().allMatch(item -> !isBlank(item.getDrawingUrl()))) {
                    for (ItemQuoteVersion item : matchedItems) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                    }
                    continue;
                }

                String fileUrl = businessFileService
                        .saveDrawing(order, drawing, "ORDER_SUPPLEMENT_DRAWING", String.valueOf(rfqId))
                        .publicPath();
                for (ItemQuoteVersion item : matchedItems) {
                    if (!isBlank(item.getDrawingUrl())) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                        continue;
                    }
                    item.setDrawingUrl(fileUrl);
                    item.setStatus(QuoteStatusWorkflow.UNPRICED);
                    itemMapper.updateById(item);
                    boundItems.add(buildItemResult(item, cleanFileName, "已绑定"));
                }
            }
        }

        if (!boundItems.isEmpty()) {
            auditService.record("ORDER_SUPPLEMENT_DRAWINGS", "order_rfq", rfqId, null, boundItems);
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
        log.info("Order supplement drawings completed rfqId={} uploadCount={} boundCount={} alreadyBoundCount={} unmatchedCount={} skippedCount={}",
                rfqId, fileCount(drawings) + fileCount(versionDrawings), boundItems.size(), alreadyBoundItems.size(), unmatchedDrawings.size(), skippedFiles.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Order supplement drawings completed with warnings rfqId={} unmatchedCount={} skippedCount={}",
                    rfqId, unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

    /**
     * @deprecated physical RFQ deletion is a legacy compatibility path. New order governance uses discard/restore.
     */
    @Deprecated
    @Transactional
    public void deleteOrderCompletely(Integer rfqId, boolean deleteFiles) {
        OrderRfq order = requireOrderForUpdate(rfqId);

        List<ItemQuoteVersion> items = listItems(rfqId);
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        List<ProcessSheetDTO> processSheets = listProcessSheets(rfqId);

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", rfqId);
        ticketMapper.delete(ticketQw);

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("rfq_id", rfqId);
        itemMapper.delete(itemQw);
        orderMapper.deleteById(rfqId);

        if (deleteFiles) {
            deleteOrderFiles(order, items);
        }
        auditService.record("ORDER_DELETE_COMPLETELY", "order_rfq", rfqId, order, Map.of(
                "itemCount", items.size(),
                "deleteFiles", deleteFiles
        ));
        log.info("Order delete completed rfqId={} deleteFiles={} itemCount={} ticketCount={} dynamicProcessSheetCount={}",
                rfqId, deleteFiles, items.size(), tickets.size(), processSheets.size());
    }

    private Map<String, Object> buildSummary(OrderRfq order) {
        ItemStats itemStats = loadItemStats(List.of(order.getId())).getOrDefault(order.getId(), new ItemStats());
        TicketStats ticketStats = loadTicketStats(List.of(order.getId())).getOrDefault(order.getId(), new TicketStats());
        return buildSummary(order, itemStats, ticketStats);
    }

    private List<Map<String, Object>> buildSummaries(List<OrderRfq> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Integer> rfqIds = orders.stream()
                .map(OrderRfq::getId)
                .filter(id -> id != null)
                .toList();
        Map<Integer, ItemStats> itemStatsMap = loadItemStats(rfqIds);
        Map<Integer, TicketStats> ticketStatsMap = loadTicketStats(rfqIds);

        return orders.stream()
                .map(order -> buildSummary(order,
                        itemStatsMap.getOrDefault(order.getId(), new ItemStats()),
                        ticketStatsMap.getOrDefault(order.getId(), new TicketStats())))
                .toList();
    }

    private Map<String, Object> buildSummary(OrderRfq order, ItemStats itemStats, TicketStats ticketStats) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", order.getId());
        summary.put("rfqNo", order.getRfqNo());
        summary.put("customerName", order.getCustomerName());
        summary.put("projectName", order.getProjectName());
        summary.put("status", order.getStatus());
        summary.put("createdAt", order.getCreatedAt());
        summary.put("itemCount", itemStats.itemCount);
        summary.put("quotedCount", itemStats.quotedCount);
        summary.put("missingDrawingCount", itemStats.missingDrawingCount);
        summary.put("ticketCount", ticketStats.ticketCount);
        summary.put("ticketQty", ticketStats.ticketQty);
        summary.put("processSheetCount", ticketStats.ticketCount);
        summary.put("fileRoot", buildOrderFileRoot(order));
        return summary;
    }

    private List<SalesOrderHeader> listReleaseSalesOrderCandidates(String customerName, String keyword, String status) {
        if (salesOrderHeaderMapper == null) {
            return List.of();
        }
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.canReadReleaseWorkspace(normalizedStatus)) {
            return List.of();
        }
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderFilters(query, customerName, keyword, status);
        if (normalizedStatus == null) {
            query.in("status", QuoteStatusWorkflow.QUOTED, QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED);
        }
        query.orderByDesc("id").last("LIMIT 100");
        return salesOrderHeaderMapper.selectList(query).stream()
                .filter(this::canAccessSalesOrder)
                .toList();
    }

    private Map<String, Object> buildReleaseRfqSourceRow(OrderRfq order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("sourceType", "RFQ");
        row.put("sourceOrderId", order.getId());
        row.put("sourceOrderNo", order.getRfqNo());
        row.put("rfqNo", order.getRfqNo());
        row.put("customerName", order.getCustomerName());
        row.put("status", order.getStatus());
        row.put("orderType", order.getOrderType());
        row.put("createdAt", order.getCreatedAt());
        row.put("sortId", order.getId());
        return row;
    }

    private Map<String, Object> buildReleaseSalesOrderSourceRow(SalesOrderHeader order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("sourceType", "SALES_ORDER");
        row.put("sourceOrderId", order.getId());
        row.put("sourceOrderNo", order.getSalesOrderNo());
        row.put("salesOrderNo", order.getSalesOrderNo());
        row.put("customerName", order.getCustomerName());
        row.put("customerOrderNo", order.getCustomerOrderNo());
        row.put("customerProjectNo", order.getCustomerProjectNo());
        row.put("internalProjectNo", order.getInternalProjectNo());
        row.put("orderType", order.getOrderType());
        row.put("status", order.getStatus());
        row.put("createdAt", order.getCreatedAt());
        row.put("sortId", order.getId());
        return row;
    }

    private void applySalesOrderFilters(QueryWrapper<SalesOrderHeader> query, String customerName, String keyword, String status) {
        if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
        }
        if (!isBlank(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("sales_order_no", text)
                    .or()
                    .like("customer_order_no", text)
                    .or()
                    .like("internal_project_no", text)
                    .or()
                    .like("status", text));
        }
    }

    private boolean canAccessSalesOrder(SalesOrderHeader order) {
        if (dataScopeService == null || order == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(toOrderScopeProxy(order));
            return true;
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("No data permission for order ")) {
                return false;
            }
            throw e;
        }
    }

    private OrderRfq toOrderScopeProxy(SalesOrderHeader order) {
        OrderRfq proxy = new OrderRfq();
        proxy.setId(order.getId() == null ? null : order.getId().intValue());
        proxy.setCustomerName(order.getCustomerName());
        proxy.setCreatedBy(order.getCreatedBy() == null ? null : order.getCreatedBy().intValue());
        proxy.setOwnerUserId(order.getOwnerUserId());
        return proxy;
    }

    private Map<Integer, ItemStats> loadItemStats(List<Integer> rfqIds) {
        if (rfqIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.select(
                "rfq_id",
                "COUNT(*) AS item_count",
                "SUM(CASE WHEN final_unit_price IS NOT NULL THEN 1 ELSE 0 END) AS quoted_count",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS missing_drawing_count"
        );
        qw.in("rfq_id", rfqIds);
        qw.groupBy("rfq_id");
        return itemMapper.selectMaps(qw).stream()
                .map(ItemStats::from)
                .collect(Collectors.toMap(stats -> stats.rfqId, Function.identity()));
    }

    private Map<Integer, TicketStats> loadTicketStats(List<Integer> rfqIds) {
        if (rfqIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.select("rfq_id", "COUNT(*) AS ticket_count", "COALESCE(SUM(qty), 0) AS ticket_qty");
        qw.in("rfq_id", rfqIds);
        qw.groupBy("rfq_id");
        return ticketMapper.selectMaps(qw).stream()
                .map(TicketStats::from)
                .collect(Collectors.toMap(stats -> stats.rfqId, Function.identity()));
    }

    private void applyOrderFilters(QueryWrapper<OrderRfq> qw, String customerName, String keyword, String status) {
        if (!isBlank(customerName)) {
            qw.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            qw.eq("status", status.trim());
        }
        if (!isBlank(keyword)) {
            String text = keyword.trim();
            qw.and(wrapper -> wrapper
                    .like("rfq_no", text)
                    .or()
                    .like("status", text));
        }
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

    private <T> PageResult<T> pageRows(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }

    private static Number numberValue(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private static long safeLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static class ItemStats {
        private Integer rfqId;
        private int itemCount;
        private int quotedCount;
        private int missingDrawingCount;

        private static ItemStats from(Map<String, Object> row) {
            ItemStats stats = new ItemStats();
            stats.rfqId = numberValue(row.get("rfq_id")).intValue();
            stats.itemCount = numberValue(row.get("item_count")).intValue();
            stats.quotedCount = numberValue(row.get("quoted_count")).intValue();
            stats.missingDrawingCount = numberValue(row.get("missing_drawing_count")).intValue();
            return stats;
        }
    }

    private static class TicketStats {
        private Integer rfqId;
        private int ticketCount;
        private int ticketQty;

        private static TicketStats from(Map<String, Object> row) {
            TicketStats stats = new TicketStats();
            stats.rfqId = numberValue(row.get("rfq_id")).intValue();
            stats.ticketCount = numberValue(row.get("ticket_count")).intValue();
            stats.ticketQty = numberValue(row.get("ticket_qty")).intValue();
            return stats;
        }
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderDataScope(order);
        return order;
    }

    private OrderRfq requireOrderForUpdate(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectByIdForUpdate(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderDataScope(order);
        return order;
    }

    private void applyOrderDataScope(QueryWrapper<OrderRfq> qw) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(qw);
        }
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }
    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return ticketMapper.selectList(qw);
    }

    private List<ProcessSheetDTO> listProcessSheets(Integer rfqId) {
        return deliveryService.listProcessSheets(rfqId);
    }

    private void deleteOrderFiles(OrderRfq order, List<ItemQuoteVersion> items) {
        for (ItemQuoteVersion item : items) {
            businessFileService.deleteByPublicPath(item.getDrawingUrl());
        }
    }

    private String buildOrderFileRoot(OrderRfq order) {
        return orderFilePathService.orderDrawingDirectory(order).toString();
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return originalFilename.replaceAll(".*[\\\\/]", "").trim();
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + DrawingPartNoResolver.normalizeVersion(version);
    }

    private boolean isEmptyFiles(List<MultipartFile> files) {
        return files == null || files.isEmpty();
    }

    private int fileCount(List<MultipartFile> files) {
        return files == null ? 0 : files.size();
    }

    private Map<String, Object> buildItemResult(ItemQuoteVersion item, String fileName, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
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

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean matchesCustomerKeyword(String customerName, String field, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String normalizedField = field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase();
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return customerName != null && customerName.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerCode(customerName);
        option.setCustomerShortName(customerName);
        option.setCustomerName(customerName);
        return option;
    }
}
