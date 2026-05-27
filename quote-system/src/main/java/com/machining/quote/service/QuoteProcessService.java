package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.QuoteLeadTimeShiftReq;
import com.machining.quote.dto.QuoteLeadTimeUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuoteProcessService {
    private static final String QUOTED_STATUS = "已报价";

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;

    public Map<String, Object> getQuoteProcess(Integer rfqId) {
        OrderRfq order = requireQuotedOrder(rfqId);
        List<ItemQuoteVersion> items = listItems(rfqId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", items.stream().map(this::buildItemRow).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> shiftLeadTimes(QuoteLeadTimeShiftReq req) {
        requireQuotedOrder(req.getRfqId());
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
            ItemQuoteVersion item = itemMapper.selectById(itemId);
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
        return result;
    }

    @Transactional
    public Map<String, Object> updateLeadTime(QuoteLeadTimeUpdateReq req) {
        requireQuotedOrder(req.getRfqId());
        if (req.getItemId() == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }

        ItemQuoteVersion item = itemMapper.selectById(req.getItemId());
        if (item == null || !req.getRfqId().equals(item.getRfqId())) {
            throw new RuntimeException("零件不存在或不属于当前 RFQ");
        }

        Object before = buildItemRow(item);
        item.setQuoteLeadTime(leadTimeService.normalizeForQuote(req.getQuoteLeadTime()));
        itemMapper.updateById(item);
        ItemQuoteVersion saved = itemMapper.selectById(item.getId());
        Map<String, Object> savedRow = buildItemRow(saved);
        auditService.record("QUOTE_LEAD_TIME_UPDATE", "item_quote_version", item.getId(), before, savedRow);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", savedRow);
        return result;
    }

    private OrderRfq requireQuotedOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ 订单不存在");
        }
        if (!QUOTED_STATUS.equals(order.getStatus())) {
            throw new RuntimeException("只有已报价订单可以处理报价单");
        }
        return order;
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
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
}
