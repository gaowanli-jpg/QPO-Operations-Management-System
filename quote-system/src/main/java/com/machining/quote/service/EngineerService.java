package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.QuoteSubmitReq;
import com.machining.quote.entity.DictExtraFeeItem;
import com.machining.quote.entity.DictMarkupFee;
import com.machining.quote.entity.DictMaterial;
import com.machining.quote.entity.DictProcess;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.DictExtraFeeItemMapper;
import com.machining.quote.mapper.DictMarkupFeeMapper;
import com.machining.quote.mapper.DictMaterialMapper;
import com.machining.quote.mapper.DictProcessMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
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
public class EngineerService {

    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private DictProcessMapper processMapper;
    @Autowired
    private DictMaterialMapper materialMapper;
    @Autowired
    private DictMarkupFeeMapper markupFeeMapper;
    @Autowired
    private DictExtraFeeItemMapper extraFeeItemMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private AuditService auditService;

    @Transactional
    public void calculateAndSaveQuote(QuoteSubmitReq req) {
        ItemQuoteVersion item = itemMapper.selectById(req.getItemId());
        if (item == null) throw new RuntimeException("找不到该零件！");
        ItemQuoteVersion before = copyItem(item);
        saveCalculatedQuote(item, req.getMaterialData(), req.getProcessRoute());
        auditService.record("ITEM_QUOTE_SAVE", "item_quote_version", item.getId(), before, itemMapper.selectById(item.getId()));
    }

    private void saveCalculatedQuote(ItemQuoteVersion item, Map<String, Object> materialData, List<QuoteSubmitReq.ProcessSubmitDTO> processRoute) {
        int qty = item.getQty() == null ? 0 : item.getQty();

        double materialCost = calculateMaterialCost(materialData);

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
                    System.out.println("===> 警告：在数据库 dict_process 表中没有找到名为 [" + process.getProcessName() + "] 的工序！");
                    continue;
                }

