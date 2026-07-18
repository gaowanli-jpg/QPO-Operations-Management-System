package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.InternalTargetLeadTimeSetReq;
import com.machining.quote.dto.InternalTargetLeadTimeShiftReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
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
public class InternalTargetLeadTimeService {
    private static final Logger log = LoggerFactory.getLogger(InternalTargetLeadTimeService.class);

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private AuditService auditService;

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
        result.put("itemList", listItems(req.getRfqId()).stream().map(this::buildItemRow).toList());
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
        result.put("itemList", listItems(req.getRfqId()).stream().map(this::buildItemRow).toList());
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

    public List<Map<String, Object>> listItemRows(Integer rfqId) {
        requireOrder(rfqId);
        return listItems(rfqId).stream().map(this::buildItemRow).toList();
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("RFQ ID 不能为空");
        }
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("RFQ 订单不存在");
        }
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
        return order;
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private Map<String, Object> buildItemRow(ItemQuoteVersion item) {
        Map<String, Object> row = new LinkedHashMap<>();
        String effectiveDeliveryLeadTime = leadTimeService.effectiveDeliveryLeadTime(item);
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
