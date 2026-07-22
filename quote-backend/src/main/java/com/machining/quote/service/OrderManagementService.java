package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private OrderFilePathService orderFilePathService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

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

    public List<String> listCustomers() {
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
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (drawings == null || drawings.isEmpty()) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }

        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<String, List<ItemQuoteVersion>> itemMap = new HashMap<>();
        for (ItemQuoteVersion item : items) {
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            if (!normalizedPartNo.isEmpty()) {
                itemMap.computeIfAbsent(normalizedPartNo, key -> new ArrayList<>()).add(item);
            }
        }

        File projectDir = orderFilePathService.orderDrawingDirectory(order).toFile();
        if (!projectDir.exists() && !projectDir.mkdirs()) {
            throw new RuntimeException("图纸目录创建失败：" + projectDir.getAbsolutePath());
        }

        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> alreadyBoundItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();

        for (MultipartFile drawing : drawings) {
            String originalFilename = drawing.getOriginalFilename();
            String cleanFileName = cleanFilename(originalFilename);
            if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                continue;
            }

            String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMap.keySet());
            List<ItemQuoteVersion> matchedItems = itemMap.get(partNoFromFileName);
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

            String newFilename = UUID.randomUUID() + ".pdf";
            File dest = new File(projectDir, newFilename);
            try {
                drawing.transferTo(dest);
            } catch (IOException e) {
                throw new RuntimeException("图纸保存失败：" + cleanFileName, e);
            }

            String fileUrl = orderFilePathService.drawingUrl(order, newFilename);
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
                rfqId, drawings.size(), boundItems.size(), alreadyBoundItems.size(), unmatchedDrawings.size(), skippedFiles.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Order supplement drawings completed with warnings rfqId={} unmatchedCount={} skippedCount={}",
                    rfqId, unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

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
            deleteDrawingByUrl(item.getDrawingUrl());
        }
        deleteDirectoryIfInsideUploadRoot(orderFilePathService.uploadRoot().resolve("orders").resolve(String.valueOf(order.getId())));
        if (!isBlank(order.getCustomerName()) && !isBlank(order.getProjectName())) {
            deleteDirectoryIfInsideUploadRoot(orderFilePathService.orderDrawingDirectory(order));
        }
    }

    private List<Map<String, Object>> listMissingDrawingFiles(List<ItemQuoteVersion> items) {
        List<Map<String, Object>> missingFiles = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            if (isBlank(item.getDrawingUrl())) {
                continue;
            }
            try {
                String pathText = URI.create(item.getDrawingUrl()).getPath();
                if (!pathText.contains("/uploads/")) {
                    continue;
                }
                String relative = pathText.substring(pathText.indexOf("/uploads/") + "/uploads/".length());
                Path file = orderFilePathService.uploadRoot().resolve(URLDecoder.decode(relative, StandardCharsets.UTF_8)).normalize();
                if (isInsideUploadRoot(file) && !Files.isRegularFile(file)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("itemId", item.getId());
                    row.put("partNo", item.getPartNo());
                    row.put("version", item.getVersion());
                    row.put("drawingUrl", item.getDrawingUrl());
                    row.put("expectedPath", file.toString());
                    missingFiles.add(row);
                }
            } catch (Exception ignored) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("itemId", item.getId());
                row.put("partNo", item.getPartNo());
                row.put("version", item.getVersion());
                row.put("drawingUrl", item.getDrawingUrl());
                row.put("expectedPath", "");
                missingFiles.add(row);
            }
        }
        return missingFiles;
    }

    private void deleteDrawingByUrl(String drawingUrl) {
        if (isBlank(drawingUrl) || !drawingUrl.contains("/uploads/")) {
            return;
        }
        try {
            String pathText = URI.create(drawingUrl).getPath();
            String relative = pathText.substring(pathText.indexOf("/uploads/") + "/uploads/".length());
            Path file = orderFilePathService.uploadRoot().resolve(URLDecoder.decode(relative, StandardCharsets.UTF_8)).normalize();
            if (isInsideUploadRoot(file) && Files.isRegularFile(file)) {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            log.warn("Order drawing delete skipped drawingUrl={} reason={}", drawingUrl, e.getMessage());
        }
    }

    private void deleteDirectoryIfInsideUploadRoot(Path directory) {
        Path normalized = directory.normalize();
        if (!isInsideUploadRoot(normalized) || !Files.exists(normalized)) {
            return;
        }
        try {
            Files.walk(normalized)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Order directory file delete skipped path={} reason={}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Order directory delete skipped path={} reason={}", normalized, e.getMessage());
        }
    }

    private boolean isInsideUploadRoot(Path path) {
        return path.toAbsolutePath().normalize().startsWith(orderFilePathService.uploadRoot());
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
}