                double rate = getTieredRate(dictInfo, qty);
                double lineCost = process.getInputVal() * rate;
                process.setCalcType("工时");
                process.setUnitRate(rate);
                process.setLineCost(lineCost);
                totalProcessCost += lineCost;
                System.out.println("===> 成功查到工序: [" + process.getProcessName() + "], 数量: " + qty + ", 匹配单价: " + rate + " 元");
            }
        }

        double baseCost = materialCost + totalProcessCost;
        List<Map<String, Object>> feeBreakdown = calculateMarkupFees(baseCost);
        double markupCost = feeBreakdown.stream()
                .mapToDouble(row -> doubleValue(row.get("cost")) == null ? 0.0 : doubleValue(row.get("cost")))
                .sum();
        double finalUnitPrice = baseCost + markupCost;
        double finalTotalPrice = finalUnitPrice * qty;

        item.setMaterialData(materialData);
        item.setProcessRoute(processRoute);
        item.setFeeData(feeBreakdown);
        item.setBaseCost(baseCost);
        item.setFinalUnitPrice(finalUnitPrice);
        item.setFinalTotalPrice(finalTotalPrice);
        item.setStatus("已报价");

        itemMapper.updateById(item);
        System.out.println("===> 零件 ID " + item.getId() + " 核价完毕！总成本(不含税): " + baseCost + ", 最终单价(含税): " + finalUnitPrice);
    }

    @Transactional
    public void completeOrderQuote(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId);
        List<ItemQuoteVersion> items = itemMapper.selectList(qw);
        if (items.isEmpty()) {
            throw new RuntimeException("该订单没有零件明细，不能完成报价");
        }

        List<String> unquotedPartNos = items.stream()
                .filter(this::hasDrawing)
                .filter(item -> item.getFinalUnitPrice() == null)
                .map(ItemQuoteVersion::getPartNo)
                .toList();
        if (!unquotedPartNos.isEmpty()) {
            throw new RuntimeException("仍有未报价的有图零件：" + String.join("、", unquotedPartNos));
        }

        for (ItemQuoteVersion item : items) {
            if (!hasDrawing(item) && item.getFinalUnitPrice() == null) {
                item.setStatus("无图");
                itemMapper.updateById(item);
            }
        }

        order.setStatus("已报价");
        orderMapper.updateById(order);
        auditService.record("ORDER_QUOTE_COMPLETE", "order_rfq", order.getId(), null, order);
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
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            RepeatSource source = repeatSources.get(normalizedPartNo);
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
        OrderRfq order = requireOrder(rfqId);
        List<ItemQuoteVersion> currentItems = listItems(rfqId);
        Map<String, RepeatSource> repeatSources = findRepeatSources(order, currentItems);

        List<Map<String, Object>> appliedItems = new ArrayList<>();
        List<Map<String, Object>> skippedItems = new ArrayList<>();

        for (ItemQuoteVersion item : currentItems) {
            RepeatSource source = repeatSources.get(normalizePartNo(item.getPartNo()));
            if (source == null) {
                skippedItems.add(buildSkipInfo(item, "未找到同客户已提单历史重复件"));
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

            ItemQuoteVersion sourceItem = source.item();
            item.setMaterialName(isBlank(item.getMaterialName()) ? sourceItem.getMaterialName() : item.getMaterialName());
            item.setSurfaceTreatment(isBlank(item.getSurfaceTreatment()) ? sourceItem.getSurfaceTreatment() : item.getSurfaceTreatment());

            Map<String, Object> materialData = normalizeMap(sourceItem.getMaterialData());
            List<QuoteSubmitReq.ProcessSubmitDTO> processRoute = toProcessSubmitRows(sourceItem.getProcessRoute());
            saveCalculatedQuote(item, materialData, processRoute);

            Map<String, Object> applied = buildRepeatItemInfo(item, source);
            applied.put("finalUnitPrice", item.getFinalUnitPrice());
            applied.put("finalTotalPrice", item.getFinalTotalPrice());
            appliedItems.add(applied);
        }

        if (!appliedItems.isEmpty()) {
            order.setStatus(resolveOrderStatus(rfqId));
            orderMapper.updateById(order);
            auditService.record("REPEAT_QUOTE_APPLY", "order_rfq", rfqId, null, appliedItems);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "重复订单自动核价完成，已套用 " + appliedItems.size() + " 个零件。");
        result.put("appliedCount", appliedItems.size());
        result.put("skippedCount", skippedItems.size());
        result.put("appliedItems", appliedItems);
        result.put("skippedItems", skippedItems);
        return result;
    }

    @Transactional
    public void saveOrderEngineerConfig(Integer rfqId, Map<String, Object> config) {
        OrderRfq order = requireOrder(rfqId);
        Object before = order.getEngineerConfig();
        order.setEngineerConfig(config == null ? Map.of() : config);
        orderMapper.updateById(order);
        auditService.record("ORDER_ENGINEER_CONFIG_SAVE", "order_rfq", rfqId, before, order.getEngineerConfig());
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
        copy.setMaterialName(source.getMaterialName());
        copy.setSurfaceTreatment(source.getSurfaceTreatment());
        copy.setUnit(source.getUnit());
        copy.setLeadTime(source.getLeadTime());
        copy.setRemark(source.getRemark());
        copy.setMaterialData(source.getMaterialData());
        copy.setProcessRoute(source.getProcessRoute());
        copy.setFeeData(source.getFeeData());
        copy.setBaseCost(source.getBaseCost());
        copy.setFinalUnitPrice(source.getFinalUnitPrice());
        copy.setFinalTotalPrice(source.getFinalTotalPrice());
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
        return order;
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private Map<String, RepeatSource> findRepeatSources(OrderRfq order, List<ItemQuoteVersion> currentItems) {
        Map<String, RepeatSource> result = new LinkedHashMap<>();
        Set<String> currentPartNos = new HashSet<>();
        for (ItemQuoteVersion item : currentItems) {
            String partNo = normalizePartNo(item.getPartNo());
            if (!partNo.isEmpty()) {
                currentPartNos.add(partNo);
            }
        }
        if (currentPartNos.isEmpty() || isBlank(order.getCustomerName())) {
            return result;
        }

        QueryWrapper<OrderRfq> orderQw = new QueryWrapper<>();
        orderQw.eq("customer_name", order.getCustomerName())
                .ne("id", order.getId())
                .orderByDesc("id");
        List<OrderRfq> historicalOrders = orderMapper.selectList(orderQw);

        for (OrderRfq historicalOrder : historicalOrders) {
            QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
            ticketQw.eq("rfq_id", historicalOrder.getId());
            List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQw);
            Set<String> ticketPartNos = new HashSet<>();
            for (OrderDeliveryTicket ticket : tickets) {
                String partNo = normalizePartNo(ticket.getPartNo());
                if (currentPartNos.contains(partNo)) {
                    ticketPartNos.add(partNo);
                }
            }
            if (ticketPartNos.isEmpty()) {
                continue;
            }

            QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
            itemQw.eq("rfq_id", historicalOrder.getId());
            List<ItemQuoteVersion> historicalItems = itemMapper.selectList(itemQw);
            for (ItemQuoteVersion historicalItem : historicalItems) {
                String partNo = normalizePartNo(historicalItem.getPartNo());
                if (!ticketPartNos.contains(partNo) || result.containsKey(partNo) || !hasReusableQuote(historicalItem)) {
                    continue;
                }
                result.put(partNo, new RepeatSource(historicalOrder, historicalItem));
            }
            if (result.size() == currentPartNos.size()) {
                break;
            }
        }

        return result;
    }

    private boolean hasReusableQuote(ItemQuoteVersion item) {
        return item != null
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
        row.put("sourceFinalUnitPrice", source.item().getFinalUnitPrice());
        row.put("sourceProcessRoute", source.item().getProcessRoute());
        return row;
    }

    private Map<String, Object> buildSkipInfo(ItemQuoteVersion item, String reason) {
        Map<String, Object> row = buildBasicItemInfo(item);
        row.put("reason", reason);
        return row;
    }

    private String resolveOrderStatus(Integer rfqId) {
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<ItemQuoteVersion> drawableItems = items.stream().filter(this::hasDrawing).toList();
        if (drawableItems.isEmpty()) {
            return "待工程部评估";
        }
        boolean allQuoted = drawableItems.stream().allMatch(item -> item.getFinalUnitPrice() != null);
        boolean anyQuoted = drawableItems.stream().anyMatch(item -> item.getFinalUnitPrice() != null);
        if (allQuoted) {
            return "已报价";
        }
        if (anyQuoted) {
            return "部分已报价";
        }
        return "待工程部评估";
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
            return result;
        }
        return List.of();
    }

    private List<QuoteSubmitReq.ProcessSubmitDTO> toProcessSubmitRows(Object routeObject) {
        List<QuoteSubmitReq.ProcessSubmitDTO> result = new ArrayList<>();
        for (Map<String, Object> row : normalizeProcessRoute(routeObject)) {
            QuoteSubmitReq.ProcessSubmitDTO process = new QuoteSubmitReq.ProcessSubmitDTO();
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
            double ratePercent = fee.getRatePercent() == null ? 0.0 : fee.getRatePercent();
            double cost = baseCost * ratePercent / 100.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feeName", fee.getFeeName());
            row.put("ratePercent", ratePercent);
            row.put("cost", cost);
            rows.add(row);
        }
        return rows;
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
        Double weight = doubleValue(materialData.get("weight"));

        if (materialName != null && weight != null && weight > 0) {
            QueryWrapper<DictMaterial> qw = new QueryWrapper<>();
            qw.eq("material_name", materialName);
            List<DictMaterial> materials = materialMapper.selectList(qw);
            if (!materials.isEmpty()) {
                DictMaterial material = materials.get(0);
                double unitPrice = resolveMaterialPrice(material, shape);
                double density = resolveMaterialDensity(material, shape);
                materialData.put("density", density);
                materialData.put("unitPrice", unitPrice);
                materialData.put("cost", weight * unitPrice);
                return weight * unitPrice;
            }
        }

        Double submittedCost = doubleValue(materialData.get("cost"));
        return submittedCost == null ? 0.0 : submittedCost;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record RepeatSource(OrderRfq order, ItemQuoteVersion item) {
    }
}
