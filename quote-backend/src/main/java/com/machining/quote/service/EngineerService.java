package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.PricingScope;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.MaterialPriceSpecDTO;
import com.machining.quote.dto.PricingItemRemarkUpdateReq;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictMaterialPriceSpec;
import com.machining.quote.entity.DictMaterialType;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictMaterialPriceSpecMapper;
import com.machining.quote.mapper.DictMaterialTypeMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.MaterialDataResolver;
import com.machining.quote.util.MaterialPriceSpecSupport;
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
    private static final String ASSEMBLY_FEE_PROCESS_NAME = "组装费";
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 500;
    private static final Set<String> AUTO_REUSABLE_REPEAT_STATUSES = Set.of(
            QuoteStatusWorkflow.PRICED,
            QuoteStatusWorkflow.QUOTED,
            QuoteStatusWorkflow.PARTIALLY_RELEASED,
            QuoteStatusWorkflow.RELEASED,
            QuoteStatusWorkflow.ITEM_RELEASED
    );

    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private ItemAssemblyRelationMapper assemblyRelationMapper;
    @Autowired
    private ProcessRoutePricingService processRoutePricingService;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMaterialPriceSpecMapper materialPriceSpecMapper;
    @Autowired
    private DictMaterialTypeMapper materialTypeMapper;
    @Autowired
    private PricingFeeCalculator pricingFeeCalculator;
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
    @Autowired(required = false)
    private ConfirmedMaterialNoReuseService confirmedMaterialNoReuseService = new ConfirmedMaterialNoReuseService();
    @Autowired(required = false)
    private PricingOptionUsageStatService pricingOptionUsageStatService;

    @Transactional
    public void calculateAndSaveQuote(QuoteSubmitReq req) {
        ItemQuoteVersion item = requireQuoteItemForUpdate(req.getItemId());
        ItemQuoteVersion before = copyItem(item);
        String quoteMode = normalizeQuoteMode(req.getQuoteMode());
        if (QUOTE_MODE_ASSEMBLY.equals(quoteMode)) {
            saveAssemblyQuote(item, req.getAssemblyFee(), req.getAssemblyChildren());
        } else {
            saveCalculatedQuote(item, req.getMaterialData(), req.getProcessRoute(), req.getPartNetWeight());
            recordPricingOptionUsage(item);
        }
        markAncestorAssembliesPending(item.getId(), new HashSet<>());
        auditService.record("ITEM_QUOTE_SAVE", "item_quote_version", item.getId(), before, itemMapper.selectById(item.getId()));
    }

    @Transactional
    public ItemQuoteVersion updatePricingItemRemark(Integer itemId, PricingItemRemarkUpdateReq req) {
        String normalizedRemark = normalizePricingRemark(req == null ? null : req.getRemark());
        ItemQuoteVersion item = requireQuoteItemForUpdate(itemId);
        Map<String, Object> before = pricingRemarkAuditSummary(item);

        UpdateWrapper<ItemQuoteVersion> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", item.getId()).set("remark", normalizedRemark);
        itemMapper.update(null, updateWrapper);

        ItemQuoteVersion afterItem = itemMapper.selectById(item.getId());
        if (afterItem == null) {
            afterItem = copyItem(item);
            afterItem.setRemark(normalizedRemark);
        }
        auditService.record(
                "ITEM_QUOTE_REMARK_UPDATE",
                "item_quote_version",
                item.getId(),
                before,
                pricingRemarkAuditSummary(afterItem)
        );
        return afterItem;
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

    public PageResult<OrderRfq> listSupplementalPricingOrders(
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.isSupplementalPricingOrderStatus(normalizedStatus)) {
            return PageResult.of(List.of(), 0L, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> query = new QueryWrapper<>();
        applyOrderDataScope(query);
        if (normalizedStatus == null) {
            query.in("status", QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.QUOTED,
                    QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED);
        } else {
            query.eq("status", normalizedStatus);
        }
        if (!isBlank(customerName)) {
            query.like("customer_name", customerName.trim());
        }
        if (!isBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("rfq_no", normalizedKeyword)
                    .or()
                    .like("customer_name", normalizedKeyword)
                    .or()
                    .like("project_name", normalizedKeyword));
        }
        List<String> pendingStatuses = QuoteStatusWorkflow.supplementalPricingPendingItemStatuses();
        query.apply("EXISTS (SELECT 1 FROM item_quote_version item "
                + "WHERE item.rfq_id = order_rfq.id "
                + "AND NULLIF(BTRIM(item.drawing_url), '') IS NOT NULL "
                + "AND item.status IN ({0}, {1}, {2}, {3}))",
                pendingStatuses.get(0),
                pendingStatuses.get(1),
                pendingStatuses.get(2),
                pendingStatuses.get(3));

        Long total = orderMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0L, safePage, safePageSize);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(query), total, safePage, safePageSize);
    }

    private void saveCalculatedQuote(ItemQuoteVersion item, Map<String, Object> materialData,
                                     List<QuoteSubmitReq.ProcessSubmitDTO> processRoute, Double partNetWeight) {
        saveCalculatedQuote(item, materialData, processRoute, partNetWeight, true);
    }

    private void saveCalculatedQuote(ItemQuoteVersion item, Map<String, Object> materialData,
                                     List<QuoteSubmitReq.ProcessSubmitDTO> processRoute, Double partNetWeight,
                                     boolean markPriced) {
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
        String engineeringMaterialCode = stringValue(materialData.get("materialCode"));
        String engineeringMaterialName = stringValue(materialData.get("materialName"));
        String engineeringMaterialTypeCode = stringValue(materialData.get("materialTypeCode"));
        String engineeringMaterialTypeName = stringValue(materialData.get("materialTypeName"));
        String engineeringMaterialCategory = stringValue(materialData.get("materialCategory"));

        double totalProcessCost = 0.0;
        List<Map<String, Object>> pricedProcessRoute = new ArrayList<>();
        Set<String> extraFeeNames = loadEnabledExtraFeeNames();
        if (processRoute != null) {
            for (QuoteSubmitReq.ProcessSubmitDTO process : processRoute) {
                if (process == null) continue;
                if (isExtraFeeProcess(process, extraFeeNames)) {
                    double lineCost = nonNegativeOrZero(process.getInputVal());
                    if (lineCost <= 0) continue;
                    pricedProcessRoute.add(extraFeeRouteSnapshot(process, lineCost));
                    totalProcessCost += lineCost;
                    continue;
                }

                List<Map<String, Object>> pricedRows = processRoutePricingService.priceProcessRows(List.of(process));
                pricedProcessRoute.addAll(pricedRows);
                totalProcessCost += pricedRows.stream()
                        .mapToDouble(row -> zeroIfNull(doubleValue(row.get("lineCost"))))
                        .sum();
            }
        }

        double baseCost = materialCost + totalProcessCost;
        PricingFeeCalculator.PricingResult pricing = pricingFeeCalculator.calculate(baseCost, PricingScope.NORMAL);
        List<Map<String, Object>> feeBreakdown = pricing.getFeeRows();
        double taxIncludedUnitPrice = pricing.getTaxIncludedUnitPrice();
        double taxIncludedTotalPrice = taxIncludedUnitPrice * qty;
        double untaxedUnitPrice = pricing.getUntaxedUnitPrice();
        double untaxedTotalPrice = untaxedUnitPrice * qty;

        item.setMaterialData(materialData);
        item.setEngineeringMaterialCode(engineeringMaterialCode);
        item.setEngineeringMaterialName(engineeringMaterialName);
        item.setEngineeringMaterialTypeCode(engineeringMaterialTypeCode);
        item.setEngineeringMaterialTypeName(engineeringMaterialTypeName);
        item.setEngineeringMaterialCategory(engineeringMaterialCategory);
        item.setMaterialShape(materialShape(materialData));
        item.setProcessRoute(pricedProcessRoute);
        item.setFeeData(feeBreakdown);
        item.setMaterialGrossWeight(materialGrossWeight);
        item.setPartNetWeight(normalizedPartNetWeight);
        item.setQuoteMode(QUOTE_MODE_NORMAL);
        item.setAssemblyFee(0.0);
        item.setBaseCost(baseCost);
        item.setTaxIncludedUnitPrice(taxIncludedUnitPrice);
        item.setTaxIncludedTotalPrice(taxIncludedTotalPrice);
        item.setUntaxedUnitPrice(untaxedUnitPrice);
        item.setUntaxedTotalPrice(untaxedTotalPrice);
        if (markPriced) {
            item.setStatus(QuoteStatusWorkflow.PRICED);
        }

        deleteAssemblyRelations(item.getId());
        itemMapper.updateById(item);
        log.info("Item quote saved rfqId={} itemId={} partNo={} quoteMode={} baseCost={} taxIncludedUnitPrice={} toStatus={}",
                item.getRfqId(), item.getId(), item.getPartNo(), QUOTE_MODE_NORMAL, baseCost, taxIncludedUnitPrice,
                item.getStatus());
    }

    private void saveAssemblyQuote(ItemQuoteVersion parent, Double assemblyFee,
                                   List<QuoteSubmitReq.AssemblyChildDTO> assemblyChildren) {
        saveAssemblyQuote(parent, assemblyFee, assemblyChildren, true);
    }

    private void saveAssemblyQuote(ItemQuoteVersion parent, Double assemblyFee,
                                   List<QuoteSubmitReq.AssemblyChildDTO> assemblyChildren,
                                   boolean markPriced) {
        if (assemblyChildren == null || assemblyChildren.isEmpty()) {
            throw new RuntimeException("组合件必须至少选择一个子件");
        }

        List<QuoteSubmitReq.AssemblyChildDTO> normalizedChildren = normalizeAssemblyChildren(parent, assemblyChildren);
        validateAssemblyGraph(parent, normalizedChildren);

        double childrenTotal = 0.0;
        List<Map<String, Object>> childSummary = new ArrayList<>();
        for (QuoteSubmitReq.AssemblyChildDTO childLine : normalizedChildren) {
            ItemQuoteVersion child = itemMapper.selectById(childLine.getChildItemId());
            if (child.getTaxIncludedUnitPrice() == null) {
                throw new RuntimeException("子件 " + itemLabel(child) + " 尚未核价，不能作为组合件子件");
            }
            double qtyPerParent = nonNegativeOrZero(childLine.getQtyPerParent());
            double childAmount = child.getTaxIncludedUnitPrice() * qtyPerParent;
            childrenTotal += childAmount;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childItemId", child.getId());
            row.put("partNo", child.getPartNo());
            row.put("version", child.getVersion());
            row.put("description", child.getDescription());
            row.put("qtyPerParent", qtyPerParent);
            row.put("taxIncludedUnitPrice", child.getTaxIncludedUnitPrice());
            row.put("lineAmount", childAmount);
            childSummary.add(row);
        }

        double safeAssemblyFee = nonNegativeOrZero(assemblyFee);
        PricingFeeCalculator.PricingResult assemblyFeePricing = pricingFeeCalculator.calculate(safeAssemblyFee, PricingScope.ASSEMBLY);
        List<Map<String, Object>> assemblyFeeBreakdown = assemblyFeePricing.getFeeRows();
        double assemblyFeeMarkupCost = assemblyFeePricing.getTaxIncludedMarkupCost();
        double baseCost = childrenTotal + safeAssemblyFee;
        double taxIncludedUnitPrice = childrenTotal + assemblyFeePricing.getTaxIncludedUnitPrice();
        int parentQty = parent.getQty() == null ? 0 : parent.getQty();
        double taxIncludedTotalPrice = taxIncludedUnitPrice * parentQty;
        double untaxedUnitPrice = childrenTotal + assemblyFeePricing.getUntaxedUnitPrice();
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
            assemblyRoute.put("routeType", "FEE");
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
        parent.setEngineeringMaterialCode("");
        parent.setEngineeringMaterialName("");
        parent.setEngineeringMaterialTypeCode("");
        parent.setEngineeringMaterialTypeName("");
        parent.setEngineeringMaterialCategory("");
        parent.setMaterialShape("");
        parent.setProcessRoute(processRoute);
        parent.setFeeData(assemblyFeeBreakdown);
        parent.setMaterialGrossWeight(0.0);
        parent.setPartNetWeight(0.0);
        parent.setBaseCost(baseCost);
        parent.setTaxIncludedUnitPrice(taxIncludedUnitPrice);
        parent.setTaxIncludedTotalPrice(taxIncludedTotalPrice);
        parent.setUntaxedUnitPrice(untaxedUnitPrice);
        parent.setUntaxedTotalPrice(untaxedTotalPrice);
        if (markPriced) {
            parent.setStatus(QuoteStatusWorkflow.PRICED);
        }
        itemMapper.updateById(parent);
        log.info("Assembly quote saved rfqId={} itemId={} partNo={} childrenTotal={} assemblyFee={} assemblyFeeMarkupCost={} taxIncludedUnitPrice={} toStatus={}",
                parent.getRfqId(), parent.getId(), parent.getPartNo(), childrenTotal, safeAssemblyFee,
                assemblyFeeMarkupCost, taxIncludedUnitPrice, parent.getStatus());
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
                        row.put("taxIncludedUnitPrice", child.getTaxIncludedUnitPrice());
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
            if (isDiscardedItem(child)) {
                throw new RuntimeException("子件已废弃，不能作为组合件子件");
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
                && !isDiscardedItem(item)
                && item.getTaxIncludedUnitPrice() != null
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
                if (child.getTaxIncludedUnitPrice() == null) {
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
            if (parent == null || isDiscardedItem(parent) || parent.getTaxIncludedUnitPrice() == null) {
                continue;
            }
            UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
            update.eq("id", parent.getId())
                    .set("status", QuoteStatusWorkflow.UNPRICED)
                    .set("base_cost", null)
                    .set("tax_included_unit_price", null)
                    .set("tax_included_total_price", null)
                    .set("untaxed_unit_price", null)
                    .set("untaxed_total_price", null)
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
                .filter(item -> !QuoteStatusWorkflow.DISCARDED.equals(item.getStatus()))
                .filter(this::hasDrawing)
                .filter(item -> item.getTaxIncludedUnitPrice() == null)
                .map(this::itemLabel)
                .toList();
        if (!unquotedItems.isEmpty()) {
            throw new RuntimeException("仍有未报价的有图零件：" + String.join("、", unquotedItems));
        }

        int normalizedPricedItemCount = markPricedPendingItemsAsPriced(items);

        int noDrawingCount = 0;
        for (ItemQuoteVersion item : items) {
            if (QuoteStatusWorkflow.DISCARDED.equals(item.getStatus())) {
                continue;
            }
            if (!hasDrawing(item) && item.getTaxIncludedUnitPrice() == null) {
                item.setStatus(QuoteStatusWorkflow.NO_DRAWING);
                itemMapper.updateById(item);
                noDrawingCount++;
            }
        }

        order.setStatus(QuoteStatusWorkflow.nextAfterEngineerComplete(order.getStatus()));
        orderMapper.updateById(order);
        auditService.record("ORDER_QUOTE_COMPLETE", "order_rfq", order.getId(), null, order);
        log.info("Order quote completed rfqId={} itemCount={} noDrawingCount={} normalizedPricedItemCount={} toStatus={} durationMs={}",
                rfqId, items.size(), noDrawingCount, normalizedPricedItemCount, order.getStatus(), System.currentTimeMillis() - startedAt);
    }

    @Transactional
    public Map<String, Object> completeSupplementalDrawingQuote(Integer rfqId) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (!QuoteStatusWorkflow.isSupplementalPricingOrderStatus(order.getStatus())) {
            throw new RuntimeException("只有已核价、已报价、部分已提单或已生成提单订单可以完成补图补核价");
        }
        String orderStatusBefore = order.getStatus();
        List<ItemQuoteVersion> items = listItemsForUpdate(rfqId);
        List<String> pendingSupplementalItems = items.stream()
                .filter(this::isSupplementalPricingPendingItem)
                .map(this::itemLabel)
                .toList();
        if (!pendingSupplementalItems.isEmpty()) {
            throw new RuntimeException("尚未完成补核价的补图零件：" + String.join("、", pendingSupplementalItems));
        }

        int normalizedPricedItemCount = markPricedPendingItemsAsPriced(items);

        int pricedSupplementalItemCount = (int) items.stream()
                .filter(this::isSupplementalPricingCompletedItem)
                .count();
        if (pricedSupplementalItemCount == 0) {
            throw new RuntimeException("没有已保存核价的补图零件，不能完成补充核价");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplemental", true);
        result.put("pricedSupplementalItemCount", pricedSupplementalItemCount);
        result.put("normalizedPricedItemCount", normalizedPricedItemCount);
        result.put("orderStatusBefore", orderStatusBefore);
        result.put("orderStatusAfter", order.getStatus());
        auditService.record("ORDER_QUOTE_COMPLETE", "order_rfq", order.getId(), null, result);
        log.info("Supplemental drawing quote completed rfqId={} pricedSupplementalItemCount={} orderStatusBefore={} orderStatusAfter={} durationMs={}",
                rfqId, pricedSupplementalItemCount, orderStatusBefore, order.getStatus(), System.currentTimeMillis() - startedAt);
        return result;
    }

    private int markPricedPendingItemsAsPriced(List<ItemQuoteVersion> items) {
        int count = 0;
        for (ItemQuoteVersion item : items == null ? List.<ItemQuoteVersion>of() : items) {
            if (isDiscardedItem(item)
                    || !hasDrawing(item)
                    || item.getTaxIncludedUnitPrice() == null
                    || !QuoteStatusWorkflow.isPendingPricingItemStatus(item.getStatus())) {
                continue;
            }
            item.setStatus(QuoteStatusWorkflow.PRICED);
            itemMapper.updateById(item);
            count++;
        }
        return count;
    }

    private boolean hasDrawing(ItemQuoteVersion item) {
        return item != null && !isBlank(item.getDrawingUrl());
    }

    private boolean isSupplementalPricingPendingItem(ItemQuoteVersion item) {
        return hasDrawing(item)
                && item.getTaxIncludedUnitPrice() == null
                && QuoteStatusWorkflow.isPendingPricingItemStatus(item.getStatus());
    }

    private boolean isSupplementalPricingCompletedItem(ItemQuoteVersion item) {
        return hasDrawing(item)
                && item.getTaxIncludedUnitPrice() != null
                && QuoteStatusWorkflow.PRICED.equals(item.getStatus());
    }

    public Map<String, Object> analyzeRepeatOrder(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        List<ItemQuoteVersion> currentItems = listItems(rfqId);
        Map<String, RepeatSource> repeatIdentitySources = findRepeatIdentitySources(order, currentItems);
        Map<String, RepeatSource> repeatSources = findRepeatSources(order, currentItems);

        List<Map<String, Object>> repeatItems = new ArrayList<>();
        List<Map<String, Object>> newItems = new ArrayList<>();
        List<Map<String, Object>> recommendedProcessRoute = reusableEngineerProcessRoute(order.getEngineerConfig());
        Map<String, Integer> materialFrequency = new LinkedHashMap<>();
        Map<String, Map<String, Object>> materialSamples = new LinkedHashMap<>();

        for (ItemQuoteVersion item : currentItems) {
            String key = itemKey(item);
            RepeatSource identitySource = repeatIdentitySources.get(key);
            if (identitySource == null) {
                newItems.add(buildBasicItemInfo(item));
                continue;
            }

            RepeatSource source = repeatSources.get(key);
            boolean autoReusable = source != null;
            RepeatSource displaySource = autoReusable ? source : identitySource;
            Map<String, Object> repeatInfo = buildRepeatItemInfo(item, displaySource);
            repeatInfo.put("autoReusable", autoReusable);
            repeatItems.add(repeatInfo);

            if (!autoReusable) {
                continue;
            }

            ItemQuoteVersion sourceItem = source.item();

            if (recommendedProcessRoute.isEmpty()) {
                recommendedProcessRoute = reusableEngineerProcessRoute(source.order().getEngineerConfig());
            }

            String materialCode = MaterialDataResolver.materialCode(sourceItem);
            String priceSpecCode = MaterialDataResolver.materialPriceSpecCode(sourceItem);
            String shape = MaterialDataResolver.materialShape(sourceItem);
            if (!isBlank(materialCode) && !isBlank(priceSpecCode)) {
                String materialKey = materialKey(materialCode, priceSpecCode, shape);
                materialFrequency.put(materialKey, materialFrequency.getOrDefault(materialKey, 0) + 1);
                materialSamples.putIfAbsent(materialKey, repeatMaterialIdentity(sourceItem, materialCode, priceSpecCode, shape));
            }
        }

        Map<String, Object> recommendedConfig = buildRecommendedConfig(recommendedProcessRoute, materialFrequency, materialSamples);

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
            if (item.getTaxIncludedUnitPrice() != null) {
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
                skippedItems.add(buildSkipInfo(item, "未找到可自动复用的历史重复件"));
                continue;
            }
            if (!hasDrawing(item)) {
                skippedItems.add(buildSkipInfo(item, "当前零件无图，暂不自动核价"));
                continue;
            }
            if (item.getTaxIncludedUnitPrice() != null) {
                skippedItems.add(buildSkipInfo(item, "当前零件已有报价，未覆盖"));
                continue;
            }
        }

        if (!appliedItems.isEmpty()) {
            auditService.record("REPEAT_QUOTE_APPLY", "order_rfq", rfqId, null, appliedItems);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        int materialNoCopiedCount = countMaterialNoReuse(appliedItems, "COPIED");
        int materialNoSkippedCount = countMaterialNoReuse(appliedItems, "IDEMPOTENT")
                + countMaterialNoReuse(appliedItems, "NOT_APPLICABLE");
        int materialNoConflictCount = countMaterialNoReuse(appliedItems, "CONFLICT");
        result.put("success", true);
        result.put("message", "重复订单自动核价完成，已套用 " + appliedItems.size()
                + " 个零件；物料号补全 " + materialNoCopiedCount + " 个，物料号跳过 "
                + materialNoSkippedCount + " 个，物料号冲突 " + materialNoConflictCount
                + " 个，请工程师审核后手动完成核价提交。");
        result.put("appliedCount", appliedItems.size());
        result.put("skippedCount", skippedItems.size());
        result.put("materialNoCopiedCount", materialNoCopiedCount);
        result.put("materialNoSkippedCount", materialNoSkippedCount);
        result.put("materialNoConflictCount", materialNoConflictCount);
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
        if (currentItem.getTaxIncludedUnitPrice() != null) {
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
            return applyRepeatNormalQuote(source, currentItem, handledItemIds, appliedItems);
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
            if (currentChild.getTaxIncludedUnitPrice() == null) {
                return "子件 " + itemLabel(historicalChild) + " 尚未核价，需手动补齐";
            }

            QuoteSubmitReq.AssemblyChildDTO child = new QuoteSubmitReq.AssemblyChildDTO();
            child.setChildItemId(currentChild.getId());
            child.setQtyPerParent(historicalRelation.getChildQtyPerParent());
            child.setSortNo(historicalRelation.getSortNo());
            currentChildren.add(child);
        }

        MaterialNoReuseResult materialNoResult = reuseConfirmedMaterialNo(currentParent, sourceParent);
        saveAssemblyQuote(currentParent, sourceParent.getAssemblyFee(), currentChildren, false);
        addAppliedRepeatItem(currentParent, source, materialNoResult, handledItemIds, appliedItems);
        return null;
    }

    private String applyRepeatNormalQuote(RepeatSource source,
                                          ItemQuoteVersion currentItem,
                                          Set<Integer> handledItemIds,
                                          List<Map<String, Object>> appliedItems) {
        ItemQuoteVersion sourceItem = source.item();
        currentItem.setMaterialName(isBlank(currentItem.getMaterialName()) ? sourceItem.getMaterialName() : currentItem.getMaterialName());
        currentItem.setSurfaceTreatment(isBlank(currentItem.getSurfaceTreatment()) ? sourceItem.getSurfaceTreatment() : currentItem.getSurfaceTreatment());

        String materialCode = MaterialDataResolver.materialCode(sourceItem);
        if (isBlank(materialCode)) {
            return "历史重复件缺少材料编码，需手动重新选择材料";
        }
        String priceSpecCode = MaterialDataResolver.materialPriceSpecCode(sourceItem);
        if (isBlank(priceSpecCode)) {
            return "历史重复件缺少材料价格规格，需手动重新选择材料规格";
        }
        Map<String, Object> materialData = repeatQuoteMaterialData(sourceItem, materialCode, priceSpecCode);
        List<QuoteSubmitReq.ProcessSubmitDTO> processRoute = toProcessSubmitRows(sourceItem.getProcessRoute());
        String processRouteBlockedReason = validateRepeatProcessRoute(processRoute);
        if (processRouteBlockedReason != null) {
            return processRouteBlockedReason;
        }
        MaterialNoReuseResult materialNoResult = reuseConfirmedMaterialNo(currentItem, sourceItem);
        saveCalculatedQuote(currentItem, materialData, processRoute, sourceItem.getPartNetWeight(), false);
        addAppliedRepeatItem(currentItem, source, materialNoResult, handledItemIds, appliedItems);
        return null;
    }

    private Map<String, Object> repeatQuoteMaterialData(ItemQuoteVersion sourceItem, String materialCode, String priceSpecCode) {
        Map<String, Object> materialData = new LinkedHashMap<>(normalizeMap(sourceItem.getMaterialData()));
        materialData.put("materialCode", materialCode);
        materialData.put("priceSpecCode", priceSpecCode.trim().toUpperCase(Locale.ROOT));
        String priceSpecName = MaterialDataResolver.materialPriceSpecName(sourceItem);
        if (!isBlank(priceSpecName)) {
            materialData.put("priceSpecName", priceSpecName);
        }
        String materialShape = MaterialDataResolver.materialShape(sourceItem);
        if (!isBlank(materialShape)) {
            materialData.put("shape", materialShape);
        }
        return materialData;
    }

    private String validateRepeatProcessRoute(List<QuoteSubmitReq.ProcessSubmitDTO> processRoute) {
        if (processRoute == null || processRoute.isEmpty()) {
            return null;
        }
        Set<String> extraFeeNames = loadEnabledExtraFeeNames();
        for (QuoteSubmitReq.ProcessSubmitDTO process : processRoute) {
            if (process == null || isExtraFeeProcess(process, extraFeeNames)) {
                continue;
            }
            try {
                processRoutePricingService.priceProcessRows(List.of(process));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                String message = isBlank(ex.getMessage()) ? "历史工艺不可用" : ex.getMessage();
                return message + "，需手动补齐";
            }
        }
        return null;
    }

    private void addAppliedRepeatItem(ItemQuoteVersion item,
                                      RepeatSource source,
                                      MaterialNoReuseResult materialNoResult,
                                      Set<Integer> handledItemIds,
                                      List<Map<String, Object>> appliedItems) {
        handledItemIds.add(item.getId());
        Map<String, Object> applied = buildRepeatItemInfo(item, source);
        applied.put("taxIncludedUnitPrice", item.getTaxIncludedUnitPrice());
        applied.put("taxIncludedTotalPrice", item.getTaxIncludedTotalPrice());
        applied.put("quoteMode", normalizeQuoteMode(item.getQuoteMode()));
        applied.put("materialNoReuseStatus", materialNoResult == null ? null : materialNoResult.status().name());
        applied.put("materialNoReuseMessage", materialNoReuseMessage(materialNoResult));
        appliedItems.add(applied);
    }

    private MaterialNoReuseResult reuseConfirmedMaterialNo(ItemQuoteVersion target, ItemQuoteVersion source) {
        try {
            return confirmedMaterialNoReuseService.copyToRfqItem(target, source);
        } catch (ConfirmedMaterialNoReuseService.MaterialNoConflictException ex) {
            return new MaterialNoReuseResult(MaterialNoReuseResult.Status.CONFLICT);
        }
    }

    private int countMaterialNoReuse(List<Map<String, Object>> appliedItems, String status) {
        if (appliedItems == null || isBlank(status)) {
            return 0;
        }
        return (int) appliedItems.stream()
                .filter(item -> status.equals(item.get("materialNoReuseStatus")))
                .count();
    }

    private String materialNoReuseMessage(MaterialNoReuseResult result) {
        if (result == null || result.status() == null) {
            return null;
        }
        return switch (result.status()) {
            case COPIED -> "已补全 MES 已确认物料号";
            case IDEMPOTENT -> "物料号已一致，跳过覆盖";
            case NOT_APPLICABLE -> "来源未确认，跳过物料号复用";
            case CONFLICT -> "目标已有不同物料号，未覆盖";
        };
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
        validateOrderEngineerConfig(config);
        Object before = order.getEngineerConfig();
        order.setEngineerConfig(config == null ? Map.of() : config);
        orderMapper.updateById(order);
        auditService.record("ORDER_ENGINEER_CONFIG_SAVE", "order_rfq", rfqId, before, order.getEngineerConfig());
        log.info("Order engineer config saved rfqId={} configKeys={}",
                rfqId, config == null ? Set.of() : config.keySet());
    }

    private void validateOrderEngineerConfig(Map<String, Object> config) {
        if (config == null) {
            return;
        }
        for (Map<String, Object> route : normalizeProcessRoute(config.get("processRoute"))) {
            if (isAssemblyFeeProcessRoute(route)) {
                throw new RuntimeException("订单全局工艺模板不能包含组装费；组装件请在组合件核价区单独维护组装费");
            }
        }
    }

    private boolean isAssemblyFeeProcessRoute(Map<String, Object> route) {
        if (route == null) {
            return false;
        }
        String sourceType = stringValue(route.get("sourceType"));
        String calcType = stringValue(route.get("calcType"));
        String processName = stringValue(route.get("processName"));
        if (processName == null) {
            processName = stringValue(route.get("detailName"));
        }
        if (processName == null) {
            processName = stringValue(route.get("feeName"));
        }
        return "extra".equalsIgnoreCase(sourceType)
                && "非工时费用".equals(calcType)
                && ASSEMBLY_FEE_PROCESS_NAME.equals(processName == null ? "" : processName.trim());
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
        copy.setTaxIncludedUnitPrice(source.getTaxIncludedUnitPrice());
        copy.setTaxIncludedTotalPrice(source.getTaxIncludedTotalPrice());
        copy.setUntaxedUnitPrice(source.getUntaxedUnitPrice());
        copy.setUntaxedTotalPrice(source.getUntaxedTotalPrice());
        copy.setShippedQty(source.getShippedQty());
        copy.setUnshippedQty(source.getUnshippedQty());
        copy.setSalesOrderImportRecordId(source.getSalesOrderImportRecordId());
        copy.setStatus(source.getStatus());
        copy.setDrawingUrl(source.getDrawingUrl());
        return copy;
    }

    private String normalizePricingRemark(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.length() > 500) {
            throw new RuntimeException("remark must be no longer than 500 characters");
        }
        return normalized;
    }

    private Map<String, Object> pricingRemarkAuditSummary(ItemQuoteVersion item) {
        if (item == null) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("itemId", item.getId());
        summary.put("rfqId", item.getRfqId());
        summary.put("partNo", item.getPartNo());
        summary.put("version", item.getVersion());
        summary.put("remark", item.getRemark());
        return summary;
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
        if (isDiscardedItem(lockedItem)) {
            throw new RuntimeException("零件已废弃，不能继续核价或修改核价信息");
        }
        return lockedItem;
    }

    private void applyOrderDataScope(QueryWrapper<OrderRfq> qw) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(qw);
        }
    }

    private int safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, 100);
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
        qw.eq("rfq_id", rfqId);
        applyActiveItemFilter(qw);
        qw.orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<ItemQuoteVersion> listItemsForUpdate(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        applyActiveItemFilter(qw);
        qw.orderByAsc("id").last("FOR UPDATE");
        return itemMapper.selectList(qw);
    }

    private void applyActiveItemFilter(QueryWrapper<ItemQuoteVersion> query) {
        query.and(wrapper -> wrapper
                .ne("status", QuoteStatusWorkflow.DISCARDED)
                .or()
                .isNull("status"));
    }

    private boolean isDiscardedItem(ItemQuoteVersion item) {
        return item != null && QuoteStatusWorkflow.DISCARDED.equals(item.getStatus());
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

    private Map<String, RepeatSource> findRepeatIdentitySources(OrderRfq order, List<ItemQuoteVersion> currentItems) {
        Map<String, RepeatSource> result = new LinkedHashMap<>();
        List<PartVersionKey> currentKeys = buildRepeatSourceKeys(currentItems);
        if (order == null || order.getId() == null || currentKeys.isEmpty()) {
            return result;
        }

        List<ItemQuoteVersion> historicalItems = new ArrayList<>();
        for (int start = 0; start < currentKeys.size(); start += REPEAT_SOURCE_KEY_BATCH_SIZE) {
            int end = Math.min(start + REPEAT_SOURCE_KEY_BATCH_SIZE, currentKeys.size());
            historicalItems.addAll(itemMapper.selectLatestPartVersionRepeatSources(order.getId(), currentKeys.subList(start, end)));
        }
        if (historicalItems.isEmpty()) {
            return result;
        }

        Map<Integer, OrderRfq> historicalOrderMap = loadRepeatSourceOrders(historicalItems);
        for (ItemQuoteVersion historicalItem : historicalItems) {
            String itemKey = itemKey(historicalItem);
            if (result.containsKey(itemKey)) {
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
                && isAutoReusableRepeatStatus(item.getStatus())
                && item.getTaxIncludedUnitPrice() != null
                && !normalizeMap(item.getMaterialData()).isEmpty()
                && !normalizeProcessRoute(item.getProcessRoute()).isEmpty();
    }

    private boolean isAutoReusableRepeatStatus(String status) {
        return status != null && AUTO_REUSABLE_REPEAT_STATUSES.contains(status.trim());
    }

    private Map<String, Object> buildRecommendedConfig(
            List<Map<String, Object>> recommendedProcessRoute,
            Map<String, Integer> materialFrequency,
            Map<String, Map<String, Object>> materialSamples) {
        Map<String, Object> config = new LinkedHashMap<>();

        List<Map<String, Object>> processRoute = new ArrayList<>();
        for (Map<String, Object> route : recommendedProcessRoute == null ? List.<Map<String, Object>>of() : recommendedProcessRoute) {
            processRoute.add(recommendedRouteRow(route));
        }

        String bestMaterialKey = mostFrequentKey(materialFrequency);
        if (bestMaterialKey != null) {
            Map<String, Object> material = materialSamples.get(bestMaterialKey);
            if (material != null) {
                config.putAll(material);
            }
        }

        config.put("processRoute", processRoute);
        return config;
    }

    private List<Map<String, Object>> reusableEngineerProcessRoute(Object engineerConfig) {
        Map<String, Object> config = normalizeMap(engineerConfig);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> route : normalizeProcessRoute(config.get("processRoute"))) {
            if (isAssemblyFeeProcessRoute(route)) {
                continue;
            }
            rows.add(recommendedRouteRow(route));
        }
        return rows;
    }

    private Map<String, Object> recommendedRouteRow(Map<String, Object> route) {
        Map<String, Object> row = new LinkedHashMap<>();
        String sourceType = recommendedRouteSourceType(route);
        row.put("sortNo", route.get("sortNo"));
        row.put("processName", route.get("processName"));
        row.put("calcType", route.get("calcType"));
        row.put("sourceType", sourceType);
        if ("process".equals(sourceType)) {
            row.put("processDetailId", route.get("processDetailId"));
            row.put("processCategoryId", route.get("processCategoryId"));
            row.put("processCode", route.get("processCode"));
            row.put("categoryName", route.get("categoryName"));
            row.put("detailName", route.get("detailName"));
            row.put("hourlyRate", route.get("hourlyRate"));
            row.put("unitWorkHours", route.get("unitWorkHours"));
            row.put("inputVal", route.get("inputVal"));
        }
        return row;
    }

    private Map<String, Object> repeatMaterialIdentity(ItemQuoteVersion sourceItem, String materialCode, String priceSpecCode, String shape) {
        Map<String, Object> sourceMaterialData = normalizeMap(sourceItem == null ? null : sourceItem.getMaterialData());
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("materialCode", materialCode.trim());
        material.put("materialName", MaterialDataResolver.engineeringMaterialName(sourceItem));
        material.put("materialTypeCode", MaterialDataResolver.materialTypeCode(sourceItem));
        material.put("materialTypeName", MaterialDataResolver.materialTypeName(sourceItem));
        material.put("materialCategory", MaterialDataResolver.materialCategory(sourceItem));
        material.put("priceSpecCode", priceSpecCode.trim().toUpperCase(Locale.ROOT));
        material.put("priceSpecName", MaterialDataResolver.materialPriceSpecName(sourceItem));
        if (sourceMaterialData.containsKey("priceSpec")) {
            material.put("priceSpec", sourceMaterialData.get("priceSpec"));
        }
        material.put("shape", isBlank(shape) ? "板材" : shape);
        return material;
    }

    private String materialKey(String materialCode, String priceSpecCode, String shape) {
        return materialCode.trim().toUpperCase(Locale.ROOT)
                + "||"
                + priceSpecCode.trim().toUpperCase(Locale.ROOT)
                + "||"
                + (isBlank(shape) ? "板材" : shape);
    }

    private String recommendedRouteSourceType(Map<String, Object> route) {
        String sourceType = stringValue(route.get("sourceType"));
        if ("extra".equalsIgnoreCase(sourceType)
                || "\u975e\u5de5\u65f6\u8d39\u7528".equals(route.get("calcType"))) {
            return "extra";
        }
        return "process";
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
        row.put("sourceTaxIncludedUnitPrice", source.item().getTaxIncludedUnitPrice());
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

    private Map<String, Object> extraFeeRouteSnapshot(QuoteSubmitReq.ProcessSubmitDTO process, double lineCost) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("routeType", "FEE");
        row.put("sourceType", "extra");
        row.put("sortNo", process.getSortNo());
        row.put("feeItemId", process.getFeeItemId());
        row.put("feeCode", stringValue(process.getFeeCode()));
        row.put("feeName", process.getProcessName());
        row.put("processName", process.getProcessName());
        row.put("calcType", "非工时费用");
        row.put("inputVal", lineCost);
        row.put("unitRate", null);
        row.put("lineCost", lineCost);
        return row;
    }

    private List<QuoteSubmitReq.ProcessSubmitDTO> toProcessSubmitRows(Object routeObject) {
        List<QuoteSubmitReq.ProcessSubmitDTO> result = new ArrayList<>();
        for (Map<String, Object> row : normalizeProcessRoute(routeObject)) {
            QuoteSubmitReq.ProcessSubmitDTO process = new QuoteSubmitReq.ProcessSubmitDTO();
            process.setSortNo(intValue(row.get("sortNo")));
            process.setProcessDetailId(longValue(row.get("processDetailId")));
            process.setProcessCategoryId(longValue(row.get("processCategoryId")));
            process.setProcessCode(stringValue(row.get("processCode")));
            process.setCategoryName(stringValue(row.get("categoryName")));
            process.setDetailName(stringValue(row.get("detailName")));
            process.setHourlyRate(doubleValue(row.get("hourlyRate")));
            process.setUnitWorkHours(doubleValue(row.get("unitWorkHours")) == null
                    ? doubleValue(row.get("inputVal"))
                    : doubleValue(row.get("unitWorkHours")));
            process.setSourceType(stringValue(row.get("sourceType")));
            process.setFeeItemId(intValue(row.get("feeItemId")));
            process.setFeeCode(stringValue(row.get("feeCode")));
            process.setProcessName(stringValue(row.get("processName")));
            process.setCalcType(stringValue(row.get("calcType")));
            process.setInputVal(doubleValue(row.get("inputVal")));
            process.setUnitRate(doubleValue(row.get("unitRate")));
            process.setLineCost(doubleValue(row.get("lineCost")));
            result.add(process);
        }
        return result;
    }

    private void recordPricingOptionUsage(ItemQuoteVersion item) {
        if (pricingOptionUsageStatService == null || item == null) {
            return;
        }
        pricingOptionUsageStatService.recordSavedQuoteOptionUsage(item.getMaterialData(), item.getProcessRoute());
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
                && ("extra".equalsIgnoreCase(process.getSourceType())
                || "非工时费用".equals(process.getCalcType())
                || (!isBlank(process.getProcessName())
                && extraFeeNames.contains(process.getProcessName().trim())));
    }

    private double calculateMaterialCost(Map<String, Object> materialData) {
        if (materialData == null) {
            throw new RuntimeException("请重新选择材料：材料编码不能为空");
        }

        String materialCode = stringValue(materialData.get("materialCode"));
        if (isBlank(materialCode)) {
            throw new RuntimeException("请重新选择材料：材料编码不能为空");
        }
        String priceSpecCode = stringValue(materialData.get("priceSpecCode"));
        if (isBlank(priceSpecCode)) {
            throw new RuntimeException("请重新选择材料规格：priceSpecCode不能为空");
        }
        String shape = stringValue(materialData.get("shape"));
        Double submittedWeight = doubleValue(materialData.get("weight"));

        DictMaterial material = requireEnabledMaterialByCode(materialCode);
        DictMaterialType materialType = material.getMaterialTypeId() == null
                ? null
                : materialTypeMapper.selectById(material.getMaterialTypeId());
        MaterialPriceSpecDTO priceSpec = requireMaterialPriceSpec(material, priceSpecCode);
        MaterialPriceSpecSupport.PriceResolution priceResolution = resolveMaterialPrice(material, priceSpec, shape);
        double unitPrice = priceResolution.unitPrice();
        Double volume = calculateMetricVolume(materialData, shape);
        Double density;
        Double weight;
        if (volume != null) {
            density = resolveMaterialDensity(material, shape);
            weight = volume * density;
        } else {
            density = resolveMaterialDensityOrNull(material, shape);
            weight = submittedWeight;
        }
        double cost = weight != null && weight > 0 ? weight * unitPrice : 0.0;

        materialData.put("materialId", material.getId());
        materialData.put("materialCode", material.getMaterialCode());
        materialData.put("materialName", material.getMaterialName());
        materialData.put("name", material.getMaterialName());
        materialData.put("materialTypeId", material.getMaterialTypeId());
        materialData.put("materialTypeCode", materialType == null ? null : materialType.getTypeCode());
        materialData.put("materialTypeName", materialType == null ? null : materialType.getTypeName());
        materialData.put("materialCategory", material.getMaterialCategory());
        materialData.put("priceSpecCode", priceSpec.getSpecCode());
        materialData.put("priceSpecName", priceSpec.getSpecName());
        materialData.put("priceSpec", priceSpecSnapshot(priceSpec));
        materialData.put("density", density == null ? 0.0 : density);
        materialData.put("densityUnit", "kg/m3");
        materialData.put("unitPrice", unitPrice);
        materialData.put("unitPriceSource", priceResolution.source());
        materialData.put("unitPriceUnit", "元/kg");
        if (volume != null) {
            materialData.put("volume", volume);
            materialData.put("volumeUnit", "m3");
        } else {
            materialData.remove("volume");
            materialData.remove("volumeUnit");
        }
        if (weight != null && weight > 0) {
            materialData.put("weight", weight);
            materialData.put("weightUnit", "kg");
        }
        materialData.put("cost", cost);
        return cost;
    }

    private DictMaterial requireEnabledMaterialByCode(String materialCode) {
        QueryWrapper<DictMaterial> qw = new QueryWrapper<>();
        qw.apply("UPPER(material_code) = {0}", materialCode.toUpperCase(Locale.ROOT));
        qw.eq("enabled", true);
        List<DictMaterial> materials = materialMapper.selectList(qw);
        if (materials == null || materials.isEmpty()) {
            throw new RuntimeException("请重新选择材料：材料编码不存在或已禁用");
        }
        return materials.get(0);
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

    private MaterialPriceSpecDTO requireMaterialPriceSpec(DictMaterial material, String priceSpecCode) {
        String normalizedSpecCode = MaterialPriceSpecSupport.normalizeSpecCode(priceSpecCode);
        QueryWrapper<DictMaterialPriceSpec> query = new QueryWrapper<>();
        query.eq("material_id", material.getId()).eq("spec_code", normalizedSpecCode).last("LIMIT 1");
        DictMaterialPriceSpec spec = materialPriceSpecMapper.selectOne(query);
        if (spec == null) {
            throw new RuntimeException("材料价格规格未维护：materialCode="
                    + safeText(material.getMaterialCode())
                    + ", materialName="
                    + safeText(material.getMaterialName())
                    + ", priceSpec="
                    + normalizedSpecCode);
        }
        return MaterialPriceSpecSupport.toDto(spec);
    }

    private MaterialPriceSpecSupport.PriceResolution resolveMaterialPrice(
            DictMaterial material,
            MaterialPriceSpecDTO priceSpec,
            String shape) {
        MaterialPriceSpecSupport.PriceResolution resolution = MaterialPriceSpecSupport.effectivePrice(priceSpec, shape);
        if (resolution.unitPrice() == null || resolution.unitPrice() <= 0) {
            throw new RuntimeException("材料单价未维护：materialCode="
                    + safeText(material.getMaterialCode())
                    + ", materialName="
                    + safeText(material.getMaterialName())
                    + ", priceSpec="
                    + safeText(priceSpec == null ? null : priceSpec.getSpecName())
                    + ", shape="
                    + safeText(shape));
        }
        return resolution;
    }

    private Map<String, Object> priceSpecSnapshot(MaterialPriceSpecDTO spec) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("specCode", spec.getSpecCode());
        snapshot.put("specName", spec.getSpecName());
        snapshot.put("unifiedPrice", spec.getUnifiedPrice());
        snapshot.put("platePrice", spec.getPlatePrice());
        snapshot.put("barPrice", spec.getBarPrice());
        snapshot.put("tubePrice", spec.getTubePrice());
        snapshot.put("effectivePlatePrice", spec.getEffectivePlatePrice());
        snapshot.put("effectiveBarPrice", spec.getEffectiveBarPrice());
        snapshot.put("effectiveTubePrice", spec.getEffectiveTubePrice());
        return snapshot;
    }

    private boolean isBarShape(String shape) {
        return "棒材".equals(shape) || "妫掓潗".equals(shape) || "BAR".equalsIgnoreCase(safeText(shape));
    }

    private boolean isTubeShape(String shape) {
        return "管材".equals(shape) || "绠℃潗".equals(shape) || "TUBE".equalsIgnoreCase(safeText(shape));
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private double resolveMaterialDensity(DictMaterial material, String shape) {
        Double density = resolveMaterialDensityOrNull(material, shape);
        if (density != null && density > 0) {
            return density;
        }
        throw new RuntimeException("材料密度未维护(density missing)：materialCode="
                + safeText(material.getMaterialCode())
                + ", materialName="
                + safeText(material.getMaterialName())
                + ", shape="
                + safeText(shape));
    }

    private Double resolveMaterialDensityOrNull(DictMaterial material, String shape) {
        Double density = material.getDensity();
        if (density != null && density > 0) {
            return density;
        }
        return parseMesDensity(material.getMesDensityRaw());
    }

    private Double parseMesDensity(String value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            double density = Double.parseDouble(text);
            return density > 0 ? density : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private Long longValue(Object value) {
        Double number = doubleValue(value);
        return number == null ? null : number.longValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record RepeatSource(OrderRfq order, ItemQuoteVersion item) {
    }
}
