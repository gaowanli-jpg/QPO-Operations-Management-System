package com.machining.quote.service;

import com.machining.quote.dto.PartVersionKey;
import com.machining.quote.dto.SalesOrderRepeatEnrichmentResult;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderRepeatEnrichmentService {
    private static final int REPEAT_SOURCE_KEY_BATCH_SIZE = 200;

    @Autowired
    private ItemQuoteVersionMapper itemMapper;

    public SalesOrderRepeatEnrichmentResult enrich(Integer rfqId, List<ItemQuoteVersion> currentItems) {
        SalesOrderRepeatEnrichmentResult result = new SalesOrderRepeatEnrichmentResult();
        if (rfqId == null || currentItems == null || currentItems.isEmpty()) {
            return result;
        }

        Map<String, ItemQuoteVersion> currentByKey = currentItemsByKey(currentItems);
        if (currentByKey.isEmpty()) {
            result.setUnmatchedItemCount(currentItems.size());
            result.setWarningMessage(warningMessage(result.getUnmatchedItemCount()));
            return result;
        }

        Map<String, ItemQuoteVersion> sourceByKey = repeatSourcesByKey(rfqId, new ArrayList<>(currentByKey.keySet()));
        int matchedCount = 0;
        int unmatchedCount = 0;
        for (Map.Entry<String, ItemQuoteVersion> entry : currentByKey.entrySet()) {
            ItemQuoteVersion current = entry.getValue();
            ItemQuoteVersion source = sourceByKey.get(entry.getKey());
            if (source == null) {
                unmatchedCount++;
                continue;
            }
            enrichItem(current, source);
            itemMapper.updateById(current);
            matchedCount++;
        }
        result.setMatchedItemCount(matchedCount);
        result.setUnmatchedItemCount(unmatchedCount);
        result.setWarningMessage(warningMessage(unmatchedCount));
        return result;
    }

    private Map<String, ItemQuoteVersion> currentItemsByKey(List<ItemQuoteVersion> items) {
        Map<String, ItemQuoteVersion> result = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            if (item == null || item.getId() == null || isBlank(item.getPartNo()) || isBlank(item.getVersion())) {
                continue;
            }
            result.putIfAbsent(itemKey(item.getPartNo(), item.getVersion()), item);
        }
        return result;
    }

    private Map<String, ItemQuoteVersion> repeatSourcesByKey(Integer rfqId, List<String> keys) {
        Map<String, ItemQuoteVersion> result = new LinkedHashMap<>();
        List<PartVersionKey> partVersionKeys = keys.stream()
                .map(this::toPartVersionKey)
                .toList();
        for (int start = 0; start < partVersionKeys.size(); start += REPEAT_SOURCE_KEY_BATCH_SIZE) {
            int end = Math.min(start + REPEAT_SOURCE_KEY_BATCH_SIZE, partVersionKeys.size());
            List<ItemQuoteVersion> sources = itemMapper.selectLatestReleasedRepeatSources(rfqId, partVersionKeys.subList(start, end));
            for (ItemQuoteVersion source : sources) {
                if (source != null && !isBlank(source.getPartNo()) && !isBlank(source.getVersion())) {
                    result.putIfAbsent(itemKey(source.getPartNo(), source.getVersion()), source);
                }
            }
        }
        return result;
    }

    private PartVersionKey toPartVersionKey(String key) {
        String[] parts = key.split("\\|\\|", 2);
        return new PartVersionKey(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private void enrichItem(ItemQuoteVersion current, ItemQuoteVersion source) {
        current.setMaterialName(source.getMaterialName());
        current.setEngineeringMaterialName(source.getEngineeringMaterialName());
        current.setSurfaceTreatment(source.getSurfaceTreatment());
        current.setMaterialGrossWeight(source.getMaterialGrossWeight());
        current.setPartNetWeight(source.getPartNetWeight());
        current.setQuoteMode(source.getQuoteMode());
        current.setAssemblyFee(source.getAssemblyFee());
        current.setMaterialData(source.getMaterialData());
        current.setProcessRoute(source.getProcessRoute());
        current.setFeeData(source.getFeeData());
        current.setDrawingUrl(source.getDrawingUrl());
        current.setBaseCost(source.getBaseCost());
    }

    private String warningMessage(int unmatchedCount) {
        if (unmatchedCount <= 0) {
            return "";
        }
        return "\u672a\u5339\u914d\u5386\u53f2\u751f\u4ea7\u5feb\u7167\u7684\u9500\u552e\u8ba2\u5355\u96f6\u4ef6\u6570\uff1a" + unmatchedCount;
    }

    private String itemKey(String partNo, String version) {
        return partNo.trim() + "||" + version.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
