package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
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
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class SalesOrderManageService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderManageService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTION_DISCARD = "SALES_ORDER_DISCARD";
    private static final String ACTION_RESTORE = "SALES_ORDER_RESTORE";
    private static final String ACTION_ITEM_EXCLUDE = "SALES_ORDER_ITEM_U9_CHANGE_EXCLUDE";
    private static final String ACTION_ITEM_RESTORE = "SALES_ORDER_ITEM_U9_CHANGE_RESTORE";
    private static final String ACTION_DRAWING_SUPPLEMENT = "SALES_ORDER_DRAWING_SUPPLEMENT";
    private static final String ACTION_DRAWING_REPLACE = "SALES_ORDER_DRAWING_REPLACE";
    private static final String U9_STATUS_SYNCED = "已同步";
    private static final String U9_STATUS_SYNCING = "同步中";
    private static final String U9_STATUS_PENDING_CONFIRMATION = "结果待确认";

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
    @Autowired(required = false)
    private ProcessRouteSnapshotService processRouteSnapshotService = new ProcessRouteSnapshotService();

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        if (dataScopeService != null) {
            return dataScopeService.listCustomerOptions(field, keyword);
        }
        return List.of();
    }

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (customerMasterService != null) {
            return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        if (dataScopeService != null) {
            return dataScopeService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        return PageResult.of(List.of(), 0, normalizePage(page), normalizePageSize(pageSize));
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            Long customerId,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        return listOrderSummaries(customerId, null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            Long customerId,
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderDataScope(query);
        applyOrderFilters(query, customerId, customerCode, customerName, keyword, status);
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
        summary.put("missingItems", listSalesOrderItems(salesOrderId).stream()
                .filter(item -> isBlank(item.getDrawingUrl()))
                .map(this::buildMissingSalesItem)
                .toList());
        return summary;
    }

    @Transactional
    public OrderStatusChangeResult discard(Long salesOrderId, OrderStatusChangeReq req) {
        return changeSalesOrderStatus(
                salesOrderId,
                req,
                ACTION_DISCARD,
                "废弃销售订单",
                QuoteStatusWorkflow.RELEASED,
                QuoteStatusWorkflow.DISCARDED,
                "只有已生成提单且未同步 U9 的销售订单可以废弃。"
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
                QuoteStatusWorkflow.RELEASED,
                "只有废弃状态的销售订单可以恢复。"
        );
    }

    @Transactional
    public OrderStatusChangeResult discardItemForU9Change(Long salesOrderId, Long itemId, OrderStatusChangeReq req) {
        return changeItemU9ChangeExclusion(salesOrderId, itemId, req, true);
    }

    @Transactional
    public OrderStatusChangeResult restoreItemForU9Change(Long salesOrderId, Long itemId, OrderStatusChangeReq req) {
        return changeItemU9ChangeExclusion(salesOrderId, itemId, req, false);
    }

    @Transactional
    public Map<String, Object> supplementOrderDrawings(Long salesOrderId, List<MultipartFile> drawings) {
        return supplementOrderDrawings(salesOrderId, drawings, null);
    }

    @Transactional
    public Map<String, Object> supplementOrderDrawings(
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        return supplementOrderDrawings(salesOrderId, drawings, versionDrawings, null, null);
    }

    @Transactional
    public Map<String, Object> supplementOrderDrawings(
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings) && isEmptyFiles(drawingFolderFiles)) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }
        if (businessFileService == null) {
            throw new RuntimeException("图纸文件服务不可用");
        }

        SalesOrderHeader order = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(order);
        if (!isEmptyFiles(drawingFolderFiles)) {
            return supplementOrderDrawingFolder(order, drawingFolderFiles, drawingFolderName);
        }
        List<SalesOrderItem> items = listSalesOrderItems(salesOrderId);
        Map<String, List<SalesOrderItem>> itemMapByPartNo = new HashMap<>();
        Map<String, List<SalesOrderItem>> itemMapByPartNoVersion = new HashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities = new HashSet<>();
        for (SalesOrderItem item : items) {
            String normalizedPartNo = DrawingPartNoResolver.normalizePartNo(item.getPartNo());
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
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该销售订单对应零件"));
                    continue;
                }
                List<SalesOrderItem> matchedItems = itemMapByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该销售订单对应零件"));
                    continue;
                }
                if (matchedItems.stream().allMatch(item -> !isBlank(item.getDrawingUrl()))) {
                    for (SalesOrderItem item : matchedItems) {
                        alreadyBoundItems.add(buildSalesItemResult(item, cleanFileName, "该销售订单零件已有图纸，未覆盖"));
                    }
                    continue;
                }

                String orderNo = firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()));
                BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        order.getCustomerName(),
                        orderNo,
                        drawing,
                        ACTION_DRAWING_SUPPLEMENT,
                        String.valueOf(salesOrderId)
                );
                for (SalesOrderItem item : matchedItems) {
                    if (!isBlank(item.getDrawingUrl())) {
                        alreadyBoundItems.add(buildSalesItemResult(item, cleanFileName, "该销售订单零件已有图纸，未覆盖"));
                        continue;
                    }
                    item.setDrawingUrl(storedFile.publicPath());
                    item.setHasDrawing(true);
                    salesOrderItemMapper.updateById(item);
                    boundItems.add(buildSalesItemResult(item, cleanFileName, "已绑定"));
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
                List<SalesOrderItem> matchedItems = itemMapByPartNo.get(partNoFromFileName);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该销售订单对应零件"));
                    continue;
                }
                if (matchedItems.stream().allMatch(item -> !isBlank(item.getDrawingUrl()))) {
                    for (SalesOrderItem item : matchedItems) {
                        alreadyBoundItems.add(buildSalesItemResult(item, cleanFileName, "该销售订单零件已有图纸，未覆盖"));
                    }
                    continue;
                }

                String orderNo = firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()));
                BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        order.getCustomerName(),
                        orderNo,
                        drawing,
                        ACTION_DRAWING_SUPPLEMENT,
                        String.valueOf(salesOrderId)
                );
                for (SalesOrderItem item : matchedItems) {
                    if (!isBlank(item.getDrawingUrl())) {
                        alreadyBoundItems.add(buildSalesItemResult(item, cleanFileName, "该销售订单零件已有图纸，未覆盖"));
                        continue;
                    }
                    item.setDrawingUrl(storedFile.publicPath());
                    item.setHasDrawing(true);
                    salesOrderItemMapper.updateById(item);
                    boundItems.add(buildSalesItemResult(item, cleanFileName, "已绑定"));
                }
            }
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
                    "sales_order_header",
                    salesOrderId,
                    null,
                    buildSupplementDrawingAuditSummary(
                            order,
                            drawings,
                            versionDrawings,
                            boundItems,
                            alreadyBoundItems,
                            unmatchedDrawings,
                            skippedFiles),
                    "MARKET",
                    "SUCCESS",
                    AuditService.labelForAction(ACTION_DRAWING_SUPPLEMENT),
                    null
            );
        }
        log.info("Sales order drawing supplement completed salesOrderId={} uploadCount={} boundCount={} alreadyBoundCount={} unmatchedCount={} skippedCount={}",
                order.getId(), fileCount(drawings) + fileCount(versionDrawings), boundItems.size(), alreadyBoundItems.size(), unmatchedDrawings.size(), skippedFiles.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Sales order drawing supplement completed with warnings salesOrderId={} unmatchedCount={} skippedCount={}",
                    order.getId(), unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> replaceOrderDrawings(
            Long salesOrderId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings) && isEmptyFiles(drawingFolderFiles)) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }
        if (businessFileService == null) {
            throw new RuntimeException("图纸文件服务不可用");
        }

        SalesOrderHeader order = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(order);
        if (!isEmptyFiles(drawingFolderFiles)) {
            return replaceOrderDrawingFolder(order, drawingFolderFiles, drawingFolderName);
        }

        List<SalesOrderItem> items = listSalesOrderItems(salesOrderId);
        Map<String, List<SalesOrderItem>> itemMapByPartNo = new HashMap<>();
        Map<String, List<SalesOrderItem>> itemMapByPartNoVersion = new HashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities = new HashSet<>();
        for (SalesOrderItem item : items) {
            String normalizedPartNo = DrawingPartNoResolver.normalizePartNo(item.getPartNo());
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

        List<Map<String, Object>> replacedItems = new ArrayList<>();
        List<Map<String, Object>> protectedSkippedItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        Set<String> versionProtectedItemKeys = new HashSet<>();

        if (versionDrawings != null) {
            for (MultipartFile drawing : versionDrawings) {
                replaceSalesOrderDrawing(
                        order,
                        drawing,
                        true,
                        itemMapByPartNo,
                        itemMapByPartNoVersion,
                        itemIdentities,
                        versionProtectedItemKeys,
                        replacedItems,
                        protectedSkippedItems,
                        unmatchedDrawings,
                        skippedFiles);
            }
        }
        if (drawings != null) {
            for (MultipartFile drawing : drawings) {
                replaceSalesOrderDrawing(
                        order,
                        drawing,
                        false,
                        itemMapByPartNo,
                        itemMapByPartNoVersion,
                        itemIdentities,
                        versionProtectedItemKeys,
                        replacedItems,
                        protectedSkippedItems,
                        unmatchedDrawings,
                        skippedFiles);
            }
        }

        if (auditService != null && !replacedItems.isEmpty()) {
            auditService.record(
                    ACTION_DRAWING_REPLACE,
                    "sales_order_header",
                    salesOrderId,
                    null,
                    buildReplaceDrawingAuditSummary(
                            order,
                            drawings,
                            versionDrawings,
                            replacedItems,
                            protectedSkippedItems,
                            unmatchedDrawings,
                            skippedFiles),
                    "MARKET",
                    "SUCCESS",
                    AuditService.labelForAction(ACTION_DRAWING_REPLACE),
                    null
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "替换图纸处理完成，成功替换 " + replacedItems.size() + " 项。");
        result.put("replacedCount", replacedItems.size());
        result.put("boundCount", replacedItems.size());
        result.put("alreadyBoundCount", protectedSkippedItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("replacedItems", replacedItems);
        result.put("boundItems", replacedItems);
        result.put("alreadyBoundItems", protectedSkippedItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        log.info("Sales order drawing replace completed salesOrderId={} uploadCount={} replacedCount={} skippedExistingCount={} unmatchedCount={} skippedCount={}",
                order.getId(), fileCount(drawings) + fileCount(versionDrawings), replacedItems.size(), protectedSkippedItems.size(), unmatchedDrawings.size(), skippedFiles.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Sales order drawing replace completed with warnings salesOrderId={} unmatchedCount={} skippedCount={}",
                    order.getId(), unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

    private void replaceSalesOrderDrawing(
            SalesOrderHeader order,
            MultipartFile drawing,
            boolean versionMatch,
            Map<String, List<SalesOrderItem>> itemMapByPartNo,
            Map<String, List<SalesOrderItem>> itemMapByPartNoVersion,
            Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities,
            Set<String> versionProtectedItemKeys,
            List<Map<String, Object>> replacedItems,
            List<Map<String, Object>> protectedSkippedItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
        if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
            skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
            return;
        }

        String partNoFromFileName;
        List<SalesOrderItem> matchedItems;
        if (versionMatch) {
            DrawingPartNoResolver.DrawingIdentity identity =
                    DrawingPartNoResolver.extractPartNoVersion(cleanFileName, itemIdentities);
            partNoFromFileName = identity == null
                    ? DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet())
                    : identity.partNo();
            matchedItems = identity == null ? List.of() : itemMapByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
        } else {
            partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet());
            matchedItems = itemMapByPartNo.get(partNoFromFileName);
        }
        if (matchedItems == null || matchedItems.isEmpty()) {
            unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该销售订单对应零件"));
            return;
        }

        List<SalesOrderItem> replaceTargets = new ArrayList<>();
        for (SalesOrderItem item : matchedItems) {
            String key = itemKey(item.getPartNo(), item.getVersion());
            if (!versionMatch && versionProtectedItemKeys.contains(key)) {
                protectedSkippedItems.add(buildSalesItemResult(item, cleanFileName, "已有图号+版本图纸替换，未被图号图纸覆盖"));
                continue;
            }
            replaceTargets.add(item);
        }
        if (replaceTargets.isEmpty()) {
            return;
        }

        String orderNo = firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()));
        BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                order.getCustomerName(),
                orderNo,
                drawing,
                ACTION_DRAWING_REPLACE,
                String.valueOf(order.getId())
        );
        for (SalesOrderItem item : replaceTargets) {
            item.setDrawingUrl(storedFile.publicPath());
            item.setHasDrawing(true);
            if (salesOrderItemMapper.updateById(item) <= 0) {
                throw new RuntimeException("销售订单图纸替换保存失败，请刷新后重试");
            }
            if (versionMatch) {
                versionProtectedItemKeys.add(itemKey(item.getPartNo(), item.getVersion()));
            }
            replacedItems.add(buildSalesItemResult(item, cleanFileName, "已替换"));
        }
    }

    private Map<String, Object> supplementOrderDrawingFolder(
            SalesOrderHeader order,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        BusinessFileService.DrawingFolderSaveResult drawingFolder = businessFileService.saveDrawingFolderFiles(
                "SALES_ORDER",
                String.valueOf(order.getId()),
                drawingFolderName,
                drawingFolderFiles,
                ACTION_DRAWING_SUPPLEMENT + "_FOLDER",
                String.valueOf(order.getId()));
        order.setDrawingFolderUrl(drawingFolder.folderUrl());
        order.setDrawingFolderName(drawingFolder.folderName());
        order.setDrawingFolderFileCount(drawingFolder.fileCount());
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(order.getId());
        update.setDrawingFolderUrl(drawingFolder.folderUrl());
        update.setDrawingFolderName(drawingFolder.folderName());
        update.setDrawingFolderFileCount(drawingFolder.fileCount());
        update.setUpdatedAt(java.time.LocalDateTime.now());
        if (salesOrderHeaderMapper.updateById(update) <= 0) {
            throw new RuntimeException("销售订单图纸文件夹保存失败，请刷新后重试");
        }

        List<SalesOrderItem> items = listSalesOrderItems(order.getId());
        List<Map<String, Object>> boundItems = new ArrayList<>();
        for (SalesOrderItem item : items) {
            item.setDrawingUrl(drawingFolder.folderUrl());
            item.setHasDrawing(true);
            if (salesOrderItemMapper.updateById(item) <= 0) {
                throw new RuntimeException("销售订单图纸文件夹绑定失败，请刷新后重试");
            }
            boundItems.add(buildSalesItemResult(item, drawingFolder.folderName(), "已绑定文件夹"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "补传图纸文件夹处理完成，成功绑定 " + boundItems.size() + " 个零件。");
        result.put("boundCount", boundItems.size());
        result.put("alreadyBoundCount", 0);
        result.put("unmatchedCount", 0);
        result.put("skippedCount", 0);
        result.put("boundItems", boundItems);
        result.put("alreadyBoundItems", List.of());
        result.put("unmatchedDrawings", List.of());
        result.put("skippedFiles", List.of());
        result.put("drawingFolderMode", true);
        result.put("drawingFolderName", drawingFolder.folderName());
        result.put("drawingFolderFileCount", drawingFolder.fileCount());

        if (auditService != null && !boundItems.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("salesOrderId", order.getId());
            summary.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
            summary.put("uploadCount", drawingFolder.fileCount());
            summary.put("uploadedDrawingCount", 0);
            summary.put("versionUploadedDrawingCount", 0);
            summary.put("boundCount", boundItems.size());
            summary.put("alreadyBoundCount", 0);
            summary.put("unmatchedCount", 0);
            summary.put("skippedCount", 0);
            summary.put("drawingFolderMode", true);
            summary.put("drawingFolderFileCount", drawingFolder.fileCount());
            auditService.record(
                    ACTION_DRAWING_SUPPLEMENT,
                    "sales_order_header",
                    order.getId(),
                    null,
                    summary,
                    "MARKET",
                    "SUCCESS",
                    AuditService.labelForAction(ACTION_DRAWING_SUPPLEMENT),
                    null
            );
        }
        log.info("Sales order drawing folder supplement completed salesOrderId={} folderFileCount={} boundCount={}",
                order.getId(), drawingFolder.fileCount(), boundItems.size());
        return result;
    }

    private Map<String, Object> replaceOrderDrawingFolder(
            SalesOrderHeader order,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        BusinessFileService.DrawingFolderSaveResult drawingFolder = businessFileService.saveDrawingFolderFiles(
                "SALES_ORDER",
                String.valueOf(order.getId()),
                drawingFolderName,
                drawingFolderFiles,
                ACTION_DRAWING_REPLACE + "_FOLDER",
                String.valueOf(order.getId()));
        order.setDrawingFolderUrl(drawingFolder.folderUrl());
        order.setDrawingFolderName(drawingFolder.folderName());
        order.setDrawingFolderFileCount(drawingFolder.fileCount());
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(order.getId());
        update.setDrawingFolderUrl(drawingFolder.folderUrl());
        update.setDrawingFolderName(drawingFolder.folderName());
        update.setDrawingFolderFileCount(drawingFolder.fileCount());
        update.setUpdatedAt(LocalDateTime.now());
        if (salesOrderHeaderMapper.updateById(update) <= 0) {
            throw new RuntimeException("销售订单图纸文件夹替换保存失败，请刷新后重试");
        }

        List<SalesOrderItem> items = listSalesOrderItems(order.getId());
        List<Map<String, Object>> replacedItems = new ArrayList<>();
        for (SalesOrderItem item : items) {
            item.setDrawingUrl(drawingFolder.folderUrl());
            item.setHasDrawing(true);
            if (salesOrderItemMapper.updateById(item) <= 0) {
                throw new RuntimeException("销售订单图纸文件夹替换绑定失败，请刷新后重试");
            }
            replacedItems.add(buildSalesItemResult(item, drawingFolder.folderName(), "已绑定文件夹"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "替换图纸文件夹处理完成，成功替换 " + replacedItems.size() + " 个零件。");
        result.put("replacedCount", replacedItems.size());
        result.put("boundCount", replacedItems.size());
        result.put("alreadyBoundCount", 0);
        result.put("unmatchedCount", 0);
        result.put("skippedCount", 0);
        result.put("replacedItems", replacedItems);
        result.put("boundItems", replacedItems);
        result.put("alreadyBoundItems", List.of());
        result.put("unmatchedDrawings", List.of());
        result.put("skippedFiles", List.of());
        result.put("drawingFolderMode", true);
        result.put("drawingFolderName", drawingFolder.folderName());
        result.put("drawingFolderFileCount", drawingFolder.fileCount());

        if (auditService != null && !replacedItems.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("salesOrderId", order.getId());
            summary.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
            summary.put("uploadCount", drawingFolder.fileCount());
            summary.put("uploadedDrawingCount", 0);
            summary.put("versionUploadedDrawingCount", 0);
            summary.put("replacedCount", replacedItems.size());
            summary.put("boundCount", replacedItems.size());
            summary.put("alreadyBoundCount", 0);
            summary.put("unmatchedCount", 0);
            summary.put("skippedCount", 0);
            summary.put("drawingFolderMode", true);
            summary.put("drawingFolderFileCount", drawingFolder.fileCount());
            auditService.record(
                    ACTION_DRAWING_REPLACE,
                    "sales_order_header",
                    order.getId(),
                    null,
                    summary,
                    "MARKET",
                    "SUCCESS",
                    AuditService.labelForAction(ACTION_DRAWING_REPLACE),
                    null
            );
        }
        log.info("Sales order drawing folder replace completed salesOrderId={} folderFileCount={} replacedCount={}",
                order.getId(), drawingFolder.fileCount(), replacedItems.size());
        return result;
    }

    private PageResult<OrderDeliveryTicket> pageTicketsForSalesOrder(Long salesOrderId, Integer page, Integer pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePage - 1) * safePageSize;
        List<Integer> linkedSourceTicketIds = linkedSourceTicketIds(salesOrderId);
        QueryWrapper<OrderDeliveryTicket> countQuery = ticketQuery(salesOrderId, linkedSourceTicketIds);
        Long total = ticketMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<OrderDeliveryTicket> pageQuery = ticketQuery(salesOrderId, linkedSourceTicketIds)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(Long salesOrderId) {
        return ticketQuery(salesOrderId, linkedSourceTicketIds(salesOrderId));
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(Long salesOrderId, List<Integer> linkedSourceTicketIds) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        if (salesOrderId == null) {
            return query.apply("1 = 0");
        }
        if (linkedSourceTicketIds == null || linkedSourceTicketIds.isEmpty()) {
            return query.eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                    .eq("source_order_id", salesOrderId);
        }
        return query.and(wrapper -> wrapper
                .eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                .eq("source_order_id", salesOrderId)
                .or()
                .in("id", linkedSourceTicketIds));
    }

    private List<Integer> linkedSourceTicketIds(Long salesOrderId) {
        if (salesOrderId == null) {
            return List.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.select("source_ticket_id")
                .eq("sales_order_id", salesOrderId)
                .eq("source_type", ReleaseSourceType.RFQ.name())
                .isNotNull("source_ticket_id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(SalesOrderItem::getSourceTicketId)
                .filter(id -> id != null)
                .distinct()
                .toList();
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
            if (ACTION_DISCARD.equals(auditAction)) {
                validateSalesOrderDiscardAllowed(order, fromStatus, rejectionMessage);
            } else if (!requiredStatus.equals(fromStatus)) {
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

    private OrderStatusChangeResult changeItemU9ChangeExclusion(
            Long salesOrderId,
            Long itemId,
            OrderStatusChangeReq req,
            boolean exclude) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        if (itemId == null) {
            throw new RuntimeException("销售订单零件行 ID 不能为空");
        }
        String reason = requireU9ChangeReason(req);
        SalesOrderHeader order = salesOrderHeaderMapper.selectByIdForUpdate(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertSalesOrderDataScope(order);
        assertItemU9ChangeExclusionAllowed(order);
        SalesOrderItem item = salesOrderItemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("销售订单零件行不存在：" + itemId);
        }
        if (!salesOrderId.equals(item.getSalesOrderId())) {
            throw new RuntimeException("销售订单零件行 " + itemId + " 不属于当前销售订单");
        }
        if (exclude && Boolean.TRUE.equals(item.getU9ChangeExcluded())) {
            throw new RuntimeException("销售订单零件行已从 U9 同步和销售变更排除");
        }
        if (!exclude && !Boolean.TRUE.equals(item.getU9ChangeExcluded())) {
            throw new RuntimeException("销售订单零件行未从 U9 同步和销售变更排除，无需恢复");
        }

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        UpdateWrapper<SalesOrderItem> update = new UpdateWrapper<>();
        update.eq("id", item.getId())
                .eq("sales_order_id", order.getId())
                .set("u9_change_excluded", exclude)
                .set("updated_at", now);
        if (exclude) {
            update.set("u9_change_excluded_at", now)
                    .set("u9_change_excluded_by", operatorId)
                    .set("u9_change_excluded_reason", reason)
                    .set("u9_change_restored_at", null)
                    .set("u9_change_restored_by", null)
                    .set("u9_change_restore_reason", null);
            item.setU9ChangeExcludedAt(now);
            item.setU9ChangeExcludedBy(operatorId);
            item.setU9ChangeExcludedReason(reason);
            item.setU9ChangeRestoredAt(null);
            item.setU9ChangeRestoredBy(null);
            item.setU9ChangeRestoreReason(null);
        } else {
            update.set("u9_change_restored_at", now)
                    .set("u9_change_restored_by", operatorId)
                    .set("u9_change_restore_reason", reason);
            item.setU9ChangeRestoredAt(now);
            item.setU9ChangeRestoredBy(operatorId);
            item.setU9ChangeRestoreReason(reason);
        }
        int updated = salesOrderItemMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("销售订单零件 U9/销售变更排除状态保存失败，请刷新后重试");
        }
        item.setU9ChangeExcluded(exclude);
        item.setUpdatedAt(now);

        String action = exclude ? ACTION_ITEM_EXCLUDE : ACTION_ITEM_RESTORE;
        String message = buildItemU9ChangeMessage(order, item, reason, exclude);
        if (auditService != null) {
            auditService.record(
                    action,
                    "sales_order_item",
                    item.getId(),
                    null,
                    itemU9ChangeAuditSummary(order, item, reason, exclude),
                    "MARKET",
                    "SUCCESS",
                    message,
                    null
            );
        }
        log.info("Sales order item U9/change exclusion changed action={} salesOrderId={} salesOrderNo={} itemId={} lineNo={} excluded={}",
                action, order.getId(), order.getSalesOrderNo(), item.getId(), item.getLineNo(), exclude);

        OrderStatusChangeResult result = new OrderStatusChangeResult();
        result.setSalesOrderId(order.getId());
        result.setSalesOrderNo(order.getSalesOrderNo());
        result.setFromStatus(order.getStatus());
        result.setToStatus(order.getStatus());
        result.setUpdatedItemCount(1);
        result.setMessage(message);
        return result;
    }

    private void assertItemU9ChangeExclusionAllowed(SalesOrderHeader order) {
        String u9Status = order == null ? null : order.getU9SyncStatus();
        if (U9_STATUS_SYNCING.equals(u9Status) || U9_STATUS_PENDING_CONFIRMATION.equals(u9Status)) {
            throw new RuntimeException("U9 状态为" + u9Status + "，不能废弃或恢复销售订单零件");
        }
    }

    private String requireU9ChangeReason(OrderStatusChangeReq req) {
        String reason = req == null ? null : req.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("原因不能为空");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new RuntimeException("原因长度不能超过 500 个字符");
        }
        return trimmed;
    }

    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }

    private String buildItemU9ChangeMessage(SalesOrderHeader order, SalesOrderItem item, String reason, boolean exclude) {
        String actionName = exclude ? "已从 U9 同步和销售变更排除" : "已恢复到 U9 同步和销售变更";
        return actionName + "：" + firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo(), String.valueOf(order.getId()))
                + "，行号 " + (item.getLineNo() == null ? "-" : item.getLineNo())
                + "，图号 " + firstNonBlank(item.getPartNo(), "-")
                + "，版本 " + firstNonBlank(item.getVersion(), "-")
                + "；原因：" + reason;
    }

    private Map<String, Object> itemU9ChangeAuditSummary(
            SalesOrderHeader order,
            SalesOrderItem item,
            String reason,
            boolean exclude) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", order.getId());
        summary.put("salesOrderNo", firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        summary.put("salesOrderItemId", item.getId());
        summary.put("lineNo", item.getLineNo());
        summary.put("partNo", item.getPartNo());
        summary.put("version", item.getVersion());
        summary.put("u9ChangeExcluded", exclude);
        summary.put("reason", reason);
        return summary;
    }

    private void validateSalesOrderDiscardAllowed(SalesOrderHeader order, String fromStatus, String rejectionMessage) {
        if (order != null && U9_STATUS_SYNCED.equals(order.getU9SyncStatus())) {
            throw new RuntimeException("已同步 U9 的销售订单不能废弃。");
        }
        if (!QuoteStatusWorkflow.RELEASED.equals(fromStatus)) {
            throw new RuntimeException(rejectionMessage);
        }
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
        if (ACTION_DISCARD.equals(auditAction)) {
            return !QuoteStatusWorkflow.DISCARDED.equals(item.getStatus());
        }
        return safeInt(item.getReleasedQty()) == 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isReleasedOrderStatus(item.getStatus());
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
        row.put("orderUntaxedTotalPrice", itemStats.orderUntaxedTotalPrice);
        row.put("orderTaxIncludedTotalPrice", itemStats.orderTaxIncludedTotalPrice);
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
                "COALESCE(SUM(untaxed_total_price), 0) AS order_untaxed_total_price",
                "COALESCE(SUM(tax_included_total_price), 0) AS order_tax_included_total_price",
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
        Map<Long, TicketStats> statsMap = new HashMap<>(loadDirectTicketStats(orderIds));
        mergeLinkedSourceTicketStats(statsMap, orderIds);
        return statsMap;
    }

    private Map<Long, TicketStats> loadDirectTicketStats(List<Long> orderIds) {
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

    private void mergeLinkedSourceTicketStats(Map<Long, TicketStats> statsMap, List<Long> orderIds) {
        Map<Integer, Long> ticketOrderMap = linkedSourceTicketOrderMap(orderIds);
        if (ticketOrderMap.isEmpty()) {
            return;
        }
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.in("id", ticketOrderMap.keySet());
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(query);
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        for (OrderDeliveryTicket ticket : tickets) {
            if (ticket == null || ticket.getId() == null) {
                continue;
            }
            Long salesOrderId = ticketOrderMap.get(ticket.getId());
            if (salesOrderId == null) {
                continue;
            }
            TicketStats stats = statsMap.computeIfAbsent(salesOrderId, id -> {
                TicketStats created = new TicketStats();
                created.salesOrderId = id;
                return created;
            });
            stats.ticketCount++;
            stats.ticketQty += safeInt(ticket.getQty());
        }
    }

    private Map<Integer, Long> linkedSourceTicketOrderMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.select("sales_order_id", "source_ticket_id")
                .in("sales_order_id", orderIds)
                .eq("source_type", ReleaseSourceType.RFQ.name())
                .isNotNull("source_ticket_id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getSalesOrderId() == null || item.getSourceTicketId() == null) {
                continue;
            }
            result.putIfAbsent(item.getSourceTicketId(), item.getSalesOrderId());
        }
        return result;
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
        sheet.setEngineeringMaterialCode(ticket.getEngineeringMaterialCode());
        sheet.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        sheet.setEngineeringMaterialTypeCode(ticket.getEngineeringMaterialTypeCode());
        sheet.setEngineeringMaterialTypeName(ticket.getEngineeringMaterialTypeName());
        sheet.setEngineeringMaterialCategory(ticket.getEngineeringMaterialCategory());
        sheet.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        sheet.setPartNetWeight(ticket.getPartNetWeight() == null ? 0.0 : ticket.getPartNetWeight());
        sheet.setSubstituteMaterial("");
        sheet.setSurfaceTreatment(ticket.getSurfaceTreatment());
        sheet.setHeatTreatment("");
        sheet.setDeliveryDate(ticket.getDeliveryDate());
        sheet.setRemark(ticket.getRemark());
        sheet.setProcessRoute(processRouteSnapshotService.copySnapshot(ticket.getProcessRoute()));
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
            String customerCode,
            String customerName,
            String keyword,
            String status) {
        if (customerId != null) {
            query.eq("customer_id", customerId);
        }
        if (!isBlank(customerCode)) {
            query.eq("customer_code", customerCode.trim());
        } else if (!isBlank(customerName)) {
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

    private String itemKey(String partNo, String version) {
        return DrawingPartNoResolver.normalizePartNo(partNo) + "||" + DrawingPartNoResolver.normalizeVersion(version);
    }

    private boolean isEmptyFiles(List<MultipartFile> files) {
        return files == null || files.isEmpty();
    }

    private int fileCount(List<MultipartFile> files) {
        return files == null ? 0 : files.size();
    }

    private Map<String, Object> buildSupplementDrawingAuditSummary(
            SalesOrderHeader order,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<Map<String, Object>> boundItems,
            List<Map<String, Object>> alreadyBoundItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        int uploadedDrawingCount = fileCount(drawings);
        int versionUploadedDrawingCount = fileCount(versionDrawings);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", order == null ? null : order.getId());
        summary.put("salesOrderNo", order == null ? "" : firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        summary.put("uploadCount", uploadedDrawingCount + versionUploadedDrawingCount);
        summary.put("uploadedDrawingCount", uploadedDrawingCount);
        summary.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        summary.put("boundCount", boundItems.size());
        summary.put("alreadyBoundCount", alreadyBoundItems.size());
        summary.put("unmatchedCount", unmatchedDrawings.size());
        summary.put("skippedCount", skippedFiles.size());
        summary.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        return summary;
    }

    private Map<String, Object> buildReplaceDrawingAuditSummary(
            SalesOrderHeader order,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<Map<String, Object>> replacedItems,
            List<Map<String, Object>> protectedSkippedItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        int uploadedDrawingCount = fileCount(drawings);
        int versionUploadedDrawingCount = fileCount(versionDrawings);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", order == null ? null : order.getId());
        summary.put("salesOrderNo", order == null ? "" : firstNonBlank(order.getSalesOrderNo(), order.getDocumentNo()));
        summary.put("uploadCount", uploadedDrawingCount + versionUploadedDrawingCount);
        summary.put("uploadedDrawingCount", uploadedDrawingCount);
        summary.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        summary.put("replacedCount", replacedItems.size());
        summary.put("alreadyBoundCount", protectedSkippedItems.size());
        summary.put("unmatchedCount", unmatchedDrawings.size());
        summary.put("skippedCount", skippedFiles.size());
        summary.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        return summary;
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

    private Map<String, Object> buildMissingSalesItem(SalesOrderItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemId", item.getId());
        result.put("partNo", item.getPartNo());
        result.put("version", item.getVersion());
        result.put("description", item.getDescription());
        result.put("qty", item.getQty());
        result.put("lineNo", item.getLineNo());
        return result;
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

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
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
        private BigDecimal orderUntaxedTotalPrice = BigDecimal.ZERO;
        private BigDecimal orderTaxIncludedTotalPrice = BigDecimal.ZERO;
        private int drawingMissingCount;

        private static ItemStats from(Map<String, Object> row) {
            ItemStats stats = new ItemStats();
            stats.salesOrderId = numberValue(row.get("sales_order_id")).longValue();
            stats.itemCount = numberValue(row.get("item_count")).intValue();
            stats.totalQuantity = numberValue(row.get("total_quantity")).intValue();
            stats.releasedQuantity = numberValue(row.get("released_quantity")).intValue();
            stats.orderUntaxedTotalPrice = decimalValue(row.get("order_untaxed_total_price"));
            stats.orderTaxIncludedTotalPrice = decimalValue(row.get("order_tax_included_total_price"));
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
