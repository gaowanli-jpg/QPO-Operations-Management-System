package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictAssemblyMarkupFee;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMarkupFee;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictProcess;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictAssemblyMarkupFeeMapper;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictProcessMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EngineerService {
    private static final Logger log = LoggerFactory.getLogger(EngineerService.class);
    private static final String QUOTE_MODE_NORMAL = "NORMAL";
    private static final String QUOTE_MODE_ASSEMBLY = "ASSEMBLY";
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 500;

    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper assemblyRelationMapper;
    @Autowired
    private DictProcessMapper processMapper;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMarkupFeeMapper markupFeeMapper;
    @Autowired
    private DictAssemblyMarkupFeeMapper assemblyMarkupFeeMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private SystemTaskGuardService taskGuardService;

    @Transactional
    public void calculateAndSaveQuote(QuoteSubmitReq req) {
        ItemQuoteVersion item = requireQuoteItemForUpdate(req.getItemId());
        ItemQuoteVersion before = copyItem(item);
        String quoteMode = normalizeQuoteMode(req.getQuoteMode());
        if (QUOTE_MODE_ASSEMBLY.equals(quoteMode)) {
            saveAssemblyQuote(item, req.getAssemblyFee(), req.getAssemblyChildren());
        } else {
            saveCalculatedQuote(item, req.getMaterialData(), req.getProcessRoute(), req.getPartNetWeight());
        }
        markAncestorAssembliesPending(item.getId(), new HashSet<>());
        auditService.record("ITEM_QUOTE_SAVE", "item_quote_version", item.getId(), before, itemMapper.selectById(item.getId()));
    }

    @Transactional
    public Map<String, Object> refreshOrderQuotes(Integer rfqId) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (!QuoteStatusWorkflow.UNPRICED.equals(order.getStatus())
                && !QuoteStatusWorkflow.isLegacyUnpricedStatus(order.getStatus())) {
            throw new RuntimeException("只有未核价订单可以刷新核价，当前订单状态已推进，请刷新页面后再操作");
        }
        List<ItemQuoteVersion> items = listItemsForUpdate(rfqId);
        log.info("Order quote refresh started rfqId={} itemCount={}", rfqId, items.size());

        Map<Integer, ItemQuoteVersion> itemMap = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            itemMap.put(item.getId(), item);
        }

        int normalCount = 0;
        int assemblyCount = 0;
        List<Map<String, Object>> skippedItems = new ArrayList<>();

        for (ItemQuoteVersion item : items) {
            if (QUOTE_MODE_ASSEMBLY.equals(normalizeQuoteMode(item.getQuoteMode()))) {
                continue;
            }
            if (!isRefreshableNormalQuote(item)) {
                continue;
            }
            try {
                saveCalculatedQuote(
                        item,
                        normalizeMap(item.getMaterialData()),
                        toProcessSubmitRows(item.getProcessRoute()),
                        item.getPartNetWeight()
                );
                itemMap.put(item.getId(), item);
                normalCount++;
            } catch (RuntimeException ex) {
                skippedItems.add(buildSkipInfo(item, ex.getMessage()));
                log.warn("Order quote refresh skipped item rfqId={} itemId={} partNo={} reason={}",
                        rfqId, item.getId(), item.getPartNo(), ex.getMessage());
            }
        }

        Set<Integer> refreshedAssemblies = new HashSet<>();
        for (ItemQuoteVersion item : items) {
            if (!QUOTE_MODE_ASSEMBLY.equals(normalizeQuoteMode(item.getQuoteMode()))) {
                continue;
            }
            try {
                assemblyCount += refreshAssemblyQuoteRecursive(item, itemMap, refreshedAssemblies, new HashSet<>());
            } catch (RuntimeException ex) {
                skippedItems.add(buildSkipInfo(item, ex.getMessage()));
                log.warn("Order quote refresh skipped assembly rfqId={} itemId={} partNo={} reason={}",
                        rfqId, item.getId(), item.getPartNo(), ex.getMessage());
            }
        }

        int refreshedCount = normalCount + assemblyCount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "一键刷新核价完成，已刷新 " + refreshedCount + " 个零件。");
        result.put("refreshedCount", refreshedCount);
        result.put("normalCount", normalCount);
        result.put("assemblyCount", assemblyCount);
        result.put("skippedCount", skippedItems.size());
        result.put("skippedItems", skippedItems);
        auditService.record("ORDER_QUOTE_REFRESH", "order_rfq", order.getId(), null, result);
        log.info("Order quote refresh completed rfqId={} refreshedCount={} normalCount={} assemblyCount={} skippedCount={} durationMs={}",
                rfqId, refreshedCount, normalCount, assemblyCount, skippedItems.size(),
                System.currentTimeMillis() - startedAt);
        return result;
    }

    private void saveCalculatedQuote(ItemQuoteVersion item, Map<String, Object> materialData,
                                     List<QuoteSubmitReq.ProcessSubmitDTO> processRoute, Double partNetWeight) {
        int qty = item.getQty() == null ? 0 : item.getQty();
        if (materialData == null) {
            materialData = new LinkedHashMap<>();
        } else {
            materialData = new LinkedHashMap<>(materialData);
        }

        double materialCost = calculateMaterialCost(materialData);
        double materialGrossWeight = nonNegativeOrZero(doubleValue(materialData.get("weight")));
        double normalizedPartNetWeight = nonNegativeOrZero(partNetWeight);
        materialData.put("grossWeight", materialGrossWeight);
        materialData.put("grossWeightUnit", "kg");
        materialData.put("netWeight", normalizedPartNetWeight);
        materialData.put("netWeightUnit", "kg");
        String engineeringMaterialName = stringValue(materialData.get("name"));

        double totalProcessCost = 0.0;
        Set<String> extraFeeNames = loadEnabledExtraFeeNames();
        if (processRoute != null) {
            for (QuoteSubmitReq.ProcessSubmitDTO process : processRoute) {
                if (process.getInputVal() == null || process.getInputVal() <= 0) continue;

                if (isExtraFeeProcess(process, extraFeeNames)) {
                    double lineCost = process.getInputVal();
                    process.setCalcType("非工时费用");
                    process.setUnitRate(null);
                    process.setLineCost(lineCost);
                    totalProcessCost += lineCost;
                    continue;
                }

                DictProcess dictInfo = findProcess(process.getProcessName());
                if (dictInfo == null) {
                    log.warn("Process dictionary missing itemId={} processName={}",
                            item.getId(), process.getProcessName());
                    continue;
                }

                double rate = getTieredRate(dictInfo, qty);
                if (rate <= 0) {
                    log.warn("Process rate missing itemId={} processName={} qty={}",
                            item.getId(), process.getProcessName(), qty);
                }
                double lineCost = process.getInputVal() * rate;
                process.setCalcType("工时");
                process.setUnitRate(rate);
                process.setLineCost(lineCost);
                totalProcessCost += lineCost;
                log.debug("Process rate matched itemId={} processName={} qty={} rate={}",
                        item.getId(), process.getProcessName(), qty, rate);
            }
        }

        double baseCost = materialCost + totalProcessCost;
        List<Map<String, Object>> feeBreakdown = calculateMarkupFees(baseCost);
        double markupCost = feeBreakdown.stream()
                .mapToDouble(row -> doubleValue(row.get("cost")) == null ? 0.0 : doubleValue(row.get("cost")))
                .sum();
        double finalUnitPrice = baseCost + markupCost;
        double finalTotalPrice = finalUnitPrice * qty;
        double untaxedUnitPrice = untaxedUnitPrice(finalUnitPrice, feeBreakdown);
        double untaxedTotalPrice = untaxedUnitPrice * qty;

        item.setMaterialData(materialData);
        item.setEngineeringMaterialName(engineeringMaterialName);
        item.setMaterialShape(materialShape(materialData));
        item.setProcessRoute(processRoute);
        item.setFeeData(feeBreakdown);
        item.setMaterialGrossWeight(materialGrossWeight);
        item.setPartNetWeight(normalizedPartNetWeight);
        item.setQuoteMode(QUOTE_MODE_NORMAL);
        item.setAssemblyFee(0.0);
        item.setBaseCost(baseCost);
        item.setFinalUnitPrice(finalUnitPrice);
        item.setFinalTotalPrice(finalTotalPrice);
        item.setTaxIncludedUnitPrice(finalUnitPrice);
        item.setTaxIncludedTotalPrice(finalTotalPrice);
        item.setUntaxedUnitPrice(untaxedUnitPrice);
        item.setUntaxedTotalPrice(untaxedTotalPrice);
        item.setStatus(QuoteStatusWorkflow.PRICED);

        deleteAssemblyRelations(item.getId());
        itemMapper.updateById(item);
        log.info("Item quote saved rfqId={} itemId={} partNo={} quoteMode={} baseCost={} finalUnitPrice={} toStatus={}",
                item.getRfqId(), item.getId(), item.getPartNo(), QUOTE_MODE_NORMAL, baseCost, finalUnitPrice,
                QuoteStatusWorkflow.PRICED);
    }

    private void saveAssemblyQuote(ItemQuoteVersion parent, Double assemblyFee,
                                   List<QuoteSubmitReq.AssemblyChildDTO> assemblyChildren) {
        if (assemblyChildren == null || assemblyChildren.isEmpty()) {
            throw new RuntimeException("组合件必须至少选择一个子件");
        }

        List<QuoteSubmitReq.AssemblyChildDTO> normalizedChildren = normalizeAssemblyChildren(parent, assemblyChildren);
        validateAssemblyGraph(parent, normalizedChildren);

        double childrenTotal = 0.0;
        List<Map<String, Object>> childSummary = new ArrayList<>();
        for (QuoteSubmitReq.AssemblyChildDTO childLine : normalizedChildren) {
            ItemQuoteVersion child = itemMapper.selectById(childLine.getChildItemId());
            if (child.getFinalUnitPrice() == null) {
                throw new RuntimeException("子件 " + itemLabel(child) + " 尚未核价，不能作为组合件子件");
            }
            double qtyPerParent = nonNegativeOrZero(childLine.getQtyPerParent());
            double childAmount = child.getFinalUnitPrice() * qtyPerParent;
            childrenTotal += childAmount;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childItemId", child.getId());
            row.put("partNo", child.getPartNo());
            row.put("version", child.getVersion());
            row.put("description", child.getDescription());
            row.put("qtyPerParent", qtyPerParent);
            row.put("finalUnitPrice", child.getFinalUnitPrice());
            row.put("lineAmount", childAmount);
            childSummary.add(row);
        }

        double safeAssemblyFee = nonNegativeOrZero(assemblyFee);
        List<Map<String, Object>> assemblyFeeBreakdown = calculateAssemblyFeeMarkupFees(safeAssemblyFee);
        double assemblyFeeMarkupCost = sumFeeCosts(assemblyFeeBreakdown);
        double baseCost = childrenTotal + safeAssemblyFee;
        double finalUnitPrice = baseCost + assemblyFeeMarkupCost;
        int parentQty = parent.getQty() == null ? 0 : parent.getQty();
        double finalTotalPrice = finalUnitPrice * parentQty;
        double untaxedUnitPrice = untaxedUnitPrice(finalUnitPrice, assemblyFeeBreakdown);
        double untaxedTotalPrice = untaxedUnitPrice * parentQty;

        Map<String, Object> materialData = new LinkedHashMap<>();
        materialData.put("quoteMode", QUOTE_MODE_ASSEMBLY);
        materialData.put("childrenTotal", childrenTotal);
        materialData.put("assemblyFee", safeAssemblyFee);
        materialData.put("assemblyFeeMarkupCost", assemblyFeeMarkupCost);
        materialData.put("assemblyFeeMarkupRows", assemblyFeeBreakdown);
        materialData.put("baseCost", baseCost);
        materialData.put("cost", 0.0);
        materialData.put("weight", 0.0);
        materialData.put("grossWeight", 0.0);
        materialData.put("grossWeightUnit", "kg");
        materialData.put("netWeight", 0.0);
        materialData.put("netWeightUnit", "kg");
        materialData.put("children", childSummary);

        List<Map<String, Object>> processRoute = new ArrayList<>();
        if (safeAssemblyFee > 0) {
            Map<String, Object> assemblyRoute = new LinkedHashMap<>();
            assemblyRoute.put("sortNo", 10);
            assemblyRoute.put("processName", "组装费");
            assemblyRoute.put("calcType", "非工时费用");
            assemblyRoute.put("sourceType", "extra");
            assemblyRoute.put("inputVal", safeAssemblyFee);
            assemblyRoute.put("unitRate", null);
            assemblyRoute.put("lineCost", safeAssemblyFee);
            processRoute.add(assemblyRoute);
        }

        replaceAssemblyRelations(parent, normalizedChildren);

        parent.setQuoteMode(QUOTE_MODE_ASSEMBLY);
        parent.setAssemblyFee(safeAssemblyFee);
        parent.setMaterialData(materialData);
        parent.setEngineeringMaterialName("");
        parent.setMaterialShape("");
        parent.setProcessRoute(processRoute);
        parent.setFeeData(assemblyFeeBreakdown);
        parent.setMaterialGrossWeight(0.0);
        parent.setPartNetWeight(0.0);
        parent.setBaseCost(baseCost);
        parent.setFinalUnitPrice(finalUnitPrice);
        parent.setFinalTotalPrice(finalTotalPrice);
        parent.setTaxIncludedUnitPrice(finalUnitPrice);
        parent.setTaxIncludedTotalPrice(finalTotalPrice);
        parent.setUntaxedUnitPrice(untaxedUnitPrice);
        parent.setUntaxedTotalPrice(untaxedTotalPrice);
        parent.setStatus(QuoteStatusWorkflow.PRICED);
        itemMapper.updateById(parent);
        log.info("Assembly quote saved rfqId={} itemId={} partNo={} childrenTotal={} assemblyFee={} assemblyFeeMarkupCost={} finalUnitPrice={} toStatus={}",
                parent.getRfqId(), parent.getId(), parent.getPartNo(), childrenTotal, safeAssemblyFee,
                assemblyFeeMarkupCost, finalUnitPrice, QuoteStatusWorkflow.PRICED);
    }

    public List<Map<String, Object>> listAssemblyRelations(Integer parentItemId) {
        ItemQuoteVersion parent = itemMapper.selectById(parentItemId);
        if (parent == null) {
            throw new RuntimeException("父件不存在");
        }
        assertOrderDataScope(parent.getRfqId());
        return listRelationsByParent(parentItemId).stream()
                .map(relation -> {
                    ItemQuoteVersion child = itemMapper.selectById(relation.getChildItemId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", relation.getId());
                    row.put("rfqId", relation.getRfqId());
                    row.put("parentItemId", relation.getParentItemId());
                    row.put("childItemId", relation.getChildItemId());
                    row.put("qtyPerParent", relation.getChildQtyPerParent());
                    row.put("sortNo", relation.getSortNo());
                    if (child != null) {
                        row.put("partNo", child.getPartNo());
                        row.put("version", child.getVersion());
                        row.put("description", child.getDescription());
                        row.put("qty", child.getQty());
                        row.put("quoteMode", normalizeQuoteMode(child.getQuoteMode()));
                        row.put("finalUnitPrice", child.getFinalUnitPrice());
                        row.put("status", child.getStatus());
                    }
                    return row;
                })
                .toList();
    }

    private List<QuoteSubmitReq.AssemblyChildDTO> normalizeAssemblyChildren(ItemQuoteVersion parent,
                                                                            List<QuoteSubmitReq.AssemblyChildDTO> children) {
        Set<Integer> childIds = new HashSet<>();
        List<QuoteSubmitReq.AssemblyChildDTO> result = new ArrayList<>();
        int index = 0;
        for (QuoteSubmitReq.AssemblyChildDTO childLine : children) {
            if (childLine == null || childLine.getChildItemId() == null) {
                continue;
            }
            if (parent.getId().equals(childLine.getChildItemId())) {
                throw new RuntimeException("组合件不能选择自己作为子件");
            }
            if (!childIds.add(childLine.getChildItemId())) {
                throw new RuntimeException("同一个组合件不能重复选择同一个子件");
            }
            double qtyPerParent = nonNegativeOrZero(childLine.getQtyPerParent());
            if (qtyPerParent <= 0) {
                throw new RuntimeException("子件每套用量必须大于 0");
            }

            ItemQuoteVersion child = itemMapper.selectById(childLine.getChildItemId());
            if (child == null) {
                throw new RuntimeException("子件不存在，ID=" + childLine.getChildItemId());
            }
            if (!parent.getRfqId().equals(child.getRfqId())) {
                throw new RuntimeException("子件 " + itemLabel(child) + " 不属于当前 RFQ");
            }

            QuoteSubmitReq.AssemblyChildDTO normalized = new QuoteSubmitReq.AssemblyChildDTO();
            normalized.setChildItemId(childLine.getChildItemId());
            normalized.setQtyPerParent(qtyPerParent);
            normalized.setSortNo(childLine.getSortNo() == null ? (index + 1) * 10 : childLine.getSortNo());
            result.add(normalized);
            index++;
        }
        if (result.isEmpty()) {
            throw new RuntimeException("组合件必须至少选择一个有效子件");
        }
        return result;
    }

    private void validateAssemblyGraph(ItemQuoteVersion parent, List<QuoteSubmitReq.AssemblyChildDTO> children) {
        Map<Integer, List<Integer>> graph = loadAssemblyGraph(parent.getRfqId(), parent.getId());
        graph.put(parent.getId(), children.stream().map(QuoteSubmitReq.AssemblyChildDTO::getChildItemId).toList());
        for (QuoteSubmitReq.AssemblyChildDTO child : children) {
            if (hasPath(child.getChildItemId(), parent.getId(), graph, new HashSet<>())) {
                throw new RuntimeException("组合件关系不能形成循环引用");
            }
        }
    }

    private Map<Integer, List<Integer>> loadAssemblyGraph(Integer rfqId, Integer excludedParentItemId) {
        QueryWrapper<ItemAssemblyRelation> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        if (excludedParentItemId != null) {
            qw.ne("parent_item_id", excludedParentItemId);
        }
        List<ItemAssemblyRelation> relations = assemblyRelationMapper.selectList(qw);
        Map<Integer, List<Integer>> graph = new LinkedHashMap<>();
        for (ItemAssemblyRelation relation : relations) {
            graph.computeIfAbsent(relation.getParentItemId(), key -> new ArrayList<>())
                    .add(relation.getChildItemId());
        }
        return graph;
    }

    private boolean hasPath(Integer currentId, Integer targetId, Map<Integer, List<Integer>> graph, Set<Integer> visited) {
        if (currentId == null) {
            return false;
        }
        if (currentId.equals(targetId)) {
            return true;
        }
        if (!visited.add(currentId)) {
            return false;
        }
        for (Integer nextId : graph.getOrDefault(currentId, List.of())) {
            if (hasPath(nextId, targetId, graph, visited)) {
                return true;
            }
        }
        return false;
    }

    private void replaceAssemblyRelations(ItemQuoteVersion parent, List<QuoteSubmitReq.AssemblyChildDTO> children) {
        deleteAssemblyRelations(parent.getId());
        for (QuoteSubmitReq.AssemblyChildDTO childLine : children) {
            ItemAssemblyRelation relation = new ItemAssemblyRelation();
            relation.setRfqId(parent.getRfqId());
            relation.setParentItemId(parent.getId());
            relation.setChildItemId(childLine.getChildItemId());
            relation.setChildQtyPerParent(childLine.getQtyPerParent());
            relation.setSortNo(childLine.getSortNo());
            assemblyRelationMapper.insert(relation);
        }
    }

    private void deleteAssemblyRelations(Integer parentItemId) {
        QueryWrapper<ItemAssemblyRelation> qw = new QueryWrapper<>();
        qw.eq("parent_item_id", parentItemId);
        assemblyRelationMapper.delete(qw);
    }

    private List<ItemAssemblyRelation> listRelationsByParent(Integer parentItemId) {
        QueryWrapper<ItemAssemblyRelation> qw = new QueryWrapper<>();
        qw.eq("parent_item_id", parentItemId).orderByAsc("sort_no").orderByAsc("id");
        return assemblyRelationMapper.selectList(qw);
    }

    private boolean isRefreshableNormalQuote(ItemQuoteVersion item) {
        return item != null
                && item.getFinalUnitPrice() != null
                && (item.getMaterialData() != null || item.getProcessRoute() != null);
    }

    private int refreshAssemblyQuoteRecursive(ItemQuoteVersion parent,
                                              Map<Integer, ItemQuoteVersion> itemMap,
                                              Set<Integer> refreshedAssemblies,
                                              Set<Integer> visiting) {
        if (parent == null || parent.getId() == null) {
            return 0;
        }
        if (refreshedAssemblies.contains(parent.getId())) {
            return 0;
        }
        if (!visiting.add(parent.getId())) {
            throw new RuntimeException("组合件关系存在循环引用，无法刷新核价");
        }

        try {
            List<ItemAssemblyRelation> relations = listRelationsByParent(parent.getId());
            if (relations.isEmpty()) {
                throw new RuntimeException("组合件缺少子件关系，无法刷新核价");
            }

            int refreshedCount = 0;
            List<QuoteSubmitReq.AssemblyChildDTO> children = new ArrayList<>();
            for (ItemAssemblyRelation relation : relations) {
                ItemQuoteVersion child = itemMap.get(relation.getChildItemId());
                if (child == null) {
                    throw new RuntimeException("组合件子件已不存在，无法刷新核价");
                }
                if (QUOTE_MODE_ASSEMBLY.equals(normalizeQuoteMode(child.getQuoteMode()))) {
                    refreshedCount += refreshAssemblyQuoteRecursive(child, itemMap, refreshedAssemblies, visiting);
                }
                if (child.getFinalUnitPrice() == null) {
                    throw new RuntimeException("子件 " + itemLabel(child) + " 尚未核价，无法刷新组合件");
                }

                QuoteSubmitReq.AssemblyChildDTO childDto = new QuoteSubmitReq.AssemblyChildDTO();
                childDto.setChildItemId(relation.getChildItemId());
                childDto.setQtyPerParent(relation.getChildQtyPerParent());
                childDto.setSortNo(relation.getSortNo());
                children.add(childDto);
            }

            saveAssemblyQuote(parent, parent.getAssemblyFee(), children);
            itemMap.put(parent.getId(), parent);
            refreshedAssemblies.add(parent.getId());
            return refreshedCount + 1;
        } finally {
            visiting.remove(parent.getId());
        }
    }

    private void markAncestorAssembliesPending(Integer childItemId, Set<Integer> visited) {
        if (childItemId == null || !visited.add(childItemId)) {
            return;
        }
        QueryWrapper<ItemAssemblyRelation> qw = new QueryWrapper<>();
        qw.eq("child_item_id", childItemId);
        List<ItemAssemblyRelation> parentRelations = assemblyRelationMapper.selectList(qw);
        for (ItemAssemblyRelation relation : parentRelations) {
            ItemQuoteVersion parent = itemMapper.selectById(relation.getParentItemId());
            if (parent == null || parent.getFinalUnitPrice() == null) {
                continue;
            }
            UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
            update.eq("id", parent.getId())
                    .set("status", QuoteStatusWorkflow.UNPRICED)
                    .set("base_cost", null)
                    .set("final_unit_price", null)
                    .set("final_total_price", null)
                    .set("fee_data", null);
            itemMapper.update(null, update);
            markAncestorAssembliesPending(parent.getId(), visited);
        }
    }

    @Transactional
    public void completeOrderQuote(Integer rfqId) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        assertNoActiveRefreshQuotesTask(rfqId);

        List<ItemQuoteVersion> items = listItemsForUpdate(rfqId);
        if (items.isEmpty()) {
            throw new RuntimeException("该订单没有零件明细，不能完成报价");
        }

        List<String> unquotedItems = items.stream()
                .filter(this::hasDrawing)
                .filter(item -> item.getFinalUnitPrice() == null)
                .map(this::itemLabel)
                .toList();
        if (!unquotedItems.isEmpty()) {
            throw new RuntimeException("仍有未报价的有图零件：" + String.join("、", unquotedItems));
        }

        int noDrawingCount = 0;
        for (ItemQuoteVersion item : items) {
            if (!hasDrawing(item) && item.getFinalUnitPrice() == null) {
                item.setStatus(QuoteStatusWorkflow.NO_DRAWING);
                itemMapper.updateById(item);
                noDrawingCount++;
            }
        }

        order.setStatus(QuoteStatusWorkflow.nextAfterEngineerComplete(order.getStatus()));
        orderMapper.updateById(order);
        auditService.record("ORDER_QUOTE_COMPLETE", "order_rfq", order.getId(), null, order);
        log.info("Order quote completed rfqId={} itemCount={} noDrawingCount={} toStatus={} durationMs={}",
                rfqId, items.size(), noDrawingCount, order.getStatus(), System.currentTimeMillis() - startedAt);
    }

    private boolean hasDrawing(ItemQuoteVersion item) {
        return item != null && !isBlank(item.getDrawingUrl());
    }

    public Map<String, Object> analyzeRepeatOrder(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        List<ItemQuoteVersion> currentItems = listItems(rfqId);
        Map<String, RepeatSource> repeatSources = findRepeatSources(order, currentItems);

        List<Map<String, Object>> repeatItems = new ArrayList<>();
        List<Map<String, Object>> newItems = new ArrayList<>();
        Map<String, Integer> routeFrequency = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> routeSamples = new LinkedHashMap<>();
        Map<String, Integer> materialFrequency = new LinkedHashMap<>();
        Map<String, String> materialShapeMap = new LinkedHashMap<>();

        for (ItemQuoteVersion item : currentItems) {
            RepeatSource source = repeatSources.get(itemKey(item));
            if (source == null) {
                newItems.add(buildBasicItemInfo(item));
                continue;
            }

            ItemQuoteVersion sourceItem = source.item();
            repeatItems.add(buildRepeatItemInfo(item, source));

            String routeKey = routeSignature(sourceItem.getProcessRoute());
            if (!routeKey.isEmpty()) {
                routeFrequency.put(routeKey, routeFrequency.getOrDefault(routeKey, 0) + 1);
                routeSamples.putIfAbsent(routeKey, normalizeProcessRoute(sourceItem.getProcessRoute()));
            }

            Map<String, Object> materialData = normalizeMap(sourceItem.getMaterialData());
            String materialName = stringValue(materialData.get("name"));
            String shape = stringValue(materialData.get("shape"));
            if (!isBlank(materialName)) {
                String materialKey = materialName + "||" + (isBlank(shape) ? "板材" : shape);
                materialFrequency.put(materialKey, materialFrequency.getOrDefault(materialKey, 0) + 1);
                materialShapeMap.put(materialKey, materialName + "||" + (isBlank(shape) ? "板材" : shape));
            }
        }

        Map<String, Object> recommendedConfig = buildRecommendedConfig(routeFrequency, routeSamples, materialFrequency, materialShapeMap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("repeatCount", repeatItems.size());
        result.put("newCount", newItems.size());
        result.put("repeatItems", repeatItems);
        result.put("newItems", newItems);
        result.put("recommendedConfig", recommendedConfig);
        return result;
    }

    @Transactional
    public Map<String, Object> applyRepeatQuotes(Integer rfqId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        List<ItemQuoteVersion> currentItems = listItemsForUpdate(rfqId);
        Map<String, RepeatSource> repeatSources = findRepeatSources(order, currentItems);

        List<Map<String, Object>> appliedItems = new ArrayList<>();
        List<Map<String, Object>> skippedItems = new ArrayList<>();
        Map<String, ItemQuoteVersion> currentItemByKey = buildCurrentItemByKey(currentItems);
        Set<Integer> handledItemIds = new HashSet<>();
        Set<Integer> visitingItemIds = new HashSet<>();

        for (ItemQuoteVersion item : currentItems) {
            RepeatSource source = repeatSources.get(itemKey(item));
            if (source == null) {
                continue;
            }
            if (!hasDrawing(item)) {
                skippedItems.add(buildSkipInfo(item, "当前零件无图，暂不自动核价"));
                handledItemIds.add(item.getId());
                continue;
            }
            if (item.getFinalUnitPrice() != null) {
                skippedItems.add(buildSkipInfo(item, "当前零件已有报价，未覆盖"));
                handledItemIds.add(item.getId());
                continue;
            }

            String skippedReason = applyRepeatQuoteRecursive(
                    source,
                    item,
                    currentItemByKey,
                    handledItemIds,
                    visitingItemIds,
                    appliedItems);
            if (skippedReason != null) {
                skippedItems.add(buildSkipInfo(item, skippedReason));
                handledItemIds.add(item.getId());
            }
        }

        for (ItemQuoteVersion item : currentItems) {
            if (handledItemIds.contains(item.getId())) {
                continue;
            }
            RepeatSource source = repeatSources.get(itemKey(item));
            if (source == null) {
                skippedItems.add(buildSkipInfo(item, "未找到已提单历史重复件"));
                continue;
            }
            if (!hasDrawing(item)) {
                skippedItems.add(buildSkipInfo(item, "当前零件无图，暂不自动核价"));
                continue;
            }
            if (item.getFinalUnitPrice() != null) {
                skippedItems.add(buildSkipInfo(item, "当前零件已有报价，未覆盖"));
                continue;
            }
        }

        if (!appliedItems.isEmpty()) {
            auditService.record("REPEAT_QUOTE_APPLY", "order_rfq", rfqId, null, appliedItems);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "重复订单自动核价完成，已套用 " + appliedItems.size() + " 个零件，请工程师审核后手动完成核价提交。");
        result.put("appliedCount", appliedItems.size());
        result.put("skippedCount", skippedItems.size());
        result.put("appliedItems", appliedItems);
        result.put("skippedItems", skippedItems);
        return result;
    }

    private String applyRepeatQuoteRecursive(RepeatSource source,
                                             ItemQuoteVersion currentItem,
                                             Map<String, ItemQuoteVersion> currentItemByKey,
                                             Set<Integer> handledItemIds,
                                             Set<Integer> visitingItemIds,
                                             List<Map<String, Object>> appliedItems) {
        if (source == null || source.item() == null || currentItem == null || currentItem.getId() == null) {
            return "历史重复件不存在，需手动补齐";
        }
        if (handledItemIds.contains(currentItem.getId())) {
            return null;
        }
        if (!hasDrawing(currentItem)) {
            return "当前零件无图，暂不自动核价";
        }
        if (currentItem.getFinalUnitPrice() != null) {
            handledItemIds.add(currentItem.getId());
            return null;
        }
        if (!visitingItemIds.add(currentItem.getId())) {
            return "组合件关系存在循环引用，需手动补齐";
        }

        try {
            ItemQuoteVersion sourceItem = source.item();
            if (QUOTE_MODE_ASSEMBLY.equals(normalizeQuoteMode(sourceItem.getQuoteMode()))) {
                return applyRepeatAssemblyQuote(source, currentItem, currentItemByKey, handledItemIds, visitingItemIds, appliedItems);
            }
            if (!hasReusableQuote(sourceItem)) {
                return "历史重复件缺少可复用核价结果，需手动补齐";
            }
            applyRepeatNormalQuote(source, currentItem, handledItemIds, appliedItems);
            return null;
        } finally {
            visitingItemIds.remove(currentItem.getId());
        }
    }

    private String applyRepeatAssemblyQuote(RepeatSource source,
                                            ItemQuoteVersion currentParent,
                                            Map<String, ItemQuoteVersion> currentItemByKey,
                                            Set<Integer> handledItemIds,
                                            Set<Integer> visitingItemIds,
                                            List<Map<String, Object>> appliedItems) {
        ItemQuoteVersion sourceParent = source.item();
        List<ItemAssemblyRelation> historicalRelations = listRelationsByParent(sourceParent.getId());
        if (historicalRelations.isEmpty()) {
            return "历史组合件缺少子件关系，需手动补齐";
        }

        List<QuoteSubmitReq.AssemblyChildDTO> currentChildren = new ArrayList<>();
        for (ItemAssemblyRelation historicalRelation : historicalRelations) {
            ItemQuoteVersion historicalChild = itemMapper.selectById(historicalRelation.getChildItemId());
            if (historicalChild == null) {
                return "历史组合件子件不存在，需手动补齐";
            }

            ItemQuoteVersion currentChild = currentItemByKey.get(itemKey(historicalChild));
            if (currentChild == null) {
                return "缺少子件 " + itemLabel(historicalChild) + "，需手动补齐";
            }

            String skippedReason = applyRepeatQuoteRecursive(
                    new RepeatSource(source.order(), historicalChild),
                    currentChild,
                    currentItemByKey,
                    handledItemIds,
                    visitingItemIds,
                    appliedItems);
            if (skippedReason != null) {
                return "子件 " + itemLabel(historicalChild) + " " + skippedReason;
            }
            if (currentChild.getFinalUnitPrice() == null) {
                return "子件 " + itemLabel(historicalChild) + " 尚未核价，需手动补齐";
            }

            QuoteSubmitReq.AssemblyChildDTO child = new QuoteSubmitReq.AssemblyChildDTO();
            child.setChildItemId(currentChild.getId());
            child.setQtyPerParent(historicalRelation.getChildQtyPerParent());
            child.setSortNo(historicalRelation.getSortNo());
            currentChildren.add(child);
        }

        saveAssemblyQuote(currentParent, sourceParent.getAssemblyFee(), currentChildren);
        addAppliedRepeatItem(currentParent, source, handledItemIds, appliedItems);
        return null;
    }

    private void applyRepeatNormalQuote(RepeatSource source,
                                        ItemQuoteVersion currentItem,
                                        Set<Integer> handledItemIds,
                                        List<Map<String, Object>> appliedItems) {
        ItemQuoteVersion sourceItem = source.item();
        currentItem.setMaterialName(isBlank(currentItem.getMaterialName()) ? sourceItem.getMaterialName() : currentItem.getMaterialName());
        currentItem.setSurfaceTreatment(isBlank(currentItem.getSurfaceTreatment()) ? sourceItem.getSurfaceTreatment() : currentItem.getSurfaceTreatment());

        Map<String, Object> materialData = normalizeMap(sourceItem.getMaterialData());
        List<QuoteSubmitReq.ProcessSubmitDTO> processRoute = toProcessSubmitRows(sourceItem.getProcessRoute());
        saveCalculatedQuote(currentItem, materialData, processRoute, sourceItem.getPartNetWeight());
        addAppliedRepeatItem(currentItem, source, handledItemIds, appliedItems);
    }

    private void addAppliedRepeatItem(ItemQuoteVersion item,
                                      RepeatSource source,
                                      Set<Integer> handledItemIds,
                                      List<Map<String, Object>> appliedItems) {
        handledItemIds.add(item.getId());
        Map<String, Object> applied = buildRepeatItemInfo(item, source);
        applied.put("finalUnitPrice", item.getFinalUnitPrice());
        applied.put("finalTotalPrice", item.getFinalTotalPrice());
        applied.put("quoteMode", normalizeQuoteMode(item.getQuoteMode()));
        appliedItems.add(applied);
    }

    private Map<String, ItemQuoteVersion> buildCurrentItemByKey(List<ItemQuoteVersion> currentItems) {
        Map<String, ItemQuoteVersion> result = new LinkedHashMap<>();
        for (ItemQuoteVersion item : currentItems) {
            String key = itemKey(item);
            if (!key.startsWith("||")) {
                result.putIfAbsent(key, item);
            }
        }
        return result;
    }

    @Transactional
    public void saveOrderEngineerConfig(Integer rfqId, Map<String, Object> config) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        Object before = order.getEngineerConfig();
        order.setEngineerConfig(config == null ? Map.of() : config);
        orderMapper.updateById(order);
        auditService.record("ORDER_ENGINEER_CONFIG_SAVE", "order_rfq", rfqId, before, order.getEngineerConfig());
        log.info("Order engineer config saved rfqId={} configKeys={}",
                rfqId, config == null ? Set.of() : config.keySet());
    }

    private ItemQuoteVersion copyItem(ItemQuoteVersion source) {
        ItemQuoteVersion copy = new ItemQuoteVersion();
        copy.setId(source.getId());
        copy.setRfqId(source.getRfqId());
        copy.setPartNo(source.getPartNo());
        copy.setDescription(source.getDescription());
        copy.setQty(source.getQty());
        copy.setVersion(source.getVersion());
        copy.setOrderType(source.getOrderType());
        copy.setSalesOrderLineNo(source.getSalesOrderLineNo());
        copy.setCustomerOrderNo(source.getCustomerOrderNo());
        copy.setCustomerProjectNo(source.getCustomerProjectNo());
        copy.setInternalProjectNo(source.getInternalProjectNo());
        copy.setSalesMaterialNo(source.getSalesMaterialNo());
        copy.setCustomerDeliveryDate(source.getCustomerDeliveryDate());
        copy.setInternalDeliveryDate(source.getInternalDeliveryDate());
        copy.setMaterialName(source.getMaterialName());
        copy.setSurfaceTreatment(source.getSurfaceTreatment());
        copy.setUnit(source.getUnit());
        copy.setLeadTime(source.getLeadTime());
        copy.setQuoteLeadTime(source.getQuoteLeadTime());
        copy.setInternalTargetLeadTime(source.getInternalTargetLeadTime());
        copy.setRemark(source.getRemark());
        copy.setQuoteMode(source.getQuoteMode());
        copy.setAssemblyFee(source.getAssemblyFee());
        copy.setMaterialData(source.getMaterialData());
        copy.setProcessRoute(source.getProcessRoute());
        copy.setFeeData(source.getFeeData());
        copy.setMaterialGrossWeight(source.getMaterialGrossWeight());
        copy.setPartNetWeight(source.getPartNetWeight());
        copy.setBaseCost(source.getBaseCost());
        copy.setFinalUnitPrice(source.getFinalUnitPrice());
        copy.setFinalTotalPrice(source.getFinalTotalPrice());
        copy.setTaxIncludedUnitPrice(source.getTaxIncludedUnitPrice());
        copy.setTaxIncludedTotalPrice(source.getTaxIncludedTotalPrice());
        copy.setUntaxedUnitPrice(source.getUntaxedUnitPrice());
        copy.setUntaxedTotalPrice(source.getUntaxedTotalPrice());
        copy.setShippedQty(source.getShippedQty());
        copy.setUnshippedQty(source.getUnshippedQty());
        copy.setActualShipDate(source.getActualShipDate());
        copy.setSalesOrderImportRecordId(source.getSalesOrderImportRecordId());
        copy.setStatus(source.getStatus());
        copy.setDrawingUrl(source.getDrawingUrl());
        return copy;
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

    private ItemQuoteVersion requireQuoteItemForUpdate(Integer itemId) {
        if (itemId == null) {
            throw new RuntimeException("零件 ID 不能为空");
        }
        ItemQuoteVersion snapshot = itemMapper.selectById(itemId);
        if (snapshot == null) {
            throw new RuntimeException("找不到该零件！");
        }
        requireOrderForUpdate(snapshot.getRfqId());
        ItemQuoteVersion lockedItem = itemMapper.selectByIdForUpdate(itemId);
        if (lockedItem == null) {
            throw new RuntimeException("找不到该零件！");
        }
        return lockedItem;
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

    private void assertOrderDataScope(Integer rfqId) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderMapper);
        }
    }

    private void assertNoActiveRefreshQuotesTask(Integer rfqId) {
        if (taskGuardService != null) {
            taskGuardService.assertNoActiveRefreshQuotesTask(rfqId);
        }
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<ItemQuoteVersion> listItemsForUpdate(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id").last("FOR UPDATE");
        return itemMapper.selectList(qw);
    }

    private Map<String, RepeatSource> findRepeatSources(OrderRfq order, List<ItemQuoteVersion> currentItems) {
        Map<String, RepeatSource> result = new LinkedHashMap<>();
        List<PartVersionKey> currentKeys = buildRepeatSourceKeys(currentItems);
        if (order == null || order.getId() == null || currentKeys.isEmpty()) {
            return result;
        }

        List<ItemQuoteVersion> historicalItems = new ArrayList<>();
        for (int start = 0; start < currentKeys.size(); start += REPEAT_SOURCE_KEY_BATCH_SIZE) {
            int end = Math.min(start + REPEAT_SOURCE_KEY_BATCH_SIZE, currentKeys.size());
            historicalItems.addAll(itemMapper.selectLatestReleasedRepeatSources(order.getId(), currentKeys.subList(start, end)));
        }
        if (historicalItems.isEmpty()) {
            return result;
        }

        Map<Integer, OrderRfq> historicalOrderMap = loadRepeatSourceOrders(historicalItems);
        for (ItemQuoteVersion historicalItem : historicalItems) {
            String itemKey = itemKey(historicalItem);
            if (result.containsKey(itemKey) || !hasReusableQuote(historicalItem)) {
                continue;
            }
            OrderRfq historicalOrder = historicalOrderMap.get(historicalItem.getRfqId());
            if (historicalOrder == null) {
                continue;
            }
            result.put(itemKey, new RepeatSource(historicalOrder, historicalItem));
            if (result.size() == currentKeys.size()) {
                break;
            }
        }

        return result;
    }

    private List<PartVersionKey> buildRepeatSourceKeys(List<ItemQuoteVersion> currentItems) {
        Map<String, PartVersionKey> keysByIdentity = new LinkedHashMap<>();
        for (ItemQuoteVersion item : currentItems) {
            if (item == null || isBlank(item.getPartNo()) || isBlank(item.getVersion())) {
                continue;
            }
            String partNo = item.getPartNo().trim();
            String version = item.getVersion().trim();
            keysByIdentity.putIfAbsent(itemKey(partNo, version), new PartVersionKey(partNo, version));
        }
        return new ArrayList<>(keysByIdentity.values());
    }

    private Map<Integer, OrderRfq> loadRepeatSourceOrders(List<ItemQuoteVersion> historicalItems) {
        List<Integer> rfqIds = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (ItemQuoteVersion item : historicalItems) {
            if (item.getRfqId() != null && seen.add(item.getRfqId())) {
                rfqIds.add(item.getRfqId());
            }
        }
        if (rfqIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, OrderRfq> result = new LinkedHashMap<>();
        for (OrderRfq historicalOrder : orderMapper.selectBatchIds(rfqIds)) {
            if (historicalOrder.getId() != null) {
                result.put(historicalOrder.getId(), historicalOrder);
            }
        }
        return result;
    }

    private boolean hasReusableQuote(ItemQuoteVersion item) {
        return item != null
                && QuoteStatusWorkflow.ITEM_RELEASED.equals(item.getStatus())
                && item.getFinalUnitPrice() != null
                && item.getMaterialData() != null
                && item.getProcessRoute() != null;
    }

    private Map<String, Object> buildRecommendedConfig(
            Map<String, Integer> routeFrequency,
            Map<String, List<Map<String, Object>>> routeSamples,
            Map<String, Integer> materialFrequency,
            Map<String, String> materialShapeMap) {
        Map<String, Object> config = new LinkedHashMap<>();

        String bestRouteKey = mostFrequentKey(routeFrequency);
        List<Map<String, Object>> processRoute = new ArrayList<>();
        if (bestRouteKey != null) {
            for (Map<String, Object> route : routeSamples.getOrDefault(bestRouteKey, List.of())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sortNo", route.get("sortNo"));
                row.put("processName", route.get("processName"));
                row.put("calcType", route.get("calcType"));
                row.put("sourceType", "非工时费用".equals(route.get("calcType")) ? "extra" : "process");
                processRoute.add(row);
            }
        }

        String bestMaterialKey = mostFrequentKey(materialFrequency);
        String materialName = null;
        String shape = "板材";
        if (bestMaterialKey != null) {
            String[] parts = materialShapeMap.get(bestMaterialKey).split("\\|\\|", 2);
            materialName = parts.length > 0 ? parts[0] : null;
            shape = parts.length > 1 ? parts[1] : "板材";
        }

        config.put("materialName", materialName);
        config.put("shape", shape);
        config.put("processRoute", processRoute);
        return config;
    }

    private String mostFrequentKey(Map<String, Integer> frequency) {
        String bestKey = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : frequency.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestKey = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestKey;
    }

    private String routeSignature(Object processRoute) {
        List<Map<String, Object>> routes = normalizeProcessRoute(processRoute);
        List<String> names = new ArrayList<>();
        for (Map<String, Object> route : routes) {
            String name = stringValue(route.get("processName"));
            if (!isBlank(name)) {
                names.add(name);
            }
        }
        return String.join(">", names);
    }

    private Map<String, Object> buildBasicItemInfo(ItemQuoteVersion item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemId", item.getId());
        row.put("partNo", item.getPartNo());
        row.put("version", normalizeVersion(item.getVersion()));
        row.put("identityKey", itemKey(item));
        row.put("description", item.getDescription());
        row.put("qty", item.getQty());
        row.put("status", item.getStatus());
        row.put("hasDrawing", hasDrawing(item));
        return row;
    }

    private Map<String, Object> buildRepeatItemInfo(ItemQuoteVersion item, RepeatSource source) {
        Map<String, Object> row = buildBasicItemInfo(item);
        row.put("sourceRfqId", source.order().getId());
        row.put("sourceRfqNo", source.order().getRfqNo());
        row.put("sourceProjectName", source.order().getProjectName());
        row.put("sourceItemId", source.item().getId());
        row.put("sourceVersion", normalizeVersion(source.item().getVersion()));
        row.put("sourceFinalUnitPrice", source.item().getFinalUnitPrice());
        row.put("sourceProcessRoute", source.item().getProcessRoute());
        return row;
    }

    private Map<String, Object> buildSkipInfo(ItemQuoteVersion item, String reason) {
        Map<String, Object> row = buildBasicItemInfo(item);
        row.put("reason", reason);
        return row;
    }

    private List<Map<String, Object>> normalizeProcessRoute(Object routeObject) {
        if (routeObject instanceof List<?> rawList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    result.add(normalized);
                }
            }
            sortProcessRoute(result);
            return result;
        }
        return List.of();
    }

    private void sortProcessRoute(List<Map<String, Object>> routes) {
        boolean hasSortNo = routes.stream().anyMatch(route -> intValue(route.get("sortNo")) != null);
        if (!hasSortNo) {
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).putIfAbsent("__index", i);
        }
        routes.sort((left, right) -> {
            Integer leftSort = intValue(left.get("sortNo"));
            Integer rightSort = intValue(right.get("sortNo"));
            if (leftSort != null && rightSort != null && !leftSort.equals(rightSort)) {
                return leftSort.compareTo(rightSort);
            }
            if (leftSort != null && rightSort == null) {
                return -1;
            }
            if (leftSort == null && rightSort != null) {
                return 1;
            }
            return intValue(left.get("__index")).compareTo(intValue(right.get("__index")));
        });
        routes.forEach(route -> route.remove("__index"));
    }

    private List<QuoteSubmitReq.ProcessSubmitDTO> toProcessSubmitRows(Object routeObject) {
        List<QuoteSubmitReq.ProcessSubmitDTO> result = new ArrayList<>();
        for (Map<String, Object> row : normalizeProcessRoute(routeObject)) {
            QuoteSubmitReq.ProcessSubmitDTO process = new QuoteSubmitReq.ProcessSubmitDTO();
            process.setSortNo(intValue(row.get("sortNo")));
            process.setProcessName(stringValue(row.get("processName")));
            process.setCalcType(stringValue(row.get("calcType")));
            process.setInputVal(doubleValue(row.get("inputVal")));
            result.add(process);
        }
        return result;
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
            return normalized;
        }
        return Map.of();
    }

    private String normalizePartNo(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeVersion(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized;
    }

    private String normalizeQuoteMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return QUOTE_MODE_ASSEMBLY.equals(normalized) ? QUOTE_MODE_ASSEMBLY : QUOTE_MODE_NORMAL;
    }

    private String itemKey(ItemQuoteVersion item) {
        return itemKey(item.getPartNo(), item.getVersion());
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private String itemLabel(ItemQuoteVersion item) {
        return item.getPartNo() + " / " + normalizeVersion(item.getVersion());
    }

    private DictProcess findProcess(String processName) {
        if (isBlank(processName)) {
            return null;
        }
        QueryWrapper<DictProcess> qw = new QueryWrapper<>();
        qw.eq("process_name", processName);
        return processMapper.selectOne(qw);
    }

    private Set<String> loadEnabledExtraFeeNames() {
        QueryWrapper<DictExtraFeeItem> qw = new QueryWrapper<>();
        qw.eq("enabled", true);
        List<DictExtraFeeItem> items = extraFeeItemMapper.selectList(qw);
        Set<String> names = new HashSet<>();
        for (DictExtraFeeItem item : items) {
            if (!isBlank(item.getFeeName())) {
                names.add(item.getFeeName().trim());
            }
        }
        return names;
    }

    private boolean isExtraFeeProcess(QuoteSubmitReq.ProcessSubmitDTO process, Set<String> extraFeeNames) {
        return process != null
                && !isBlank(process.getProcessName())
                && extraFeeNames.contains(process.getProcessName().trim());
    }

    private List<Map<String, Object>> calculateMarkupFees(double baseCost) {
        QueryWrapper<DictMarkupFee> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        List<DictMarkupFee> fees = markupFeeMapper.selectList(qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DictMarkupFee fee : fees) {
            rows.add(markupFeeRow(baseCost, fee.getFeeName(), fee.getRatePercent()));
        }
        return rows;
    }

    private List<Map<String, Object>> calculateAssemblyFeeMarkupFees(double assemblyFee) {
        QueryWrapper<DictAssemblyMarkupFee> qw = new QueryWrapper<>();
        qw.eq("enabled", true).orderByAsc("sort_no").orderByAsc("id");
        List<DictAssemblyMarkupFee> fees = assemblyMarkupFeeMapper.selectList(qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DictAssemblyMarkupFee fee : fees) {
            rows.add(markupFeeRow(assemblyFee, fee.getFeeName(), fee.getRatePercent()));
        }
        return rows;
    }

    private Map<String, Object> markupFeeRow(double baseCost, String feeName, Double ratePercentValue) {
        double ratePercent = ratePercentValue == null ? 0.0 : ratePercentValue;
        double cost = baseCost * ratePercent / 100.0;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("feeName", feeName);
        row.put("ratePercent", ratePercent);
        row.put("cost", cost);
        return row;
    }

    private double sumFeeCosts(List<Map<String, Object>> feeRows) {
        if (feeRows == null) {
            return 0.0;
        }
        return feeRows.stream()
                .mapToDouble(row -> {
                    Double cost = doubleValue(row.get("cost"));
                    return cost == null ? 0.0 : cost;
                })
                .sum();
    }

    private double untaxedUnitPrice(double finalUnitPrice, List<Map<String, Object>> feeRows) {
        double taxCost = sumTaxFeeCosts(feeRows);
        if (taxCost <= 0) {
            return finalUnitPrice;
        }
        return Math.max(0.0, finalUnitPrice - taxCost);
    }

    private double sumTaxFeeCosts(List<Map<String, Object>> feeRows) {
        if (feeRows == null) {
            return 0.0;
        }
        return feeRows.stream()
                .filter(this::isTaxFeeRow)
                .mapToDouble(row -> {
                    Double cost = doubleValue(row.get("cost"));
                    return cost == null ? 0.0 : cost;
                })
                .sum();
    }

    private boolean isTaxFeeRow(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        String feeName = stringValue(row.get("feeName"));
        if (feeName == null) {
            return false;
        }
        String normalized = feeName.toLowerCase(Locale.ROOT);
        return normalized.contains("税")
                || normalized.contains("tax")
                || normalized.contains("vat");
    }

    private double getTieredRate(DictProcess dp, int qty) {
        if (qty <= 5) return dp.getPrice1To5() != null ? dp.getPrice1To5() : 0;
        if (qty <= 10) return dp.getPrice5To10() != null ? dp.getPrice5To10() : 0;
        if (qty <= 50) return dp.getPrice10To50() != null ? dp.getPrice10To50() : 0;
        if (qty <= 100) return dp.getPrice50To100() != null ? dp.getPrice50To100() : 0;
        if (qty <= 500) return dp.getPrice100To500() != null ? dp.getPrice100To500() : 0;
        if (qty <= 1000) return dp.getPrice500To1000() != null ? dp.getPrice500To1000() : 0;
        if (qty <= 3000) return dp.getPrice1000To3000() != null ? dp.getPrice1000To3000() : 0;
        return dp.getPrice3000Plus() != null ? dp.getPrice3000Plus() : 0;
    }

    private double calculateMaterialCost(Map<String, Object> materialData) {
        if (materialData == null) {
            return 0.0;
        }

        String materialName = stringValue(materialData.get("name"));
        String shape = stringValue(materialData.get("shape"));
        Double submittedWeight = doubleValue(materialData.get("weight"));

        if (materialName != null) {
            QueryWrapper<DictMaterial> qw = new QueryWrapper<>();
            qw.eq("material_name", materialName);
            List<DictMaterial> materials = materialMapper.selectList(qw);
            if (!materials.isEmpty()) {
                DictMaterial material = materials.get(0);
                double unitPrice = resolveMaterialPrice(material, shape);
                double density = resolveMaterialDensity(material, shape);
                Double volume = calculateMetricVolume(materialData, shape);
                Double weight = volume != null && density > 0
                        ? volume * density
                        : submittedWeight;

                materialData.put("density", density);
                materialData.put("densityUnit", "kg/m3");
                materialData.put("unitPrice", unitPrice);
                materialData.put("unitPriceUnit", "元/kg");
                if (volume != null) {
                    materialData.put("volume", volume);
                    materialData.put("volumeUnit", "m3");
                }
                if (weight != null && weight > 0) {
                    double cost = weight * unitPrice;
                    materialData.put("weight", weight);
                    materialData.put("weightUnit", "kg");
                    materialData.put("cost", cost);
                    return cost;
                }
            }
        }

        Double submittedCost = doubleValue(materialData.get("cost"));
        return submittedCost == null ? 0.0 : submittedCost;
    }

    private Double calculateMetricVolume(Map<String, Object> materialData, String shape) {
        if (!isMetricDimensionData(materialData)) {
            return null;
        }
        Object dimensionsObject = materialData.get("dimensions");
        if (!(dimensionsObject instanceof Map<?, ?> dimensions)) {
            return null;
        }

        double length = zeroIfNull(doubleValue(dimensions.get("L")));
        double width = zeroIfNull(doubleValue(dimensions.get("W")));
        double thickness = zeroIfNull(doubleValue(dimensions.get("T")));
        double outerDiameter = zeroIfNull(doubleValue(dimensions.get("outR")));
        double innerDiameter = zeroIfNull(doubleValue(dimensions.get("inR")));

        if ("棒材".equals(shape)) {
            if (length <= 0 || outerDiameter <= 0) {
                return null;
            }
            return Math.PI * Math.pow(outerDiameter / 2.0, 2) * length;
        }
        if ("管材".equals(shape)) {
            if (length <= 0 || outerDiameter <= 0 || innerDiameter < 0 || innerDiameter >= outerDiameter) {
                return null;
            }
            return Math.PI * (Math.pow(outerDiameter / 2.0, 2) - Math.pow(innerDiameter / 2.0, 2)) * length;
        }
        if (length <= 0 || width <= 0 || thickness <= 0) {
            return null;
        }
        return length * width * thickness;
    }

    private boolean isMetricDimensionData(Map<String, Object> materialData) {
        String unit = stringValue(materialData.get("dimensionUnit"));
        return "m".equalsIgnoreCase(unit) || "meter".equalsIgnoreCase(unit) || "meters".equalsIgnoreCase(unit) || "米".equals(unit);
    }

    private double zeroIfNull(Double value) {
        return value == null ? 0.0 : value;
    }

    private double resolveMaterialPrice(DictMaterial material, String shape) {
        if ("棒材".equals(shape)) {
            return material.getBarPrice() != null ? material.getBarPrice() : 0.0;
        }
        if ("管材".equals(shape)) {
            return material.getTubePrice() != null ? material.getTubePrice() : 0.0;
        }
        return material.getPlatePrice() != null ? material.getPlatePrice() : 0.0;
    }

    private double resolveMaterialDensity(DictMaterial material, String shape) {
        if ("棒材".equals(shape)) {
            return material.getBarDensity() != null ? material.getBarDensity() : fallbackDensity(material);
        }
        if ("管材".equals(shape)) {
            return material.getTubeDensity() != null ? material.getTubeDensity() : fallbackDensity(material);
        }
        return material.getPlateDensity() != null ? material.getPlateDensity() : fallbackDensity(material);
    }

    private double fallbackDensity(DictMaterial material) {
        return material.getDensity() != null ? material.getDensity() : 0.0;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String materialShape(Map<String, Object> materialData) {
        return stringValue(materialData == null ? null : materialData.get("shape"));
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double nonNegativeOrZero(Double value) {
        return value == null || value < 0 ? 0.0 : value;
    }

    private Integer intValue(Object value) {
        Double number = doubleValue(value);
        return number == null ? null : number.intValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record RepeatSource(OrderRfq order, ItemQuoteVersion item) {
    }
}
