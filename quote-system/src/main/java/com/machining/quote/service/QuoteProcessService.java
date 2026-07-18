package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.QuoteLeadTimeSetReq;
import com.machining.quote.dto.QuoteLeadTimeShiftReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuoteProcessService {
    private static final Logger log = LoggerFactory.getLogger(QuoteProcessService.class);

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private SystemTaskGuardService taskGuardService;

    public Map<String, Object> getQuoteProcess(Integer rfqId) {
        OrderRfq order = requireQuoteProcessReadableOrder(rfqId);
        List<ItemQuoteVersion> items = listItems(rfqId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", items.stream().map(this::buildItemRow).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> shiftLeadTimes(QuoteLeadTimeShiftReq req) {
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId());
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        result.put("itemList", listItems(req.getRfqId()).stream().map(this::buildItemRow).toList());
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
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId());
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

            Object before = buildItemRow(item);
            item.setQuoteLeadTime(normalizedDate);
            itemMapper.updateById(item);
            auditService.record("QUOTE_LEAD_TIME_SET", "item_quote_version", item.getId(), before, buildItemRow(itemMapper.selectById(item.getId())));
            adjustedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("adjustedCount", adjustedCount);
        result.put("skippedCount", skipped.size());
        result.put("skippedItems", skipped);
        result.put("itemList", listItems(req.getRfqId()).stream().map(this::buildItemRow).toList());
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
        requireQuoteProcessEditableOrderForUpdate(req.getRfqId());
        if (req.getItemId() == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }

        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(req.getItemId());
        if (item == null || !req.getRfqId().equals(item.getRfqId())) {
            throw new RuntimeException("零件不存在或不属于当前 RFQ");
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
                    .filter(item -> item.getFinalUnitPrice() == null)
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

            Map<String, Object> before = quoteSheetSummary(order, items, missingLeadTimes.size());
            order.setStatus(QuoteStatusWorkflow.nextAfterMarketSaveQuote(order.getStatus()));
            orderRfqMapper.updateById(order);
            OrderRfq savedOrder = orderRfqMapper.selectById(order.getId());
            Map<String, Object> after = quoteSheetSummary(savedOrder, items, 0);
            auditService.record(
                    "QUOTE_PROCESS_SAVE_QUOTE",
                    "order_rfq",
                    order.getId(),
                    before,
                    after,
                    "QUOTE_PROCESS",
                    "SUCCESS",
                    "保存报价单：RFQ " + safe(savedOrder.getRfqNo()) + "，零件 " + items.size() + " 个",
                    null
            );
            log.info("Quote sheet save completed rfqId={} fromStatus={} toStatus={} itemCount={} durationMs={}",
                    rfqId, before.get("status"), savedOrder.getStatus(), items.size(), System.currentTimeMillis() - startedAt);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("orderInfo", savedOrder);
            result.put("itemList", items.stream().map(this::buildItemRow).toList());
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

    public void assertQuoteExportable(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        if (!QuoteStatusWorkflow.canExportQuoteSheet(order.getStatus())) {
            if (QuoteStatusWorkflow.PRICED.equals(order.getStatus())) {
                throw new RuntimeException("请先在报价处理模块保存报价单后再导出");
            }
            throw new RuntimeException("只有已报价订单可以导出报价单");
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
            throw new RuntimeException("只有已核价或已报价订单可以处理报价单");
        }
        return order;
    }

    private OrderRfq requireQuoteProcessEditableOrderForUpdate(Integer rfqId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (!QuoteStatusWorkflow.canEditQuoteLeadTime(order.getStatus())) {
            if (QuoteStatusWorkflow.QUOTED.equals(order.getStatus())) {
                throw new RuntimeException("已报价订单交期已锁定，不能修改报价交期");
            }
            throw new RuntimeException("只有已核价订单可以维护报价交期");
        }
        return order;
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
    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private Map<String, Object> quoteSheetSummary(OrderRfq order, List<ItemQuoteVersion> items, int missingLeadTimeCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", order == null ? null : order.getStatus());
        summary.put("rfqNo", order == null ? null : order.getRfqNo());
        summary.put("itemCount", items == null ? 0 : items.size());
        summary.put("missingLeadTimeCount", missingLeadTimeCount);
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
        row.put("finalUnitPrice", item.getFinalUnitPrice());
        row.put("finalTotalPrice", item.getFinalTotalPrice());
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
