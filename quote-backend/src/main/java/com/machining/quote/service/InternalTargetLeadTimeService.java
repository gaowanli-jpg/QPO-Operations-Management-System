package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeShiftReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseItemBatchQueryReq;
import com.machining.quote.dto.ReleaseItemVersionUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class InternalTargetLeadTimeService {
    private static final Logger log = LoggerFactory.getLogger(InternalTargetLeadTimeService.class);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private OrderFilePathService orderFilePathService;
    @Autowired
    private OrderItemPageService orderItemPageService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    @Transactional
    public Map<String, Object> shiftLeadTimes(InternalTargetLeadTimeShiftReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new RuntimeException("请至少选择一个零件");
        }
        int days = req.getDays() == null ? 0 : req.getDays();
        if (days == 0) {
            throw new RuntimeException("调整天数不能为 0");
        }

        List<Map<String, Object>> skipped = new ArrayList<>();
        int adjustedCount = 0;
        for (Integer itemId : req.getItemIds()) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "零件不存在或不属于当前 RFQ"));
                continue;
            }

            String baseLeadTime = leadTimeService.effectiveDeliveryLeadTime(item);
            try {
                Object before = buildItemRow(item);
                item.setInternalTargetLeadTime(leadTimeService.shiftLeadTime(baseLeadTime, days));
                itemMapper.updateById(item);
                auditService.record("INTERNAL_TARGET_LEAD_TIME_SHIFT", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
                adjustedCount++;
            } catch (RuntimeException e) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        log.info("Internal target lead time shifted rfqId={} itemCount={} adjustedCount={} skippedCount={} days={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), days);
        if (!skipped.isEmpty()) {
            log.warn("Internal target lead time shifted with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> setLeadTimes(InternalTargetLeadTimeSetReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new RuntimeException("请至少选择一个零件");
        }
        String normalizedDate = leadTimeService.normalizeRequiredDate(req.getInternalTargetLeadTime());

        List<Map<String, Object>> skipped = new ArrayList<>();
        int adjustedCount = 0;
        for (Integer itemId : req.getItemIds()) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "零件不存在或不属于当前 RFQ"));
                continue;
            }

            Object before = buildItemRow(item);
            item.setInternalTargetLeadTime(normalizedDate);
            itemMapper.updateById(item);
            auditService.record("INTERNAL_TARGET_LEAD_TIME_SET", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
            adjustedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        log.info("Internal target lead time set rfqId={} itemCount={} adjustedCount={} skippedCount={} targetLeadTime={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), normalizedDate);
        if (!skipped.isEmpty()) {
            log.warn("Internal target lead time set with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateLeadTime(InternalTargetLeadTimeUpdateReq req) {
        requireOrderForUpdate(req.getRfqId());
        if (req.getItemId() == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(req.getItemId());
        if (item == null || !req.getRfqId().equals(item.getRfqId())) {
            throw new RuntimeException("零件不存在或不属于当前 RFQ");
        }

        Object before = buildItemRow(item);
        String normalizedLeadTime = leadTimeService.normalizeForQuote(req.getInternalTargetLeadTime());
        UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
        update.eq("id", item.getId()).set("internal_target_lead_time", normalizedLeadTime);
        itemMapper.update(null, update);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved);
        auditService.record("INTERNAL_TARGET_LEAD_TIME_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        return result;
    }

    @Transactional
    public Map<String, Object> updateReleaseItemVersion(Integer itemId, ReleaseItemVersionUpdateReq req) {
        Integer rfqId = req == null ? null : req.getRfqId();
        requireOrderForUpdate(rfqId);
        if (itemId == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null || !Objects.equals(rfqId, item.getRfqId())) {
            throw new RuntimeException("零件不存在或不属于当前 RFQ");
        }
        assertReleaseItemMutable(item, listTicketsForItems(rfqId, List.of(item)));

        Map<String, Object> before = buildItemRow(item);
        item.setVersion(normalizeVersion(req.getVersion()));
        itemMapper.updateById(item);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved == null ? item : saved);
        auditService.record("RELEASE_ITEM_VERSION_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        log.info("Release item version updated rfqId={} itemId={} partNo={}",
                rfqId, item.getId(), normalizePartNo(item.getPartNo()));
        return result;
    }

    @Transactional
    public Map<String, Object> replaceReleaseDrawings(List<Integer> rfqIds, List<MultipartFile> drawings) {
        List<OrderRfq> orders = requireOrdersForUpdate(rfqIds);
        if (drawings == null || drawings.isEmpty()) {
            throw new RuntimeException("请至少上传一个 PDF 图纸");
        }

        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));
        List<ItemQuoteVersion> items = listItemsBatch(orderById.keySet().stream().toList());
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        Map<String, List<ItemQuoteVersion>> itemsByPartNo = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            String partNo = normalizePartNo(item.getPartNo());
            if (!partNo.isEmpty()) {
                itemsByPartNo.computeIfAbsent(partNo, ignored -> new ArrayList<>()).add(item);
            }
        }

        List<Map<String, Object>> replacedItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        List<Map<String, Object>> skippedItems = new ArrayList<>();
        int matchedCount = 0;

        try {
            for (MultipartFile drawing : drawings) {
                String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
                if (drawing == null || cleanFileName.isEmpty() || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                    skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                    continue;
                }

                String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemsByPartNo.keySet());
                List<ItemQuoteVersion> matchedItems = itemsByPartNo.get(partNoFromFileName);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到所选订单对应零件"));
                    continue;
                }

                matchedCount += matchedItems.size();
                Map<Integer, String> newUrlByRfqId = new LinkedHashMap<>();
                for (ItemQuoteVersion item : matchedItems) {
                    int itemTicketedQty = ticketedQty(item, tickets);
                    if (!isReleaseItemMutable(item, itemTicketedQty)) {
                        skippedItems.add(buildDrawingItemResult(item, orderById.get(item.getRfqId()), cleanFileName,
                                releaseMutationBlockedReason(item, Math.max(0, safeInt(item.getQty()) - itemTicketedQty))));
                        continue;
                    }
                    String fileUrl = newUrlByRfqId.computeIfAbsent(item.getRfqId(), ignored -> {
                        try {
                            return saveReleaseDrawing(drawing, cleanFileName, orderById.get(item.getRfqId()));
                        } catch (IOException e) {
                            throw new ReleaseDrawingSaveException("图纸保存失败：" + cleanFileName, e);
                        }
                    });
                    item.setDrawingUrl(fileUrl);
                    itemMapper.updateById(item);
                    replacedItems.add(buildDrawingItemResult(item, orderById.get(item.getRfqId()), cleanFileName, "已替换"));
                }
            }
        } catch (ReleaseDrawingSaveException e) {
            log.error("Release drawings replace failed rfqIds={} uploadCount={}", orderById.keySet(), drawings.size(), e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Release drawings replace failed rfqIds={} uploadCount={}", orderById.keySet(), drawings.size(), e);
            throw e;
        }

        Map<String, Object> auditSummary = new LinkedHashMap<>();
        auditSummary.put("rfqIds", orderById.keySet());
        auditSummary.put("rfqNos", orders.stream().map(OrderRfq::getRfqNo).toList());
        auditSummary.put("uploadCount", drawings.size());
        auditSummary.put("matchedCount", matchedCount);
        auditSummary.put("replacedCount", replacedItems.size());
        auditSummary.put("unmatchedCount", unmatchedDrawings.size());
        auditSummary.put("skippedCount", skippedFiles.size() + skippedItems.size());
        auditSummary.put("replacedItems", replacedItems);
        auditSummary.put("unmatchedDrawings", unmatchedDrawings);
        auditSummary.put("skippedFiles", skippedFiles);
        auditSummary.put("skippedItems", skippedItems);
        String auditTargetId = orderById.keySet().stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        auditService.record(
                "RELEASE_DRAWING_REPLACE",
                "item_quote_version",
                auditTargetId,
                null,
                auditSummary,
                "MARKET",
                "SUCCESS",
                "RELEASE_DRAWING_REPLACE rfqIds=" + orderById.keySet() + " replacedCount=" + replacedItems.size(),
                null
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "图纸替换完成，成功替换 " + replacedItems.size() + " 项。");
        result.put("uploadCount", drawings.size());
        result.put("matchedCount", matchedCount);
        result.put("replacedCount", replacedItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size() + skippedItems.size());
        result.put("replacedItems", replacedItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        result.put("skippedItems", skippedItems);
        log.info("Release drawings replace completed rfqIds={} uploadCount={} matchedCount={} replacedCount={} unmatchedCount={} skippedCount={}",
                orderById.keySet(), drawings.size(), matchedCount, replacedItems.size(), unmatchedDrawings.size(),
                skippedFiles.size() + skippedItems.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty() || !skippedItems.isEmpty()) {
            log.warn("Release drawings replace completed with warnings rfqIds={} unmatchedCount={} skippedCount={}",
                    orderById.keySet(), unmatchedDrawings.size(), skippedFiles.size() + skippedItems.size());
        }
        return result;
    }

    public List<Map<String, Object>> listItemRows(Integer rfqId) {
        requireOrder(rfqId);
        return buildItemRows(rfqId);
    }

    public Map<String, Object> releaseSummary(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("summary", orderItemPageService.summarizeItems(rfqId));
        return result;
    }

    public PageResult<Map<String, Object>> pageItemRows(
            Integer rfqId,
            Integer page,
            Integer pageSize,
            String keyword,
            String status,
            Boolean remainingOnly) {
        requireOrder(rfqId);
        OrderItemPageReq req = new OrderItemPageReq();
        req.setRfqId(rfqId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setKeyword(keyword);
        req.setStatus(status);
        OrderItemPageDTO itemPage = orderItemPageService.pageItems(req);
        List<ItemQuoteVersion> items = itemPage.getRecords();
        List<OrderDeliveryTicket> tickets = listTicketsForItems(rfqId, items);
        List<Map<String, Object>> rows = items.stream()
                .map(item -> buildItemRow(item, ticketedQty(item, tickets)))
                .filter(row -> !Boolean.TRUE.equals(remainingOnly) || safeInt(row.get("remainingQty")) > 0)
                .toList();
        return PageResult.of(rows, itemPage.getTotal(), itemPage.getPage(), itemPage.getPageSize());
    }

    public PageResult<Map<String, Object>> pageItemRowsBatch(ReleaseItemBatchQueryReq req) {
        List<OrderRfq> orders = requireOrders(req == null ? null : req.getRfqIds());
        if (orders.isEmpty()) {
            return PageResult.of(
                    List.of(),
                    0,
                    safePage(req == null ? null : req.getPage()),
                    safePageSize(req == null ? null : req.getPageSize()));
        }

        ReleaseItemBatchQueryReq queryReq = new ReleaseItemBatchQueryReq();
        queryReq.setRfqIds(orders.stream().map(OrderRfq::getId).toList());
        queryReq.setPage(req == null ? null : req.getPage());
        queryReq.setPageSize(req == null ? null : req.getPageSize());
        queryReq.setKeyword(req == null ? null : req.getKeyword());
        queryReq.setStatus(req == null ? null : req.getStatus());
        queryReq.setRemainingOnly(req == null ? null : req.getRemainingOnly());

        OrderItemPageDTO itemPage = orderItemPageService.pageItemsBatch(queryReq);
        List<ItemQuoteVersion> items = itemPage.getRecords();
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));
        List<Map<String, Object>> rows = items.stream()
                .map(item -> buildBatchItemRow(item, ticketedQty(item, tickets), orderById))
                .filter(row -> !Boolean.TRUE.equals(queryReq.getRemainingOnly()) || safeInt(row.get("remainingQty")) > 0)
                .toList();
        return PageResult.of(rows, itemPage.getTotal(), itemPage.getPage(), itemPage.getPageSize());
    }

    public Map<String, Object> matchPartSelection(Integer rfqId, List<Map<String, String>> importedItems) {
        requireOrder(rfqId);
        Set<String> importedKeys = new HashSet<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if (!"||".equals(key)) {
                importedKeys.add(key);
            }
        }
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        List<Map<String, Object>> selectedRows = new ArrayList<>();
        List<Map<String, Object>> skippedRows = new ArrayList<>();
        int matchedCount = 0;
        for (ItemQuoteVersion item : listItems(rfqId)) {
            if (!importedKeys.contains(itemKey(item.getPartNo(), item.getVersion()))) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildItemRow(item, ticketedQty(item, tickets));
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", skippedRows.size());
        result.put("data", selectedRows);
        result.put("skippedItems", skippedRows);
        return result;
    }

    public Map<String, Object> matchPartSelectionBatch(List<Integer> rfqIds, List<Map<String, String>> importedItems) {
        List<OrderRfq> orders = requireOrders(rfqIds);
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orders.forEach(order -> orderById.put(order.getId(), order));

        Set<String> importedKeys = new HashSet<>();
        Map<String, Set<String>> importedKeysByRfqNo = new LinkedHashMap<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if ("||".equals(key)) {
                continue;
            }
            String rfqNo = normalizeRfqNo(item.getOrDefault("normalizedRfqNo", item.get("rfqNo")));
            if (rfqNo.isEmpty()) {
                importedKeys.add(key);
            } else {
                importedKeysByRfqNo.computeIfAbsent(rfqNo, ignored -> new HashSet<>()).add(key);
            }
        }

        List<ItemQuoteVersion> items = listItemsBatch(orderById.keySet().stream().toList());
        List<OrderDeliveryTicket> tickets = listTicketsForItems(items);
        List<Map<String, Object>> selectedRows = new ArrayList<>();
        List<Map<String, Object>> skippedRows = new ArrayList<>();
        int matchedCount = 0;
        for (ItemQuoteVersion item : items) {
            OrderRfq order = orderById.get(item.getRfqId());
            String key = itemKey(item.getPartNo(), item.getVersion());
            String rfqNo = order == null ? "" : normalizeRfqNo(order.getRfqNo());
            boolean matchesAnySelectedOrder = importedKeys.contains(key);
            boolean matchesSpecifiedOrder = importedKeysByRfqNo.getOrDefault(rfqNo, Set.of()).contains(key);
            if (!matchesAnySelectedOrder && !matchesSpecifiedOrder) {
                continue;
            }
            matchedCount++;
            Map<String, Object> row = buildBatchItemRow(item, ticketedQty(item, tickets), orderById);
            if (Boolean.TRUE.equals(row.get("canSelect"))) {
                selectedRows.add(row);
            } else {
                skippedRows.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size() + importedKeysByRfqNo.values().stream().mapToInt(Set::size).sum());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", skippedRows.size());
        result.put("data", selectedRows);
        result.put("skippedItems", skippedRows);
        return result;
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ 订单不存在");
        }
        assertOrderDataScope(order);
        assertCanReadReleaseWorkspace(order);
        return order;
    }

    private List<OrderRfq> requireOrders(List<Integer> rfqIds) {
        List<Integer> normalizedIds = new ArrayList<>(new java.util.LinkedHashSet<>(
                rfqIds == null ? List.of() : rfqIds.stream()
                        .filter(id -> id != null)
                        .toList()));
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        Map<Integer, OrderRfq> orderById = new LinkedHashMap<>();
        orderRfqMapper.selectBatchIds(normalizedIds).forEach(order -> orderById.put(order.getId(), order));
        List<OrderRfq> orders = new ArrayList<>();
        for (Integer rfqId : normalizedIds) {
            OrderRfq order = orderById.get(rfqId);
            if (order == null || !canAccessOrder(order)) {
                continue;
            }
            assertCanReadReleaseWorkspace(order);
            orders.add(order);
        }
        return orders;
    }

    private List<OrderRfq> requireOrdersForUpdate(List<Integer> rfqIds) {
        List<Integer> normalizedIds = new ArrayList<>(new java.util.LinkedHashSet<>(
                rfqIds == null ? List.of() : rfqIds.stream()
                        .filter(id -> id != null)
                        .toList()));
        if (normalizedIds.isEmpty()) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        List<OrderRfq> orders = new ArrayList<>();
        for (Integer rfqId : normalizedIds) {
            orders.add(requireOrderForUpdate(rfqId));
        }
        return orders;
    }

    private OrderRfq requireOrderForUpdate(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        OrderRfq order = orderRfqMapper.selectByIdForUpdate(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ 订单不存在");
        }
        assertOrderDataScope(order);
        assertCanGenerateRelease(order);
        return order;
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private boolean canAccessOrder(OrderRfq order) {
        if (dataScopeService == null) {
            return true;
        }
        try {
            dataScopeService.assertCanAccessOrder(order);
            return true;
        } catch (RuntimeException e) {
            if (isDataScopeDenied(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDataScopeDenied(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("No data permission for order ");
    }

    private void assertCanReadReleaseWorkspace(OrderRfq order) {
        if (order == null || QuoteStatusWorkflow.canReadReleaseWorkspace(order.getStatus())) {
            return;
        }
        if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
            throw new RuntimeException("请先在报价处理模块保存报价单后再进入议价提单");
        }
        throw new RuntimeException("只有已报价、部分已提单或已生成提单订单可以进入议价提单");
    }

    private void assertCanGenerateRelease(OrderRfq order) {
        if (order == null || QuoteStatusWorkflow.canGenerateDeliveryTicket(order.getStatus())) {
            return;
        }
        if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
            throw new RuntimeException("请先在报价处理模块保存报价单后再进入议价提单");
        }
        throw new RuntimeException("只有已报价订单可以进入议价提单");
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<ItemQuoteVersion> listItemsBatch(List<Integer> rfqIds) {
        if (rfqIds == null || rfqIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.in("rfq_id", rfqIds)
                .orderByAsc("rfq_id")
                .orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<Map<String, Object>> buildItemRows(Integer rfqId) {
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        return items.stream()
                .map(item -> buildItemRow(item, ticketedQty(item, tickets)))
                .toList();
    }

    private List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        return ticketMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTicketsForItems(Integer rfqId, List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Integer> itemIds = items.stream()
                .map(ItemQuoteVersion::getId)
                .filter(id -> id != null)
                .toList();
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        if (!itemIds.isEmpty()) {
            qw.and(wrapper -> wrapper.in("item_id", itemIds).or().isNull("item_id"));
        }
        return ticketMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTicketsForItems(List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Integer> rfqIds = items.stream()
                .map(ItemQuoteVersion::getRfqId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Integer> itemIds = items.stream()
                .map(ItemQuoteVersion::getId)
                .filter(id -> id != null)
                .toList();
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.in("rfq_id", rfqIds);
        if (!itemIds.isEmpty()) {
            qw.and(wrapper -> wrapper.in("item_id", itemIds).or().isNull("item_id"));
        }
        return ticketMapper.selectList(qw);
    }

    private int ticketedQty(ItemQuoteVersion item, List<OrderDeliveryTicket> tickets) {
        return tickets.stream()
                .filter(ticket -> matchesTicketItem(item, ticket))
                .mapToInt(ticket -> safeInt(ticket.getQty()))
                .sum();
    }

    private boolean matchesTicketItem(ItemQuoteVersion item, OrderDeliveryTicket ticket) {
        if (!Objects.equals(item.getRfqId(), ticket.getRfqId())) {
            return false;
        }
        if (item.getId() != null && item.getId().equals(ticket.getItemId())) {
            return true;
        }
        return ticket.getItemId() == null
                && normalizePartNo(item.getPartNo()).equals(normalizePartNo(ticket.getPartNo()))
                && normalizeVersion(item.getVersion()).equals(normalizeVersion(ticket.getVersion()));
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item) {
        return buildItemRow(item, 0);
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item, int ticketedQty) {
        Map<String, Object> row = new LinkedHashMap<>();
        String effectiveDeliveryLeadTime = leadTimeService.effectiveDeliveryLeadTime(item);
        int sourceQty = safeInt(item.getQty());
        int remainingQty = Math.max(0, sourceQty - ticketedQty);
        row.put("id", item.getId());
        row.put("rfqId", item.getRfqId());
        row.put("partNo", item.getPartNo());
        row.put("version", item.getVersion());
        String drawingUrl = item.getDrawingUrl();
        row.put("drawingUrl", drawingUrl);
        row.put("hasDrawing", drawingUrl != null && !drawingUrl.trim().isEmpty());
        row.put("description", item.getDescription());
        row.put("qty", item.getQty());
        row.put("ticketedQty", ticketedQty);
        row.put("remainingQty", remainingQty);
        row.put("unit", item.getUnit());
        row.put("materialName", item.getMaterialName());
        row.put("engineeringMaterialName", MaterialDataResolver.engineeringMaterialName(item));
        row.put("surfaceTreatment", item.getSurfaceTreatment());
        row.put("leadTime", item.getLeadTime());
        row.put("quoteLeadTime", item.getQuoteLeadTime());
        row.put("internalTargetLeadTime", item.getInternalTargetLeadTime());
        row.put("effectiveDeliveryLeadTime", effectiveDeliveryLeadTime);
        row.put("deliveryParsedDate", leadTimeService.parsedDateText(effectiveDeliveryLeadTime));
        row.put("canAutoShiftInternalLeadTime", leadTimeService.canAutoShift(effectiveDeliveryLeadTime));
        row.put("finalUnitPrice", item.getFinalUnitPrice());
        row.put("finalTotalPrice", item.getFinalTotalPrice());
        row.put("status", item.getStatus());
        row.put("remark", item.getRemark());
        row.put("quoteMode", item.getQuoteMode());
        row.put("assemblyFee", item.getAssemblyFee());
        boolean canSelect = item.getFinalUnitPrice() != null
                && remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
        row.put("canSelect", canSelect);
        row.put("releaseBlockedReason", canSelect ? "" : releaseBlockedReason(item, remainingQty));
        return row;
    }

    private Map<String, Object> buildBatchItemRow(ItemQuoteVersion item, int ticketedQty, Map<Integer, OrderRfq> orderById) {
        Map<String, Object> row = buildItemRow(item, ticketedQty);
        OrderRfq order = orderById.get(item.getRfqId());
        row.put("rfqNo", order == null ? "" : order.getRfqNo());
        row.put("customerName", order == null ? "" : order.getCustomerName());
        row.put("orderStatus", order == null ? "" : order.getStatus());
        return row;
    }

    private String releaseBlockedReason(ItemQuoteVersion item, int remainingQty) {
        if (item.getFinalUnitPrice() == null) {
            return "零件未报价";
        }
        if (remainingQty <= 0) {
            return "零件已全部提单";
        }
        if (QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus())) {
            return "零件已提单";
        }
        return "";
    }

    private String normalizePartNo(String partNo) {
        return partNo == null ? "" : partNo.trim().toUpperCase();
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().toUpperCase();
    }

    private String normalizeRfqNo(String rfqNo) {
        return rfqNo == null ? "" : rfqNo.trim().toUpperCase();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
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

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private void assertReleaseItemMutable(ItemQuoteVersion item, List<OrderDeliveryTicket> tickets) {
        int itemTicketedQty = ticketedQty(item, tickets == null ? List.of() : tickets);
        if (!isReleaseItemMutable(item, itemTicketedQty)) {
            int remainingQty = Math.max(0, safeInt(item.getQty()) - itemTicketedQty);
            throw new RuntimeException(releaseMutationBlockedReason(item, remainingQty));
        }
    }

    private boolean isReleaseItemMutable(ItemQuoteVersion item, int ticketedQty) {
        if (item == null) {
            return false;
        }
        int remainingQty = Math.max(0, safeInt(item.getQty()) - ticketedQty);
        return remainingQty > 0
                && !QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                && !QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus());
    }

    private String releaseMutationBlockedReason(ItemQuoteVersion item, int remainingQty) {
        if (remainingQty <= 0) {
            return "零件已全部提单";
        }
        if (item != null && (QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.RELEASED.equals(item.getStatus())
                || QuoteStatusWorkflow.isLegacyItemReleasedStatus(item.getStatus()))) {
            return "零件已提单";
        }
        return "零件不可修改";
    }

    private String saveReleaseDrawing(MultipartFile drawing, String cleanFileName, OrderRfq order) throws IOException {
        if (order == null) {
            throw new IOException("订单不存在");
        }
        Path projectDir = orderFilePathService.orderDrawingDirectory(order);
        Files.createDirectories(projectDir);
        String newFilename = UUID.randomUUID() + ".pdf";
        Path dest = projectDir.resolve(newFilename).normalize();
        if (!dest.startsWith(projectDir)) {
            throw new IOException("图纸保存路径非法：" + cleanFileName);
        }
        try (InputStream inputStream = drawing.getInputStream()) {
            Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return orderFilePathService.drawingUrl(order, newFilename);
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return originalFilename.replaceAll(".*[\\\\/]", "").trim();
    }

    private Map<String, Object> buildDrawingItemResult(
            ItemQuoteVersion item,
            OrderRfq order,
            String fileName,
            String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rfqId", item == null ? null : item.getRfqId());
        result.put("rfqNo", order == null ? "" : order.getRfqNo());
        result.put("itemId", item == null ? null : item.getId());
        result.put("partNo", item == null ? "" : item.getPartNo());
        result.put("version", item == null ? "" : item.getVersion());
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

    private Map<String, Object> skipRow(Integer itemId, String partNo, String version, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemId", itemId);
        row.put("partNo", partNo);
        row.put("version", version);
        row.put("reason", reason);
        return row;
    }

    private static class ReleaseDrawingSaveException extends RuntimeException {
        private ReleaseDrawingSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
