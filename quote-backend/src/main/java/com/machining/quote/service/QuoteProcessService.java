package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.OrderItemPageDTO;
import com.machining.quote.dto.OrderItemPageReq;
import com.machining.quote.dto.OrderItemSummaryDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.QuoteLeadTimeBatchUpdateReq;
import com.machining.quote.dto.QuoteLeadTimeReplacementConfirmDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementPreviewDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementRowDTO;
import com.machining.quote.dto.QuoteLeadTimeReplacementSummaryDTO;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeShiftReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class QuoteProcessService {
    private static final Logger log = LoggerFactory.getLogger(QuoteProcessService.class);

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
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private SystemTaskGuardService taskGuardService;
    @Autowired
    private OrderItemPageService orderItemPageService;
    @Autowired
    private QuoteLeadTimeReplacementExcelParser leadTimeReplacementExcelParser;

    public Map<String, Object> getQuoteProcess(Integer rfqId) {
        OrderRfq order = requireQuoteProcessReadableOrder(rfqId);
        List<ItemQuoteVersion> items = listItems(rfqId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", items.stream().map(this::buildItemRow).toList());
        return result;
    }

    public Map<String, Object> getQuoteProcessSummary(Integer rfqId) {
        return getQuoteProcessSummary(rfqId, null);
    }

    public Map<String, Object> getQuoteProcessSummary(Integer rfqId, String scope) {
        OrderRfq order = requireQuoteProcessReadableOrder(rfqId, scope);
        OrderItemSummaryDTO summary = orderItemPageService.summarizeItems(rfqId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("summary", summary);
        result.put("itemCount", summary.getItemCount());
        result.put("missingLeadTimeCount", countMissingQuoteLeadTimes(rfqId, scope));
        result.put("quotedCount", summary.getQuotedCount());
        return result;
    }

    public PageResult<Map<String, Object>> listQuoteProcessItems(OrderItemPageReq req) {
        if (req == null || req.getRfqId() == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        requireQuoteProcessReadableOrder(req.getRfqId(), req.getScope());
        OrderItemPageDTO page = orderItemPageService.pageItems(req);
        List<Map<String, Object>> rows = page.getRecords().stream().map(this::buildItemRow).toList();
        return PageResult.of(rows, page.getTotal(), page.getPage(), page.getPageSize());
    }

    public Map<String, Object> matchPartSelection(Integer rfqId, List<Map<String, String>> importedItems) {
        requireQuoteProcessEditableOrder(rfqId);
        Set<String> importedKeys = new HashSet<>();
        for (Map<String, String> item : importedItems == null ? List.<Map<String, String>>of() : importedItems) {
            String key = itemKey(
                    item.getOrDefault("normalizedPartNo", item.get("partNo")),
                    item.getOrDefault("normalizedVersion", item.get("version")));
            if (!"||".equals(key)) {
                importedKeys.add(key);
            }
        }

        List<Map<String, Object>> selectedRows = new ArrayList<>();
        int matchedCount = 0;
        for (ItemQuoteVersion item : listItems(rfqId)) {
            if (!importedKeys.contains(itemKey(item.getPartNo(), item.getVersion()))) {
                continue;
            }
            matchedCount++;
            selectedRows.add(buildItemRow(item));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("importedCount", importedKeys.size());
        result.put("matchedCount", matchedCount);
        result.put("selectedCount", selectedRows.size());
        result.put("skippedCount", 0);
        result.put("data", selectedRows);
        result.put("skippedItems", List.of());
        return result;
    }

    @Transactional
    public Map<String, Object> shiftLeadTimes(QuoteLeadTimeShiftReq req) {
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId(), req.getScope());
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
            if (!isItemEditableInScope(item, req.getScope())) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), "零件不属于补充报价待确认范围"));
                continue;
            }

            String effectiveLeadTime = leadTimeService.effectiveLeadTime(item);
            try {
                Object before = buildItemRow(item);
                item.setQuoteLeadTime(leadTimeService.shiftLeadTime(effectiveLeadTime, days));
                itemMapper.updateById(item);
                auditService.record("QUOTE_LEAD_TIME_SHIFT", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
                adjustedCount++;
            } catch (RuntimeException e) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), e.getMessage()));
            }
        }

        Map<String, Object> result = quoteLeadTimeMutationSummary(req.getRfqId(), adjustedCount, skipped, req.getScope());
        log.info("Quote lead time shifted rfqId={} itemCount={} adjustedCount={} skippedCount={} days={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), days);
        if (!skipped.isEmpty()) {
            log.warn("Quote lead time shifted with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> setLeadTimes(QuoteLeadTimeSetReq req) {
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId(), req.getScope());
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new RuntimeException("请至少选择一个零件");
        }
        String normalizedDate = leadTimeService.normalizeRequiredDate(req.getQuoteLeadTime());

        List<Map<String, Object>> skipped = new ArrayList<>();
        int adjustedCount = 0;
        for (Integer itemId : req.getItemIds()) {
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "零件不存在或不属于当前 RFQ"));
                continue;
            }
            if (!isItemEditableInScope(item, req.getScope())) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), "零件不属于补充报价待确认范围"));
                continue;
            }

            Object before = buildItemRow(item);
            item.setQuoteLeadTime(normalizedDate);
            itemMapper.updateById(item);
            auditService.record("QUOTE_LEAD_TIME_SET", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
            adjustedCount++;
        }

        Map<String, Object> result = quoteLeadTimeMutationSummary(req.getRfqId(), adjustedCount, skipped, req.getScope());
        log.info("Quote lead time set rfqId={} itemCount={} adjustedCount={} skippedCount={} targetLeadTime={}",
                req.getRfqId(), req.getItemIds().size(), adjustedCount, skipped.size(), normalizedDate);
        if (!skipped.isEmpty()) {
            log.warn("Quote lead time set with warnings rfqId={} skippedCount={}",
                    req.getRfqId(), skipped.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateLeadTime(QuoteLeadTimeUpdateReq req) {
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId(), req.getScope());
        if (req.getItemId() == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }

        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(req.getItemId());
        if (item == null || !req.getRfqId().equals(item.getRfqId())) {
            throw new RuntimeException("零件不存在或不属于当前 RFQ");
        }
        if (!isItemEditableInScope(item, req.getScope())) {
            throw new RuntimeException("零件不属于补充报价待确认范围");
        }

        Object before = buildItemRow(item);
        String normalizedLeadTime = leadTimeService.normalizeForQuote(req.getQuoteLeadTime());
        UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
        update.eq("id", item.getId()).set("quote_lead_time", normalizedLeadTime);
        itemMapper.update(null, update);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved);
        auditService.record("QUOTE_LEAD_TIME_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        return result;
    }

    @Transactional
    public Map<String, Object> batchUpdateLeadTimes(QuoteLeadTimeBatchUpdateReq req) {
        if (req == null || req.getRfqId() == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId(), req.getScope());
        List<QuoteLeadTimeBatchUpdateReq.ItemUpdate> updates = safeList(req.getItems());
        if (updates.isEmpty()) {
            throw new RuntimeException("没有需要保存的报价交期修改");
        }

        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> affectedRows = new ArrayList<>();
        int adjustedCount = 0;
        for (QuoteLeadTimeBatchUpdateReq.ItemUpdate updateReq : updates) {
            Integer itemId = updateReq == null ? null : updateReq.getItemId();
            if (itemId == null) {
                skipped.add(skipRow(null, "", "", "零件 ID 不能为空"));
                continue;
            }
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
            if (item == null || !req.getRfqId().equals(item.getRfqId())) {
                skipped.add(skipRow(itemId, "", "", "零件不存在或不属于当前 RFQ"));
                continue;
            }
            if (!isItemEditableInScope(item, req.getScope())) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), "零件不属于补充报价待确认范围"));
                continue;
            }

            String normalizedLeadTime = leadTimeService.normalizeForQuote(updateReq.getQuoteLeadTime());
            if (Objects.equals(normalizeBlank(item.getQuoteLeadTime()), normalizeBlank(normalizedLeadTime))) {
                skipped.add(skipRow(item.getId(), item.getPartNo(), item.getVersion(), "报价交期未变化"));
                continue;
            }

            UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
            update.eq("id", item.getId()).set("quote_lead_time", normalizedLeadTime);
            itemMapper.update(null, update);
            ItemQuoteVersion saved = itemMapper.selectById(item.getId());
            affectedRows.add(buildItemRow(saved));
            adjustedCount++;
        }

        if (adjustedCount > 0) {
            Map<String, Object> auditSummary = Map.of(
                    "rfqId", req.getRfqId(),
                    "requestedCount", updates.size(),
                    "updatedItemCount", adjustedCount,
                    "skippedCount", skipped.size());
            auditService.record("QUOTE_LEAD_TIME_BATCH_UPDATE", "order_rfq", req.getRfqId(), null, auditSummary,
                    "QUOTE_PROCESS", "SUCCESS", "批量保存报价交期：更新 " + adjustedCount + " 个零件", null);
        }

        Map<String, Object> result = quoteLeadTimeMutationSummary(req.getRfqId(), adjustedCount, skipped, req.getScope());
        result.put("updatedCount", adjustedCount);
        result.put("affectedRows", affectedRows);
        log.info("Quote lead time batch saved rfqId={} requestedCount={} updatedItemCount={} skippedCount={}",
                req.getRfqId(), updates.size(), adjustedCount, skipped.size());
        return result;
    }

    public QuoteLeadTimeReplacementPreviewDTO previewLeadTimeReplacement(Integer rfqId, InputStream inputStream) {
        return previewLeadTimeReplacement(rfqId, inputStream, null);
    }

    public QuoteLeadTimeReplacementPreviewDTO previewLeadTimeReplacement(Integer rfqId, InputStream inputStream, String scope) {
        OrderRfq order = requireQuoteProcessEditableOrder(rfqId, scope);
        QuoteLeadTimeReplacementExcelParser.ParseResult parsed = leadTimeReplacementExcelParser.parse(inputStream);
        List<ItemQuoteVersion> items = listItems(rfqId, scope);
        QuoteLeadTimeReplacementPreviewDTO preview = buildLeadTimeReplacementPreview(order, parsed, items);
        preview.setPreviewFingerprint(leadTimeReplacementFingerprint(rfqId, parsed, preview.getRows(), items, order.getStatus()));
        return preview;
    }

    @Transactional
    public QuoteLeadTimeReplacementConfirmDTO confirmLeadTimeReplacement(
            Integer rfqId,
            InputStream inputStream,
            String previewFingerprint) {
        return confirmLeadTimeReplacement(rfqId, inputStream, previewFingerprint, null);
    }

    @Transactional
    public QuoteLeadTimeReplacementConfirmDTO confirmLeadTimeReplacement(
            Integer rfqId,
            InputStream inputStream,
            String previewFingerprint,
            String scope) {
        if (isBlank(previewFingerprint)) {
            throw new IllegalArgumentException("预览指纹不能为空");
        }
        OrderRfq order = requireQuoteProcessEditableOrderForUpdate(rfqId, scope);
        QuoteLeadTimeReplacementExcelParser.ParseResult parsed = leadTimeReplacementExcelParser.parse(inputStream);
        List<ItemQuoteVersion> items = listItems(rfqId, scope);
        QuoteLeadTimeReplacementPreviewDTO preview = buildLeadTimeReplacementPreview(order, parsed, items);
        String currentFingerprint = leadTimeReplacementFingerprint(rfqId, parsed, preview.getRows(), items, order.getStatus());
        if (!previewFingerprint.trim().equals(currentFingerprint)) {
            throw new PreviewFingerprintConflictException("数据已发生变化，请重新预览");
        }
        if (preview.getSummary().getErrorCount() > 0) {
            throw new IllegalArgumentException("表格数据替换存在错误行，请修正后重新预览");
        }

        List<Map<String, Object>> affectedRows = new ArrayList<>();
        int updatedCount = 0;
        for (QuoteLeadTimeReplacementRowDTO row : preview.getRows()) {
            if (!"CHANGED".equals(row.getStatus())) {
                continue;
            }
            ItemQuoteVersion item = itemMapper.selectByIdForUpdate(row.getItemId());
            if (item == null || !rfqId.equals(item.getRfqId())) {
                throw new PreviewFingerprintConflictException("数据已发生变化，请重新预览");
            }
            UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
            update.eq("id", item.getId()).set("quote_lead_time", row.getImportedQuoteLeadTime());
            itemMapper.update(null, update);
            affectedRows.add(buildItemRow(itemMapper.selectById(item.getId())));
            updatedCount++;
        }

        QuoteLeadTimeReplacementSummaryDTO summary = preview.getSummary();
        summary.setUpdatedItemCount(updatedCount);
        QuoteLeadTimeReplacementConfirmDTO confirm = new QuoteLeadTimeReplacementConfirmDTO();
        confirm.setSummary(summary);
        confirm.setAffectedRows(affectedRows);
        confirm.setMessage("表格数据替换完成，已更新 " + updatedCount + " 个零件报价交期。");

        if (updatedCount > 0) {
            Map<String, Object> auditSummary = Map.of(
                    "rfqId", rfqId,
                    "importedCount", summary.getImportedCount(),
                    "matchedCount", summary.getMatchedCount(),
                    "updatedItemCount", updatedCount,
                    "unmatchedCount", summary.getUnmatchedCount(),
                    "emptyValueCount", summary.getEmptyValueCount(),
                    "errorCount", summary.getErrorCount());
            auditService.record("QUOTE_LEAD_TIME_DATA_REPLACEMENT", "order_rfq", rfqId, null, auditSummary,
                    "QUOTE_PROCESS", "SUCCESS", "报价处理表格数据替换：更新 " + updatedCount + " 个零件", null);
        }
        log.info("Quote lead time data replacement completed rfqId={} importedCount={} matchedCount={} updatedItemCount={} unmatchedCount={} emptyValueCount={}",
                rfqId, summary.getImportedCount(), summary.getMatchedCount(), updatedCount,
                summary.getUnmatchedCount(), summary.getEmptyValueCount());
        return confirm;
    }

    @Transactional
    public Map<String, Object> saveQuoteSheet(Integer rfqId) {
        long startedAt = System.currentTimeMillis();
        log.info("Quote sheet save started rfqId={}", rfqId);
        try {
            OrderRfq order = requireQuoteProcessSavableOrderForUpdate(rfqId);
            List<ItemQuoteVersion> items = listItems(rfqId);
            if (items.isEmpty()) {
                throw new RuntimeException("订单没有零件，不能保存报价单");
            }

            List<String> unpricedItems = items.stream()
                    .filter(this::hasDrawing)
                    .filter(item -> item.getTaxIncludedUnitPrice() == null)
                    .map(this::itemLabel)
                    .toList();
            if (!unpricedItems.isEmpty()) {
                throw new RuntimeException("订单存在未核价零件，不能保存报价单：" + String.join("、", unpricedItems));
            }

            List<String> missingLeadTimes = items.stream()
                    .filter(item -> isBlank(leadTimeService.effectiveLeadTime(item)))
                    .map(this::itemLabel)
                    .toList();
            if (!missingLeadTimes.isEmpty()) {
                throw new RuntimeException("以下零件缺少报价交期，不能保存报价单：" + String.join("、", missingLeadTimes));
            }

            Map<String, Object> before = quoteSheetSummary(order, items, missingLeadTimes.size(), 0);
            order.setStatus(QuoteStatusWorkflow.nextAfterMarketSaveQuote(order.getStatus()));
            orderRfqMapper.updateById(order);
            int itemStatusUpdatedCount = markPricedItemsAsQuoted(items);
            OrderRfq savedOrder = orderRfqMapper.selectById(order.getId());
            Map<String, Object> after = quoteSheetSummary(savedOrder, items, 0, itemStatusUpdatedCount);
            auditService.record(
                    "QUOTE_PROCESS_SAVE_QUOTE",
                    "order_rfq",
                    order.getId(),
                    before,
                    after,
                    "QUOTE_PROCESS",
                    "SUCCESS",
                    "保存报价单：RFQ " + safe(savedOrder.getRfqNo()) + "，零件 " + items.size()
                            + " 个，状态更新 " + itemStatusUpdatedCount + " 个",
                    null
            );
            log.info("Quote sheet save completed rfqId={} fromStatus={} toStatus={} itemCount={} itemStatusUpdatedCount={} durationMs={}",
                    rfqId, before.get("status"), savedOrder.getStatus(), items.size(), itemStatusUpdatedCount,
                    System.currentTimeMillis() - startedAt);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("orderInfo", savedOrder);
            result.put("summary", after);
            result.put("itemCount", items.size());
            result.put("itemStatusUpdatedCount", itemStatusUpdatedCount);
            return result;
        } catch (RuntimeException e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            if (isBusinessRejection(e)) {
                log.warn("Quote sheet save rejected rfqId={} reason={} durationMs={}",
                        rfqId, safeLogMessage(e), durationMs);
            } else {
                log.error("Quote sheet save failed rfqId={} durationMs={}", rfqId, durationMs, e);
            }
            throw e;
        }
    }

    @Transactional
    public Map<String, Object> saveSupplementalQuoteSheet(Integer rfqId) {
        long startedAt = System.currentTimeMillis();
        log.info("Supplemental quote sheet save started rfqId={}", rfqId);
        try {
            OrderRfq order = requireSupplementalQuoteOrderForUpdate(rfqId);
            String orderStatusBefore = order.getStatus();
            List<ItemQuoteVersion> items = listItems(rfqId, OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING_QUOTE);
            if (items.isEmpty()) {
                throw new RuntimeException("没有补充报价待确认零件，不能保存补充报价单");
            }

            List<String> missingLeadTimes = items.stream()
                    .filter(item -> isBlank(leadTimeService.effectiveLeadTime(item)))
                    .map(this::itemLabel)
                    .toList();
            if (!missingLeadTimes.isEmpty()) {
                throw new RuntimeException("以下补图零件缺少报价交期，不能保存补充报价单：" + String.join("、", missingLeadTimes));
            }

            Map<String, Object> before = supplementalQuoteSheetSummary(order, items, missingLeadTimes.size(), 0);
            int itemStatusUpdatedCount = markSupplementalItemsAsQuoted(items);
            OrderRfq savedOrder = refreshReleasedOrderStatus(order.getId());
            Map<String, Object> after = supplementalQuoteSheetSummary(savedOrder, items, 0, itemStatusUpdatedCount);
            auditService.record(
                    "QUOTE_PROCESS_SAVE_QUOTE",
                    "order_rfq",
                    order.getId(),
                    before,
                    after,
                    "QUOTE_PROCESS",
                    "SUCCESS",
                    "保存补充报价单：RFQ " + safe(savedOrder.getRfqNo()) + "，补图零件 " + items.size()
                            + " 个，状态更新 " + itemStatusUpdatedCount + " 个",
                    null
            );
            log.info("Supplemental quote sheet save completed rfqId={} orderStatusBefore={} orderStatusAfter={} supplementalItemCount={} itemStatusUpdatedCount={} durationMs={}",
                    rfqId, orderStatusBefore, savedOrder.getStatus(), items.size(), itemStatusUpdatedCount,
                    System.currentTimeMillis() - startedAt);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("supplemental", true);
            result.put("orderInfo", savedOrder);
            result.put("summary", after);
            result.put("supplementalItemCount", items.size());
            result.put("itemStatusUpdatedCount", itemStatusUpdatedCount);
            result.put("orderStatusBefore", orderStatusBefore);
            result.put("orderStatusAfter", savedOrder.getStatus());
            return result;
        } catch (RuntimeException e) {
            long durationMs = System.currentTimeMillis() - startedAt;
            if (isBusinessRejection(e)) {
                log.warn("Supplemental quote sheet save rejected rfqId={} reason={} durationMs={}",
                        rfqId, safeLogMessage(e), durationMs);
            } else {
                log.error("Supplemental quote sheet save failed rfqId={} durationMs={}", rfqId, durationMs, e);
            }
            throw e;
        }
    }

    public void assertQuoteExportable(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        if (!QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())) {
            if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
                throw new RuntimeException("请先在报价处理模块保存报价单后再导出");
            }
            throw new RuntimeException("只有已报价或已提单订单可以导出报价单");
        }
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
        return order;
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
        return order;
    }

    private OrderRfq requireQuoteProcessReadableOrder(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        if (!QuoteStatusWorkflow.canReadQuoteProcess(order.getStatus())) {
            throw new RuntimeException("只有已核价、已报价或已提单订单可以处理报价单");
        }
        return order;
    }

    private OrderRfq requireQuoteProcessReadableOrder(Integer rfqId, String scope) {
        if (isSupplementalQuoteScope(scope)) {
            return requireSupplementalQuoteOrder(rfqId);
        }
        return requireQuoteProcessReadableOrder(rfqId);
    }

    private OrderRfq requireQuoteProcessEditableOrder(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        if (!QuoteStatusWorkflow.canEditQuoteLeadTime(order.getStatus())
                && QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())
                && order.getStatus() != null) {
            throw new RuntimeException("当前订单状态" + order.getStatus() + "，交期已锁定，不能修改报价交期");
        }
        if (!QuoteStatusWorkflow.canEditQuoteLeadTime(order.getStatus())) {
            if (QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())) {
                throw new RuntimeException("当前订单交期已锁定，不能修改报价交期");
            }
            throw new RuntimeException("只有已核价订单可以维护报价交期");
        }
        return order;
    }

    private OrderRfq requireQuoteProcessEditableOrder(Integer rfqId, String scope) {
        if (isSupplementalQuoteScope(scope)) {
            return requireSupplementalQuoteOrder(rfqId);
        }
        return requireQuoteProcessEditableOrder(rfqId);
    }

    private OrderRfq requireQuoteProcessEditableOrderForUpdate(Integer rfqId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (!QuoteStatusWorkflow.canEditQuoteLeadTime(order.getStatus())
                && QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())
                && order.getStatus() != null) {
            throw new RuntimeException("当前订单状态" + order.getStatus() + "，交期已锁定，不能修改报价交期");
        }
        if (!QuoteStatusWorkflow.canEditQuoteLeadTime(order.getStatus())) {
            if (QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())) {
                throw new RuntimeException("当前订单交期已锁定，不能修改报价交期");
            }
            throw new RuntimeException("只有已核价订单可以维护报价交期");
        }
        return order;
    }

    private OrderRfq requireQuoteProcessEditableOrderForUpdate(Integer rfqId, String scope) {
        if (isSupplementalQuoteScope(scope)) {
            return requireSupplementalQuoteOrderForUpdate(rfqId);
        }
        return requireQuoteProcessEditableOrderForUpdate(rfqId);
    }

    private OrderRfq requireQuoteProcessSavableOrderForUpdate(Integer rfqId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        assertNoActiveRefreshQuotesTask(rfqId);
        if (!QuoteStatusWorkflow.canSaveQuoteSheet(order.getStatus())) {
            if (QuoteStatusWorkflow.QUOTED.equals(order.getStatus())) {
                throw new RuntimeException("该订单已保存报价单，不能重复保存");
            }
            if (QuoteStatusWorkflow.UNPRICED.equals(order.getStatus())
                    || QuoteStatusWorkflow.isLegacyUnpricedStatus(order.getStatus())) {
                throw new RuntimeException("订单尚未完成核价，不能保存报价单");
            }
            if (QuoteStatusWorkflow.PARTIALLY_RELEASED.equals(order.getStatus())
                    || QuoteStatusWorkflow.RELEASED.equals(order.getStatus())) {
                throw new RuntimeException("订单已进入提单流程，不能保存报价单");
            }
            throw new RuntimeException("只有已核价订单可以保存报价单");
        }
        return order;
    }

    private OrderRfq requireSupplementalQuoteOrder(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        assertSupplementalQuoteOrderStatus(order);
        return order;
    }

    private OrderRfq requireSupplementalQuoteOrderForUpdate(Integer rfqId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        assertNoActiveRefreshQuotesTask(rfqId);
        assertSupplementalQuoteOrderStatus(order);
        return order;
    }

    private void assertSupplementalQuoteOrderStatus(OrderRfq order) {
        if (order == null || !QuoteStatusWorkflow.isReleasedOrderStatus(order.getStatus())) {
            throw new RuntimeException("只有部分已提单或已生成提单订单可以处理补充报价");
        }
    }

    private void assertNoActiveRefreshQuotesTask(Integer rfqId) {
        if (taskGuardService != null) {
            taskGuardService.assertNoActiveRefreshQuotesTask(rfqId);
        }
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }

    private QuoteLeadTimeReplacementPreviewDTO buildLeadTimeReplacementPreview(
            OrderRfq order,
            QuoteLeadTimeReplacementExcelParser.ParseResult parsed,
            List<ItemQuoteVersion> items) {
        Map<String, List<ItemQuoteVersion>> candidatesByKey = new LinkedHashMap<>();
        for (ItemQuoteVersion item : safeList(items)) {
            candidatesByKey.computeIfAbsent(itemKey(item.getPartNo(), item.getVersion()), ignored -> new ArrayList<>())
                    .add(item);
        }

        List<QuoteLeadTimeReplacementRowDTO> rows = new ArrayList<>();
        for (QuoteLeadTimeReplacementRowDTO excelRow : safeList(parsed.rows())) {
            QuoteLeadTimeReplacementRowDTO row = copyReplacementRow(excelRow);
            if (row.hasErrors()) {
                row.setStatus("ERROR");
                row.setReason(String.join("；", row.getErrors()));
                rows.add(row);
                continue;
            }

            List<ItemQuoteVersion> candidates = candidatesByKey.getOrDefault(
                    itemKey(row.getNormalizedPartNo(), row.getNormalizedVersion()),
                    List.of());
            if (candidates.isEmpty()) {
                row.setStatus("UNMATCHED");
                row.setReason("当前 RFQ 未找到对应图号和版本的零件");
                rows.add(row);
                continue;
            }
            if (candidates.size() > 1) {
                row.setStatus("ERROR");
                row.setReason("当前 RFQ 存在多个相同图号和版本的零件");
                rows.add(row);
                continue;
            }

            ItemQuoteVersion item = candidates.get(0);
            row.setRfqId(item.getRfqId());
            row.setRfqNo(order == null ? null : order.getRfqNo());
            row.setItemId(item.getId());
            row.setCurrentQuoteLeadTime(item.getQuoteLeadTime());
            if (isBlank(row.getImportedQuoteLeadTime())) {
                row.setStatus("EMPTY_VALUE");
                row.setReason("报价交期为空，未替换");
                rows.add(row);
                continue;
            }
            if (Objects.equals(normalizeBlank(item.getQuoteLeadTime()), normalizeBlank(row.getImportedQuoteLeadTime()))) {
                row.setStatus("UNCHANGED");
                row.setReason("报价交期未变化");
            } else {
                row.setStatus("CHANGED");
                row.setReason("");
            }
            rows.add(row);
        }

        QuoteLeadTimeReplacementSummaryDTO summary = summarizeLeadTimeReplacement(rows);
        QuoteLeadTimeReplacementPreviewDTO preview = new QuoteLeadTimeReplacementPreviewDTO();
        preview.setRecognizedFields(parsed.recognizedFields());
        preview.setRows(rows);
        preview.setSummary(summary);
        preview.setCanConfirm(summary.getErrorCount() == 0 && summary.getChangedCount() > 0);
        return preview;
    }

    private QuoteLeadTimeReplacementRowDTO copyReplacementRow(QuoteLeadTimeReplacementRowDTO source) {
        QuoteLeadTimeReplacementRowDTO row = new QuoteLeadTimeReplacementRowDTO();
        row.setExcelRowNumber(source.getExcelRowNumber());
        row.setPartNo(source.getPartNo());
        row.setVersion(source.getVersion());
        row.setNormalizedPartNo(source.getNormalizedPartNo());
        row.setNormalizedVersion(source.getNormalizedVersion());
        row.setImportedQuoteLeadTime(source.getImportedQuoteLeadTime());
        row.setErrors(new ArrayList<>(safeList(source.getErrors())));
        return row;
    }

    private QuoteLeadTimeReplacementSummaryDTO summarizeLeadTimeReplacement(List<QuoteLeadTimeReplacementRowDTO> rows) {
        QuoteLeadTimeReplacementSummaryDTO summary = new QuoteLeadTimeReplacementSummaryDTO();
        summary.setImportedCount(rows == null ? 0 : rows.size());
        for (QuoteLeadTimeReplacementRowDTO row : safeList(rows)) {
            if (row.getItemId() != null) {
                summary.setMatchedCount(summary.getMatchedCount() + 1);
            }
            switch (safe(row.getStatus())) {
                case "CHANGED" -> summary.setChangedCount(summary.getChangedCount() + 1);
                case "UNCHANGED" -> summary.setUnchangedCount(summary.getUnchangedCount() + 1);
                case "EMPTY_VALUE" -> summary.setEmptyValueCount(summary.getEmptyValueCount() + 1);
                case "UNMATCHED" -> summary.setUnmatchedCount(summary.getUnmatchedCount() + 1);
                case "ERROR" -> summary.setErrorCount(summary.getErrorCount() + 1);
                default -> {
                }
            }
        }
        return summary;
    }

    private String leadTimeReplacementFingerprint(
            Integer rfqId,
            QuoteLeadTimeReplacementExcelParser.ParseResult parsed,
            List<QuoteLeadTimeReplacementRowDTO> previewRows,
            List<ItemQuoteVersion> items,
            String orderStatus) {
        StringBuilder canonical = new StringBuilder();
        appendToken(canonical, "rfqId", rfqId);
        appendToken(canonical, "orderStatus", orderStatus);
        safeList(parsed.recognizedFields()).stream()
                .sorted()
                .forEach(field -> appendToken(canonical, "field", field));
        safeList(previewRows).stream()
                .sorted(Comparator
                        .comparing((QuoteLeadTimeReplacementRowDTO row) -> safe(row.getNormalizedPartNo()))
                        .thenComparing(row -> safe(row.getNormalizedVersion()))
                        .thenComparing(row -> row.getExcelRowNumber() == null ? Integer.MAX_VALUE : row.getExcelRowNumber()))
                .forEach(row -> {
                    appendToken(canonical, "row", row.getExcelRowNumber());
                    appendToken(canonical, "partNo", row.getNormalizedPartNo());
                    appendToken(canonical, "version", row.getNormalizedVersion());
                    appendToken(canonical, "imported", row.getImportedQuoteLeadTime());
                    appendToken(canonical, "status", row.getStatus());
                    appendToken(canonical, "itemId", row.getItemId());
                    appendToken(canonical, "current", row.getCurrentQuoteLeadTime());
                    safeList(row.getErrors()).stream().sorted().forEach(error -> appendToken(canonical, "error", error));
                });
        safeList(items).stream()
                .sorted(Comparator.comparing(ItemQuoteVersion::getId))
                .forEach(item -> {
                    appendToken(canonical, "candidateId", item.getId());
                    appendToken(canonical, "candidateKey", itemKey(item.getPartNo(), item.getVersion()));
                    appendToken(canonical, "candidateLeadTime", item.getQuoteLeadTime());
                    appendToken(canonical, "candidateStatus", item.getStatus());
                });
        return sha256(canonical.toString());
    }

    private void appendToken(StringBuilder canonical, String key, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        canonical.append(key.length()).append(':').append(key)
                .append(text.length()).append(':').append(text).append('\n');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private Map<String, Object> quoteLeadTimeMutationSummary(Integer rfqId, int adjustedCount,
                                                             List<Map<String, Object>> skipped) {
        return quoteLeadTimeMutationSummary(rfqId, adjustedCount, skipped, null);
    }

    private Map<String, Object> quoteLeadTimeMutationSummary(Integer rfqId, int adjustedCount,
                                                             List<Map<String, Object>> skipped,
                                                             String scope) {
        OrderItemSummaryDTO summary = orderItemPageService.summarizeItems(rfqId);
        long missingLeadTimeCount = countMissingQuoteLeadTimes(rfqId, scope);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        if (isSupplementalQuoteScope(scope)) {
            result.put("supplemental", true);
        }
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped == null ? 0 : skipped.size());
        result.put("skippedItems", skipped == null ? List.of() : skipped);
        result.put("summary", summary);
        result.put("itemCount", summary.getItemCount());
        result.put("missingLeadTimeCount", missingLeadTimeCount);
        result.put("quotedCount", summary.getQuotedCount());
        return result;
    }

    private long countMissingQuoteLeadTimes(Integer rfqId) {
        return countMissingQuoteLeadTimes(rfqId, null);
    }

    private long countMissingQuoteLeadTimes(Integer rfqId, String scope) {
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId)
                .apply("(quote_lead_time IS NULL OR BTRIM(quote_lead_time) = '')")
                .apply("(lead_time IS NULL OR BTRIM(lead_time) = '')");
        if (isSupplementalQuoteScope(scope)) {
            applySupplementalQuoteItemFilter(query);
        }
        Long count = itemMapper.selectCount(query);
        return count == null ? 0L : count;
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId, String scope) {
        if (!isSupplementalQuoteScope(scope)) {
            return listItems(rfqId);
        }
        QueryWrapper<ItemQuoteVersion> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId);
        applySupplementalQuoteItemFilter(query);
        query.orderByAsc("id");
        return itemMapper.selectList(query);
    }

    private void applySupplementalQuoteItemFilter(QueryWrapper<ItemQuoteVersion> query) {
        query.apply("NULLIF(BTRIM(drawing_url), '') IS NOT NULL")
                .isNotNull("tax_included_unit_price")
                .eq("status", QuoteStatusWorkflow.PRICED);
    }

    private boolean isItemEditableInScope(ItemQuoteVersion item, String scope) {
        return !isSupplementalQuoteScope(scope) || isSupplementalQuoteItem(item);
    }

    private boolean isSupplementalQuoteItem(ItemQuoteVersion item) {
        return hasDrawing(item)
                && item.getTaxIncludedUnitPrice() != null
                && QuoteStatusWorkflow.PRICED.equals(item.getStatus());
    }

    private boolean isSupplementalQuoteScope(String scope) {
        return scope != null && OrderItemPageReq.SCOPE_SUPPLEMENTAL_DRAWING_QUOTE.equalsIgnoreCase(scope.trim());
    }

    private int markPricedItemsAsQuoted(List<ItemQuoteVersion> items) {
        int updated = 0;
        for (ItemQuoteVersion item : items) {
            if (!hasDrawing(item) || item.getTaxIncludedUnitPrice() == null) {
                continue;
            }
            if (QuoteStatusWorkflow.QUOTED.equals(item.getStatus())) {
                continue;
            }
            if (QuoteStatusWorkflow.PRICED.equals(item.getStatus())
                    || QuoteStatusWorkflow.UNPRICED.equals(item.getStatus())
                    || QuoteStatusWorkflow.isLegacyUnpricedStatus(item.getStatus())
                    || isBlank(item.getStatus())) {
                item.setStatus(QuoteStatusWorkflow.QUOTED);
                itemMapper.updateById(item);
                updated++;
            }
        }
        return updated;
    }

    private int markSupplementalItemsAsQuoted(List<ItemQuoteVersion> items) {
        int updated = 0;
        for (ItemQuoteVersion item : safeList(items)) {
            if (!isSupplementalQuoteItem(item)) {
                continue;
            }
            item.setStatus(QuoteStatusWorkflow.QUOTED);
            itemMapper.updateById(item);
            updated++;
        }
        return updated;
    }

    private OrderRfq refreshReleasedOrderStatus(Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectByIdForUpdate(rfqId);
        if (order == null || !QuoteStatusWorkflow.isReleasedOrderStatus(order.getStatus())) {
            return order;
        }
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(new QueryWrapper<OrderDeliveryTicket>()
                .eq("rfq_id", rfqId));
        if (tickets.isEmpty()) {
            return order;
        }

        Map<Integer, Integer> qtyByItem = new HashMap<>();
        for (OrderDeliveryTicket ticket : tickets) {
            if (ticket.getItemId() != null) {
                qtyByItem.merge(ticket.getItemId(), safeInt(ticket.getQty()), Integer::sum);
            }
        }
        List<ItemQuoteVersion> releasableItems = items.stream()
                .filter(item -> QuoteStatusWorkflow.isRfqReleaseEligibleItem(
                        item.getStatus(), hasDrawing(item), item.getTaxIncludedUnitPrice() != null))
                .toList();
        if (releasableItems.isEmpty()) {
            return order;
        }
        boolean allReleasableItemsTicketed = releasableItems.stream()
                .allMatch(item -> qtyByItem.getOrDefault(item.getId(), 0) >= safeInt(item.getQty()));
        String refreshedStatus = allReleasableItemsTicketed
                ? QuoteStatusWorkflow.RELEASED
                : QuoteStatusWorkflow.PARTIALLY_RELEASED;
        if (!Objects.equals(order.getStatus(), refreshedStatus)) {
            order.setStatus(refreshedStatus);
            orderRfqMapper.updateById(order);
        }
        return orderRfqMapper.selectById(order.getId());
    }

    private Map<String, Object> quoteSheetSummary(OrderRfq order, List<ItemQuoteVersion> items, int missingLeadTimeCount,
                                                  int itemStatusUpdatedCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", order == null ? null : order.getStatus());
        summary.put("rfqNo", order == null ? null : order.getRfqNo());
        summary.put("itemCount", items == null ? 0 : items.size());
        summary.put("missingLeadTimeCount", missingLeadTimeCount);
        summary.put("itemStatusUpdatedCount", itemStatusUpdatedCount);
        return summary;
    }

    private Map<String, Object> supplementalQuoteSheetSummary(
            OrderRfq order,
            List<ItemQuoteVersion> items,
            int missingLeadTimeCount,
            int itemStatusUpdatedCount) {
        Map<String, Object> summary = quoteSheetSummary(order, items, missingLeadTimeCount, itemStatusUpdatedCount);
        summary.put("supplemental", true);
        summary.put("supplementalItemCount", items == null ? 0 : items.size());
        return summary;
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item) {
        Map<String, Object> row = new LinkedHashMap<>();
        String effectiveLeadTime = leadTimeService.effectiveLeadTime(item);
        row.put("id", item.getId());
        row.put("rfqId", item.getRfqId());
        row.put("partNo", item.getPartNo());
        row.put("version", item.getVersion());
        row.put("description", item.getDescription());
        row.put("qty", item.getQty());
        row.put("unit", item.getUnit());
        row.put("materialName", item.getMaterialName());
        row.put("engineeringMaterialName", MaterialDataResolver.engineeringMaterialName(item));
        row.put("surfaceTreatment", item.getSurfaceTreatment());
        row.put("leadTime", item.getLeadTime());
        row.put("quoteLeadTime", item.getQuoteLeadTime());
        row.put("effectiveLeadTime", effectiveLeadTime);
        row.put("parsedDate", leadTimeService.parsedDateText(effectiveLeadTime));
        row.put("canAutoShift", leadTimeService.canAutoShift(effectiveLeadTime));
        row.put("taxIncludedUnitPrice", item.getTaxIncludedUnitPrice());
        row.put("taxIncludedTotalPrice", item.getTaxIncludedTotalPrice());
        row.put("status", item.getStatus());
        row.put("remark", item.getRemark());
        row.put("quoteMode", item.getQuoteMode());
        row.put("assemblyFee", item.getAssemblyFee());
        return row;
    }

    private Map<String, Object> skipRow(Integer itemId, String partNo, String version, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemId", itemId);
        row.put("partNo", partNo);
        row.put("version", version);
        row.put("reason", reason);
        return row;
    }

    private String itemKey(String partNo, String version) {
        return DrawingPartNoResolver.normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private boolean hasDrawing(ItemQuoteVersion item) {
        return item != null && !isBlank(item.getDrawingUrl());
    }

    private String itemLabel(ItemQuoteVersion item) {
        if (item == null) {
            return "-";
        }
        return safe(item.getPartNo()) + " / " + safe(item.getVersion());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeBlank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().toUpperCase();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBusinessRejection(RuntimeException e) {
        return e != null
                && e.getClass().equals(RuntimeException.class)
                && e.getCause() == null
                && !isBlank(e.getMessage());
    }

    private String safeLogMessage(RuntimeException e) {
        String message = e == null ? null : e.getMessage();
        if (isBlank(message)) {
            return e == null ? "unknown" : e.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
