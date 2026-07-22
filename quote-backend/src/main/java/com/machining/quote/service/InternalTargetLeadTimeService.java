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
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.MaterialDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InternalTargetLeadTimeService {
    private static final Logger log = LoggerFactory.getLogger(InternalTargetLeadTimeService.class);

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

    private int ticketedQty(ItemQuoteVersion item, List<OrderDeliveryTicket> tickets) {
        return tickets.stream()
                .filter(ticket -> matchesTicketItem(item, ticket))
                .mapToInt(ticket -> safeInt(ticket.getQty()))
                .sum();
    }

    private boolean matchesTicketItem(ItemQuoteVersion item, OrderDeliveryTicket ticket) {
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

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
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
